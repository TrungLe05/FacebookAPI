package com.dev.webhookservice.Service;

import com.dev.webhookservice.Dtos.NormalizedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

    public void processWebhook(String payload, String signature) throws Exception {
        if (!verifySignature(payload, signature)) {
            throw new RuntimeException("Invalid signature");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(payload);
        String object = root.get("object").asText();

        if ("page".equals(object)) {
            root.get("entry").forEach(entry -> {
                // Xử lý feed (comment, post)
                if (entry.has("changes") && !entry.get("changes").isNull()) {
                    entry.get("changes").forEach(change -> {
                        try {
                            NormalizedEvent event = normalizeEvent(change, entry);
                            kafkaTemplate.send(TOPIC, event.getEventId(), event);
                            log.info("Published feed event: {}", event.getEventType());
                        } catch (Exception e) {
                            log.error("Error processing feed event", e);
                        }
                    });
                }

                // Xử lý messages
                if (entry.has("messaging") && !entry.get("messaging").isNull()) {
                    entry.get("messaging").forEach(msg -> {
                        try {
                            NormalizedEvent event = normalizeMessage(msg, entry);
                            kafkaTemplate.send(TOPIC, event.getEventId(), event);
                            log.info("Published message event: {}", event.getEventType());
                        } catch (Exception e) {
                            log.error("Error processing message event", e);
                        }
                    });
                }
            });
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

        return NormalizedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(field.contains("feed") ? "comment" : "message")
                .pageId(entry.get("id").asText())
                .senderId(value.path("from").path("id").asText(""))
                .content(value.path("message").asText(""))
                .timestamp(System.currentTimeMillis())
                .rawPayload(new ObjectMapper().convertValue(value, Map.class))
                .build();
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(), "HmacSHA256"));
            String expected = "sha256=" +
                    HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));
            return expected.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}