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
            - ask_price: user asks about price, availability, shipping
            - complaint: user reports issue, unhappy, demands refund
            - compliment: user praises, positive feedback
            - spam: promotional, off-topic, gibberish
            - other: anything else
            
            Comment: "%s"
            """;

    public ClassificationResult classify(NormalizedEvent event) {
        log.info("[AiClassifier] Using model: {} endpoint: {}",
                props.getAi().getGroq().getModel(),
                props.getAi().getGroq().getEndpoint());

        try {
            return callGroqApi(event);
        } catch (Exception e) {
            log.error("[AiClassifier] Groq failed or not configured: {}. Trying Gemini...", e.getMessage());
            try {
                return callGeminiApi(event);
            } catch (Exception ex) {
                log.error("[AiClassifier] Gemini also failed: {}. Using fallback.", ex.getMessage());
                return buildFallback();
            }
        }
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

    private ClassificationResult callClaudeApi(NormalizedEvent event) {
        String apiKey = props.getAi().getAnthropic().getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("DISABLED")) {
            throw new RuntimeException("Anthropic API key not configured");
        }

        String prompt = PROMPT_TEMPLATE.formatted(sanitize(event.getContent()));
        Map<String, Object> requestBody = Map.of(
                "model", props.getAi().getAnthropic().getModel(),
                "max_tokens", 200,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", prompt
                ))
        );

        String responseJson = webClient.post()
                .uri(props.getAi().getAnthropic().getEndpoint())
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response ->
                        response.bodyToMono(String.class).flatMap(body -> {
                            log.error("[AiClassifier] Claude API 4xx body: {}", body);
                            return Mono.error(new RuntimeException("Claude error: " + body));
                        })
                )
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        if (responseJson == null) throw new RuntimeException("Empty response from Claude");
        return parseClaudeResponse(responseJson);
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

    private ClassificationResult parseClaudeResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            // Claude trả về content[0].text
            String text = root
                    .path("content").get(0)
                    .path("text").asText();

            // Parse JSON result
            JsonNode result = objectMapper.readTree(text.trim());
            return ClassificationResult.builder()
                    .intent(result.path("intent").asText("other"))
                    .sentiment(result.path("sentiment").asText("neutral"))
                    .requiresReply(result.path("requires_reply").asBoolean(false))
                    .confidence(result.path("confidence").asDouble(0.5))
                    .fallback(false)
                    .build();
        } catch (Exception e) {
            log.error("[AiClassifier] Failed to parse Claude response: {}", e.getMessage());
            return buildFallback();
        }
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
     * Fallback khi AI không available – dùng keyword heuristic đơn giản.
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
