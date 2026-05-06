package com.dev.coreservice.client;

import com.dev.coreservice.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacebookClient {

    private final AppProperties props;

    @Value("${facebook.mock-enabled:false}")
    private boolean mockEnabled;

    private RestTemplate buildRestTemplate() {
        return new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Ẩn comment trên Facebook Page.
     */
    public boolean hideComment(String commentId) {
        if (commentId == null || commentId.isBlank()) {
            log.warn("[FacebookClient] Cannot hide comment – commentId is null");
            return false;
        }

        // ── MOCK MODE ──────────────────────────────────────────────────────────
        if (mockEnabled) {
            log.info("[MOCK] hideComment skipped – commentId={}", commentId);
            return true;
        }
        // ──────────────────────────────────────────────────────────────────────

        String url = props.getFacebook().getBaseUrl() + "/" + commentId;
        Map<String, Object> body = new HashMap<>();
        body.put("is_hidden", true);
        body.put("access_token", props.getFacebook().getPageAccessToken());
        try {
            Map<?, ?> result = post(url, body);
            boolean success = Boolean.TRUE.equals(result.get("success"));
            if (success) {
                log.info("[FacebookClient] Comment {} hidden successfully", commentId);
            } else {
                log.warn("[FacebookClient] Failed to hide comment {}: {}", commentId, result);
            }
            return success;
        } catch (Exception e) {
            log.error("[FacebookClient] Error hiding comment {}: {}", commentId, e.getMessage());
            throw new RuntimeException("Failed to hide comment: " + e.getMessage(), e);
        }
    }

    /**
     * Reply vào comment.
     */
    public String replyToComment(String commentId, String message) {
        if (commentId == null || commentId.isBlank()) {
            log.warn("[FacebookClient] Cannot reply – commentId is null");
            return null;
        }

        // ── MOCK MODE ──────────────────────────────────────────────────────────
        if (mockEnabled) {
            log.info("[MOCK] replyToComment skipped – commentId={} message={}", commentId, message);
            return "mock_reply_id_" + System.currentTimeMillis();
        }
        // ──────────────────────────────────────────────────────────────────────

        String url = props.getFacebook().getBaseUrl() + "/" + commentId + "/comments";
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        body.put("access_token", props.getFacebook().getPageAccessToken());
        try {
            Map<?, ?> result = post(url, body);
            String newCommentId = String.valueOf(result.get("id"));
            log.info("[FacebookClient] Replied to comment {} → new comment id: {}", commentId, newCommentId);
            return newCommentId;
        } catch (Exception e) {
            log.error("[FacebookClient] Error replying to comment {}: {}", commentId, e.getMessage());
            throw new RuntimeException("Failed to reply to comment: " + e.getMessage(), e);
        }
    }

    /**
     * Gửi tin nhắn DM qua Messenger API.
     */
    public void sendMessage(String recipientId, String message) {

        // ── MOCK MODE ──────────────────────────────────────────────────────────
        if (mockEnabled) {
            log.info("[MOCK] sendMessage skipped – recipientId={} message={}", recipientId, message);
            return;
        }
        // ──────────────────────────────────────────────────────────────────────

        String url = props.getFacebook().getBaseUrl() + "/me/messages"
                + "?access_token=" + props.getFacebook().getPageAccessToken();
        Map<String, Object> body = Map.of(
                "recipient", Map.of("id", recipientId),
                "message", Map.of("text", message)
        );
        try {
            post(url, body);
            log.info("[FacebookClient] Message sent to recipient {}", recipientId);
        } catch (Exception e) {
            log.error("[FacebookClient] Error sending message to {}: {}", recipientId, e.getMessage());
            throw new RuntimeException("Failed to send message: " + e.getMessage(), e);
        }
    }

    /**
     * Xóa comment.
     */
    public boolean deleteComment(String commentId) {

        // ── MOCK MODE ──────────────────────────────────────────────────────────
        if (mockEnabled) {
            log.info("[MOCK] deleteComment skipped – commentId={}", commentId);
            return true;
        }
        // ──────────────────────────────────────────────────────────────────────

        String url = props.getFacebook().getBaseUrl() + "/" + commentId
                + "?access_token=" + props.getFacebook().getPageAccessToken();
        try {
            RestTemplate restTemplate = buildRestTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, null, Map.class);
            boolean success = response.getBody() != null
                    && Boolean.TRUE.equals(response.getBody().get("success"));
            log.info("[FacebookClient] Comment {} deleted: {}", commentId, success);
            return success;
        } catch (Exception e) {
            log.error("[FacebookClient] Error deleting comment {}: {}", commentId, e.getMessage());
            throw new RuntimeException("Failed to delete comment: " + e.getMessage(), e);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Map<String, Object> body) {
        RestTemplate restTemplate = buildRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            return response.getBody() != null ? response.getBody() : Map.of("success", true);
        } catch (HttpClientErrorException e) {
            log.error("[FacebookClient] HTTP {} from FB API: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }
}