package com.dev.webhookservice.Service;

import com.dev.webhookservice.Dtos.NormalizedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final KafkaTemplate<String, NormalizedEvent> kafkaTemplate;

    @Value("${facebook.app-secret}")
    private String appSecret;

    private static final String TOPIC = "raw_events";

    public void processWebhook(byte[] rawBody, String signature) throws Exception {
        log.info("Raw body: {}", rawBody);
        String payload = new String(rawBody, StandardCharsets.UTF_8);
        log.info("=== Incoming webhook payload ===\n{}", payload);

        // Facebook Test button (trên Dashboard) không gửi signature
        // => Chỉ verify khi signature tồn tại
        if (signature != null) {
            if (!verifySignature(rawBody, signature)) {
                log.error("Signature verification FAILED!\n  Received : {}\n  Payload  : {}", signature, payload);
                throw new RuntimeException("Invalid signature");
            }
            log.info("Signature verified OK.");
        } else {
            log.warn("No signature header — skipping verification (likely Facebook Dashboard test)");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(payload);
        log.info("Json node Root: {}", root);
        String object = root.path("object").asText("");
        log.info("Webhook object type: '{}'", object);

        if ("page".equals(object)) {
            JsonNode entries = root.get("entry");
            if (entries == null || entries.isEmpty()) {
                log.warn("No 'entry' array found in payload");
                return;
            }
            entries.forEach(entry -> {
                log.info("Processing entry: {}", entry);
                if (entry.has("changes") && !entry.get("changes").isNull()) {
                    entry.get("changes").forEach(change -> {
                        try {
                            NormalizedEvent event = normalizeEvent(change, entry);
                            kafkaTemplate.send(TOPIC, event.getEventId(), event);
                            log.info("Published feed event: {} | commentId={} | content='{}'",
                                    event.getEventType(), event.getCommentId(), event.getContent());
                        } catch (Exception e) {
                            log.error("Error processing feed event: {}", e.getMessage(), e);
                        }
                    });
                }

                if (entry.has("messaging") && !entry.get("messaging").isNull()) {
                    entry.get("messaging").forEach(msg -> {
                        try {
                            NormalizedEvent event = normalizeMessage(msg, entry);
                            kafkaTemplate.send(TOPIC, event.getEventId(), event);
                            log.info("Published message event: {} | senderId={}", event.getEventType(), event.getSenderId());
                        } catch (Exception e) {
                            log.error("Error processing message event: {}", e.getMessage(), e);
                        }
                    });
                }
            });
        } else {
            log.warn("Received unknown object type: '{}'. Full payload: {}", object, payload);
        }
    }

    private boolean verifySignature(byte[] rawBody, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" +
                    HexFormat.of().formatHex(mac.doFinal(rawBody));
            return expected.equals(signature);
        } catch (Exception e) {
            log.error("Error verifying signature", e);
            return false;
        }
    }

    private NormalizedEvent normalizeMessage(JsonNode msg, JsonNode entry) {
        return NormalizedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("message")
                .pageId(entry.get("id").asText())
                .senderId(msg.path("sender").path("id").asText(""))
                .recipientId(msg.path("recipient").path("id").asText(""))
                .content(msg.path("message").path("text").asText(""))
                .timestamp(System.currentTimeMillis())
                .rawPayload(new ObjectMapper().convertValue(msg, Map.class))
                .build();
    }

    private NormalizedEvent normalizeEvent(JsonNode change, JsonNode entry) {
        String field = change.get("field").asText(); // "feed" (comment) hoặc "messages"
        JsonNode value = change.get("value");

        // Lấy comment ID và post ID từ value node (Facebook feed event)
        String commentId = value.path("comment_id").asText(null);
        if (commentId == null || commentId.isBlank()) {
            // Fallback: một số event dùng "id" trực tiếp
            commentId = value.path("id").asText(null);
        }
        String postId = value.path("post_id").asText(null);

        return NormalizedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(field.contains("feed") ? "comment" : "message")
                .pageId(entry.get("id").asText())
                .senderId(value.path("from").path("id").asText(""))
                .content(value.path("message").asText(""))
                .commentId(commentId)
                .postId(postId)
                .timestamp(System.currentTimeMillis())
                .rawPayload(new ObjectMapper().convertValue(value, Map.class))
                .build();
    }


}