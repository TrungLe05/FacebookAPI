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
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

/**
 * Client gọi Facebook Graph API.
 *
 * Quy tắc Graph API:
 *  - access_token phải nằm trong query parameter (không phải JSON body)
 *    khi Content-Type là application/json.
 *  - hideComment  : POST /{comment-id}?access_token=...  body: {"is_hidden": true}
 *  - replyComment : POST /{comment-id}/comments?access_token=...  body: {"message": "..."}
 *  - sendMessage  : POST /me/messages?access_token=...  body: {recipient, message}
 *  - deleteComment: DELETE /{comment-id}?access_token=...
 */
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

    // ── Private helper: build URL có access_token ──────────────────────────────

    private String withToken(String path) {
        String base = props.getFacebook().getBaseUrl();   // https://graph.facebook.com/v21.0
        String token = props.getFacebook().getPageAccessToken();
        return UriComponentsBuilder
                .fromHttpUrl(base + path)
                .queryParam("access_token", token)
                .toUriString();
    }

    // ── hideComment ────────────────────────────────────────────────────────────

    /**
     * Ẩn comment trên Facebook Page.
     * Graph API: POST /{comment-id}?access_token=... body: {"is_hidden": true}
     */
    public boolean hideComment(String commentId) {
        if (commentId == null || commentId.isBlank()) {
            log.warn("[FacebookClient] Cannot hide comment – commentId is null/blank");
            return false;
        }

        if (mockEnabled) {
            log.info("[MOCK] hideComment skipped – commentId={}", commentId);
            return true;
        }

        String url = withToken("/" + commentId);
        Map<String, Object> body = Map.of("is_hidden", true);
        log.info("[FacebookClient] Hiding comment {} → POST {}", commentId, url);

        try {
            Map<?, ?> result = postJson(url, body);
            boolean success = Boolean.TRUE.equals(result.get("success"));
            if (success) {
                log.info("[FacebookClient] Comment {} hidden successfully", commentId);
            } else {
                log.warn("[FacebookClient] hide comment {} response: {}", commentId, result);
            }
            return success;
        } catch (HttpClientErrorException e) {
            String body2 = e.getResponseBodyAsString();
            // error_subcode 1446036 = comment đã bị ẩn/đánh spam rồi → coi là thành công, không retry
            if (body2.contains("1446036")) {
                log.info("[FacebookClient] Comment {} already hidden (duplicate request) – treating as success", commentId);
                return true;
            }
            log.error("[FacebookClient] HTTP {} hiding comment {}: {}",
                    e.getStatusCode(), commentId, body2);
            throw new RuntimeException("Failed to hide comment " + commentId + ": " + body2, e);
        } catch (Exception e) {
            log.error("[FacebookClient] Error hiding comment {}: {}", commentId, e.getMessage());
            throw new RuntimeException("Failed to hide comment: " + e.getMessage(), e);
        }
    }

    // ── replyToComment ─────────────────────────────────────────────────────────

    /**
     * Reply vào một comment.
     * Graph API: POST /{comment-id}/comments?access_token=... body: {"message": "..."}
     */
    public String replyToComment(String commentId, String message) {
        if (commentId == null || commentId.isBlank()) {
            log.warn("[FacebookClient] Cannot reply – commentId is null/blank");
            return null;
        }

        if (mockEnabled) {
            log.info("[MOCK] replyToComment skipped – commentId={} message={}", commentId, message);
            return "mock_reply_id_" + System.currentTimeMillis();
        }

        String url = withToken("/" + commentId + "/comments");
        Map<String, Object> body = Map.of("message", message);
        log.info("[FacebookClient] Replying to comment {} → POST {}", commentId, url);

        try {
            Map<?, ?> result = postJson(url, body);
            String newCommentId = String.valueOf(result.get("id"));
            log.info("[FacebookClient] Replied to comment {} → new comment id: {}", commentId, newCommentId);
            return newCommentId;
        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("[FacebookClient] HTTP {} replying to comment {}: {}",
                    e.getStatusCode(), commentId, responseBody);
            throw handleFacebookError("Failed to reply to comment " + commentId, responseBody);
        } catch (FacebookNonRetryableException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FacebookClient] Error replying to comment {}: {}", commentId, e.getMessage());
            throw new RuntimeException("Failed to reply to comment: " + e.getMessage(), e);
        }
    }

    // ── sendMessage ────────────────────────────────────────────────────────────

    /**
     * Gửi tin nhắn DM qua Messenger API.
     * Graph API: POST /me/messages?access_token=... body: {recipient, message}
     */
    public void sendMessage(String recipientId, String message) {
        if (recipientId == null || recipientId.isBlank()) {
            log.warn("[FacebookClient] Cannot send message – recipientId is null/blank");
            return;
        }

        if (mockEnabled) {
            log.info("[MOCK] sendMessage skipped – recipientId={} message={}", recipientId, message);
            return;
        }

        String url = withToken("/me/messages");
        Map<String, Object> body = Map.of(
                "recipient", Map.of("id", recipientId),
                "message", Map.of("text", message)
        );
        log.info("[FacebookClient] Sending message to {} → POST {}", recipientId, url);

        try {
            postJson(url, body);
            log.info("[FacebookClient] ✅ Message sent to recipient {}", recipientId);
        } catch (HttpClientErrorException e) {
            log.error("[FacebookClient] HTTP {} sending message to {}: {}",
                    e.getStatusCode(), recipientId, e.getResponseBodyAsString());
            throw new RuntimeException("Failed to send message: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("[FacebookClient] Error sending message to {}: {}", recipientId, e.getMessage());
            throw new RuntimeException("Failed to send message: " + e.getMessage(), e);
        }
    }

    // ── deleteComment ──────────────────────────────────────────────────────────

    /**
     * Xóa comment.
     * Graph API: DELETE /{comment-id}?access_token=...
     */
    public boolean deleteComment(String commentId) {
        if (commentId == null || commentId.isBlank()) {
            log.warn("[FacebookClient] Cannot delete comment – commentId is null/blank");
            return false;
        }

        if (mockEnabled) {
            log.info("[MOCK] deleteComment skipped – commentId={}", commentId);
            return true;
        }

        String url = withToken("/" + commentId);
        log.info("[FacebookClient] Deleting comment {} → DELETE {}", commentId, url);

        try {
            RestTemplate restTemplate = buildRestTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, null, Map.class);
            boolean success = response.getBody() != null
                    && Boolean.TRUE.equals(response.getBody().get("success"));
            log.info("[FacebookClient] Comment {} deleted: {}", commentId, success);
            return success;
        } catch (HttpClientErrorException e) {
            log.error("[FacebookClient] HTTP {} deleting comment {}: {}",
                    e.getStatusCode(), commentId, e.getResponseBodyAsString());
            throw new RuntimeException("Failed to delete comment: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("[FacebookClient] Error deleting comment {}: {}", commentId, e.getMessage());
            throw new RuntimeException("Failed to delete comment: " + e.getMessage(), e);
        }
    }

    // ── Internal POST helper ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> postJson(String url, Map<String, Object> body) {
        RestTemplate restTemplate = buildRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        return response.getBody() != null ? response.getBody() : Map.of("success", true);
    }

    /**
     * Parse lỗi Facebook và throw đúng loại exception.
     * code=190, subcode=463/467 → FacebookNonRetryableException (không retry)
     */
    private RuntimeException handleFacebookError(String message, String responseBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);
            JsonNode error = root.path("error");
            int code = error.path("code").asInt(0);
            int subcode = error.path("error_subcode").asInt(0);
            String errorMsg = error.path("message").asText(responseBody);

            if (code == 190 || subcode == 463 || subcode == 467) {
                log.error("[FacebookClient] Token expired (code={} subcode={}). "
                        + "Cần cập nhật PAGE_ACCESS_TOKEN trong .env và restart!", code, subcode);
                return new FacebookNonRetryableException(
                        message + " – Token expired: " + errorMsg, code, subcode);
            }
        } catch (Exception ignored) { /* JSON parse failed */ }
        return new RuntimeException(message + ": " + responseBody);
    }
}