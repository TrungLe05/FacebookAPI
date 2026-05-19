package com.dev.coreservice.pipeline;

import com.dev.coreservice.config.AppProperties;
import com.dev.coreservice.model.ClassificationResult;
import com.dev.coreservice.model.NormalizedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Bước 2: Phân loại intent & sentiment bằng Gemini 1.5 Flash.
 * Nếu API không available → fallback về default classification.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClassifier {

    private final WebClient webClient;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    private static final String PROMPT_TEMPLATE = """
            Classify the following Facebook comment strictly in JSON format. \
            Do NOT include markdown, only raw JSON.
            
            JSON schema:
            {
              "intent": "one of [ask_price, complaint, compliment, spam, other]",
              "sentiment": "one of [positive, neutral, negative]",
              "requires_reply": true or false,
              "confidence": number between 0.0 and 1.0
            }
            
            Rules:
            - ask_price: user asks about price, availability, shipping, ordering.
              Vietnamese signals: giá, giá bao nhiêu, bao nhiêu tiền, giá sao, mua ở đâu, đặt hàng, ship, giao hàng, inbox, ib, pm, còn hàng, order, tư vấn
            - complaint: user reports issue, unhappy, demands refund, criticizes quality.
              Vietnamese signals: tệ, kém, lỗi, hỏng, không tốt, hoàn tiền, chờ lâu, thất vọng
            - compliment: user praises, positive feedback, recommends.
              Vietnamese signals: hay, tốt, đẹp, ngon, chất lượng, uy tín, thích, recommend
            - spam: promotional, off-topic link, gibberish, unrelated content
            - other: anything that does NOT fit the above categories
            
            Important: Treat any question or inquiry about price/cost/availability as ask_price, \
            even short phrases like "giá sao", "bao nhiêu", "giá?", "có hàng không".
            
            Comment: "%s"
            """;

    public ClassificationResult classify(NormalizedEvent event) {
        log.info("[AiClassifier] Classifying event={} content='{}'",
                event.getEventId(), event.getContent());

        ClassificationResult result;
        try {
            result = callGroqApi(event);
        } catch (Exception e) {
            log.warn("[AiClassifier] Groq failed: {}. Trying Gemini...", e.getMessage());
            try {
                result = callGeminiApi(event);
            } catch (Exception ex) {
                log.warn("[AiClassifier] Gemini also failed: {}. Using keyword fallback.", ex.getMessage());
                return buildKeywordFallback(event.getContent());
            }
        }

        // Post-check: AI trả về "other" → thử keyword để tránh miss classify tiếng Việt
        if ("other".equals(result.getIntent())) {
            ClassificationResult keywordResult = buildKeywordFallback(event.getContent());
            if (!"other".equals(keywordResult.getIntent())) {
                log.info("[AiClassifier] AI returned 'other' but keyword matched '{}' → overriding",
                        keywordResult.getIntent());
                return keywordResult;
            }
        }

        return result;
    }

    private ClassificationResult callGroqApi(NormalizedEvent event) {
        String apiKey = props.getAi().getGroq().getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("DISABLED")) {
            throw new RuntimeException("Groq API key not configured");
        }

        String prompt = PROMPT_TEMPLATE.formatted(sanitize(event.getContent()));
        Map<String, Object> requestBody = Map.of(
                "model", props.getAi().getGroq().getModel(),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", prompt
                )),
                "temperature", 0.0,
                "response_format", Map.of("type", "json_object") // Yêu cầu trả về JSON chuẩn
        );

        String responseJson = webClient.post()
                .uri(props.getAi().getGroq().getEndpoint())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response ->
                        response.bodyToMono(String.class).flatMap(body -> {
                            log.error("[AiClassifier] Groq API 4xx body: {}", body);
                            return Mono.error(new RuntimeException("Groq error: " + body));
                        })
                )
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        if (responseJson == null) throw new RuntimeException("Empty response from Groq");
        return parseGroqResponse(responseJson);
    }

    private ClassificationResult parseGroqResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            // Lấy text từ choices[0].message.content (chuẩn OpenAI API)
            String text = root
                    .path("choices").get(0)
                    .path("message")
                    .path("content").asText();

            log.info("[AiClassifier] Groq Raw Response:\n{}", text.trim());

            // Parse JSON result từ Groq
            JsonNode result = objectMapper.readTree(text.trim());
            return ClassificationResult.builder()
                    .intent(result.path("intent").asText("other"))
                    .sentiment(result.path("sentiment").asText("neutral"))
                    .requiresReply(result.path("requires_reply").asBoolean(false))
                    .confidence(result.path("confidence").asDouble(0.5))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.error("[AiClassifier] Failed to parse Groq response: {}", e.getMessage());
            return buildFallback();
        }
    }

    private ClassificationResult callGeminiApi(NormalizedEvent event) {
        String apiKey = props.getAi().getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("DISABLED")) {
            throw new RuntimeException("Gemini API key not configured");
        }

        String prompt = PROMPT_TEMPLATE.formatted(sanitize(event.getContent()));
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                ))
        );

        String uri = props.getAi().getGemini().getEndpoint() + "/" 
                     + props.getAi().getGemini().getModel() 
                     + ":generateContent?key=" + apiKey;

        String responseJson = webClient.post()
                .uri(uri)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response ->
                        response.bodyToMono(String.class).flatMap(body -> {
                            log.error("[AiClassifier] Gemini API 4xx body: {}", body);
                            return Mono.error(new RuntimeException("Gemini error: " + body));
                        })
                )
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        if (responseJson == null) throw new RuntimeException("Empty response from Gemini");
        return parseGeminiResponse(responseJson);
    }

    private ClassificationResult parseGeminiResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            // Lấy text từ candidates[0].content.parts[0].text
            String text = root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();

            log.info("[AiClassifier] Gemini Raw Response:\n{}", text.trim());

            // Parse JSON result từ Gemini
            JsonNode result = objectMapper.readTree(text.trim());
            return ClassificationResult.builder()
                    .intent(result.path("intent").asText("other"))
                    .sentiment(result.path("sentiment").asText("neutral"))
                    .requiresReply(result.path("requires_reply").asBoolean(false))
                    .confidence(result.path("confidence").asDouble(0.5))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.error("[AiClassifier] Failed to parse Gemini response: {}", e.getMessage());
            return buildFallback();
        }
    }

    /**
     * Fallback dựa trên keyword khi cả Groq lẫn Gemini đều fail.
     * Ưu tiên detect các intent phổ biến để không bỏ lỡ AUTO_REPLY.
     */
    private ClassificationResult buildKeywordFallback(String content) {
        if (content == null || content.isBlank()) {
            return buildFallback();
        }
        String lower = content.toLowerCase().trim();

        // ── Hỏi giá / tư vấn ─────────────────────────────────────────────────
        if (containsAny(lower, List.of(
                "giá", "bao nhiêu", "giá sao", "giá bao",
                "price", "cost", "phí", "mua", "order", "đặt hàng", "đặt",
                "inbox", "ib", "pm", "dm", "nhắn tin", "ship", "giao hàng",
                "còn hàng", "hàng còn", "có hàng", "tư vấn", "mua ở đâu"
        ))) {
            log.info("[AiClassifier] Keyword fallback → ask_price (matched in '{}')", lower);
            return ClassificationResult.builder()
                    .intent("ask_price").sentiment("neutral")
                    .requiresReply(true).confidence(0.65).fallback(true)
                    .build();
        }

        // ── Khen ngợi ─────────────────────────────────────────────────────────
        if (containsAny(lower, List.of(
                "hay lắm", "tốt lắm", "đẹp lắm", "ngon lắm", "chất lượng",
                "uy tín", "thích", "recommend", "love", "great", "nice",
                "awesome", "tuyệt", "xuất sắc"
        ))) {
            log.info("[AiClassifier] Keyword fallback → compliment (matched in '{}')", lower);
            return ClassificationResult.builder()
                    .intent("compliment").sentiment("positive")
                    .requiresReply(true).confidence(0.65).fallback(true)
                    .build();
        }

        // ── Khiếu nại ─────────────────────────────────────────────────────────
        if (containsAny(lower, List.of(
                "tệ", "kém", "lỗi", "hỏng", "không tốt", "hoàn tiền",
                "refund", "bad", "worst", "terrible", "chờ lâu",
                "thất vọng", "không hài lòng", "bị lỗi", "trả hàng"
        ))) {
            log.info("[AiClassifier] Keyword fallback → complaint (matched in '{}')", lower);
            return ClassificationResult.builder()
                    .intent("complaint").sentiment("negative")
                    .requiresReply(false).confidence(0.65).fallback(true)
                    .build();
        }

        log.info("[AiClassifier] Keyword fallback → other (no keyword matched for '{}')", lower);
        return buildFallback();
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /**
     * Hard fallback cuối cùng – không làm gì (IGNORE).
     */
    private ClassificationResult buildFallback() {
        return ClassificationResult.builder()
                .intent("other")
                .sentiment("neutral")
                .requiresReply(false)
                .confidence(0.0)
                .fallback(true)
                .build();
    }

    private String sanitize(String content) {
        if (content == null) return "";
        // Escape double quotes để không phá vỡ prompt JSON
        return content.replace("\"", "'").substring(0, Math.min(content.length(), 500));
    }
}
