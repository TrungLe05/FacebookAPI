package com.dev.backendapi.infrastructure.adapter;

import com.dev.backendapi.domain.model.*;
import com.dev.backendapi.domain.port.FacebookGateway;
import com.dev.backendapi.infrastructure.config.FacebookProperties;
import com.dev.backendapi.infrastructure.exception.FacebookApiException;
import com.dev.backendapi.infrastructure.exception.FacebookNonRetryableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacebookGatewayImpl implements FacebookGateway {

    private final RestTemplate restTemplate;
    private final FacebookProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public PageInfo getPageInfo(String pageId) {
        String url = UriComponentsBuilder
                .fromUriString(props.getBaseUrl() + "/" + pageId)
                .queryParam("fields", "id,name,fan_count,followers_count,about,website")
                .queryParam("access_token", props.getToken())
                .toUriString();
        Map<String, Object> raw = get(url);
        return PageInfo.builder()
                .id(str(raw, "id"))
                .name(str(raw, "name"))
                .fanCount(longVal(raw, "fan_count"))
                .followersCount(longVal(raw, "followers_count"))
                .about(str(raw, "about"))
                .website(str(raw, "website"))
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Post> getPagePosts(String pageId) {
        String url = UriComponentsBuilder
                .fromUriString(props.getBaseUrl() + "/" + pageId + "/posts")
                .queryParam("fields", "id,message,created_time,full_picture,permalink_url")
                .queryParam("access_token", props.getToken())
                .toUriString();
        Map<String, Object> raw = get(url);
        List<Map<String, Object>> data = (List<Map<String, Object>>) raw.get("data");
        if (data == null) return Collections.emptyList();
        return data.stream().map(p -> Post.builder()
                .id(str(p, "id"))
                .message(str(p, "message"))
                .createdTime(str(p, "created_time"))
                .fullPicture(str(p, "full_picture"))
                .permalinkUrl(str(p, "permalink_url"))
                .build()).collect(Collectors.toList());
    }

    @Override
    public String createPost(String pageId, String message, String link) {
        String url = props.getBaseUrl() + "/" + pageId + "/feed";
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        body.put("access_token", props.getToken());
        if (link != null && !link.isBlank()) body.put("link", link);
        Map<String, Object> result = call(url, HttpMethod.POST, body);
        return str(result, "id");
    }

    @Override
    public boolean deletePost(String postId) {
        String url = UriComponentsBuilder
                .fromUriString(props.getBaseUrl() + "/" + postId)
                .queryParam("access_token", props.getToken())
                .toUriString();
        Map<String, Object> result = call(url, HttpMethod.DELETE, null);
        return Boolean.TRUE.equals(result.get("success"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Insight> getInsights(String pageId) {
        String url = UriComponentsBuilder
                .fromUriString(props.getBaseUrl() + "/" + pageId + "/insights")
                .queryParam("metric", "page_impressions,page_engaged_users,page_fans")
                .queryParam("period", "day")
                .queryParam("access_token", props.getToken())
                .toUriString();
        Map<String, Object> raw = get(url);
        List<Map<String, Object>> data = (List<Map<String, Object>>) raw.get("data");
        if (data == null) return Collections.emptyList();
        return data.stream().map(i -> Insight.builder()
                .id(str(i, "id"))
                .name(str(i, "name"))
                .period(str(i, "period"))
                .title(str(i, "title"))
                .description(str(i, "description"))
                .values((List<Map<String, Object>>) i.get("values"))
                .build()).collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Comment> getPostComments(String postId) {
        String url = UriComponentsBuilder
                .fromUriString(props.getBaseUrl() + "/" + postId + "/comments")
                .queryParam("fields", "id,message,from,created_time")
                .queryParam("access_token", props.getToken())
                .toUriString();
        Map<String, Object> raw = get(url);
        List<Map<String, Object>> data = (List<Map<String, Object>>) raw.get("data");
        if (data == null) return Collections.emptyList();
        return data.stream().map(c -> {
            Map<String, Object> from = (Map<String, Object>) c.get("from");
            return Comment.builder()
                    .id(str(c, "id"))
                    .message(str(c, "message"))
                    .createdTime(str(c, "created_time"))
                    .fromName(from != null ? str(from, "name") : null)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public LikeSummary getPostLikes(String postId) {
        String url = UriComponentsBuilder
                .fromUriString(props.getBaseUrl() + "/" + postId + "/likes")
                .queryParam("summary", "true")
                .queryParam("access_token", props.getToken())
                .toUriString();
        Map<String, Object> raw = get(url);
        Map<String, Object> summary = (Map<String, Object>) raw.get("summary");
        if (summary == null) return LikeSummary.builder().totalCount(0L).build();
        return LikeSummary.builder()
                .totalCount(longVal(summary, "total_count"))
                .canLike((Boolean) summary.get("can_like"))
                .hasLiked((Boolean) summary.get("has_liked"))
                .build();
    }

    @Override
    public boolean hideComment(String commentId) {
        if (commentId == null || commentId.isBlank()) {
            log.warn("[FacebookGateway] hideComment – commentId is null/blank");
            return false;
        }
        String url = withToken("/" + commentId);
        log.info("[FacebookGateway] Hiding comment {}", commentId);

        try {
            Map<String, Object> result = callWithBody(url, HttpMethod.POST,
                    Map.of("is_hidden", true));
            boolean success = Boolean.TRUE.equals(result.get("success"));
            log.info("[FacebookGateway] hideComment {} → {}", commentId, success);
            return success;

        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            // error_subcode 1446036 = đã bị ẩn rồi → coi như thành công, không retry
            if (body.contains("1446036")) {
                log.info("[FacebookGateway] Comment {} already hidden – treating as success", commentId);
                return true;
            }
            log.error("[FacebookGateway] HTTP {} hiding {}: {}", e.getStatusCode(), commentId, body);
            throw parseAndWrap("hideComment " + commentId, body);
        }
    }

    @Override
    public String replyToComment(String commentId, String message) {
        if (commentId == null || commentId.isBlank()) {
            log.warn("[FacebookGateway] replyToComment – commentId is null/blank");
            return null;
        }
        String url = withToken("/" + commentId + "/comments");
        log.info("[FacebookGateway] Replying to comment {}", commentId);

        try {
            Map<String, Object> result = callWithBody(url, HttpMethod.POST,
                    Map.of("message", message));
            String newId = str(result, "id");
            log.info("[FacebookGateway] Reply sent → new comment id {}", newId);
            return newId;

        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            log.error("[FacebookGateway] HTTP {} replying to {}: {}", e.getStatusCode(), commentId, body);
            throw parseAndWrap("replyToComment " + commentId, body);
        }
    }

    @Override
    public void sendMessage(String recipientId, String message) {
        if (recipientId == null || recipientId.isBlank()) {
            log.warn("[FacebookGateway] sendMessage – recipientId is null/blank");
            return;
        }
        String url = withToken("/me/messages");
        log.info("[FacebookGateway] Sending message to {}", recipientId);

        try {
            callWithBody(url, HttpMethod.POST, Map.of(
                    "recipient", Map.of("id", recipientId),
                    "message",   Map.of("text", message)
            ));
            log.info("[FacebookGateway] Message sent to {}", recipientId);

        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            log.error("[FacebookGateway] HTTP {} sending message to {}: {}",
                    e.getStatusCode(), recipientId, body);
            throw parseAndWrap("sendMessage " + recipientId, body);
        }
    }

    @Override
    public boolean deleteComment(String commentId) {
        if (commentId == null || commentId.isBlank()) {
            log.warn("[FacebookGateway] deleteComment – commentId is null/blank");
            return false;
        }
        String url = withToken("/" + commentId);
        log.info("[FacebookGateway] Deleting comment {}", commentId);

        try {
            Map<String, Object> result = call(url, HttpMethod.DELETE, null);
            boolean success = Boolean.TRUE.equals(result.get("success"));
            log.info("[FacebookGateway] deleteComment {} → {}", commentId, success);
            return success;

        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            log.error("[FacebookGateway] HTTP {} deleting {}: {}", e.getStatusCode(), commentId, body);
            throw parseAndWrap("deleteComment " + commentId, body);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String withToken(String path) {
        return UriComponentsBuilder
                .fromUriString(props.getBaseUrl() + path)
                .queryParam("access_token", props.getToken())
                .toUriString();
    }

    private Map<String, Object> get(String url) {
        return call(url, HttpMethod.GET, null);
    }

    /** POST với JSON body — dùng cho automation APIs. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callWithBody(String url, HttpMethod method,
                                             Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.exchange(url, method, entity, Map.class);
        return resp.getBody() != null ? resp.getBody() : Map.of("success", true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String url, HttpMethod method,
                                     Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, method, entity, Map.class);
            return resp.getBody() != null ? resp.getBody() : Map.of("success", true);
        } catch (HttpClientErrorException e) {
            // Re-throw để caller xử lý đúng loại exception
            throw e;
        } catch (Exception e) {
            log.error("[FacebookGateway] Unexpected error: {}", e.getMessage());
            throw new FacebookApiException(e.getMessage());
        }
    }

    /**
     * Parse lỗi Facebook và throw đúng loại exception.
     * code=190, subcode=463/467 → FacebookNonRetryableException (không retry)
     * Còn lại → FacebookApiException (có thể retry)
     */
    private RuntimeException parseAndWrap(String context, String responseBody) {
        try {
            JsonNode error = objectMapper.readTree(responseBody).path("error");
            int code    = error.path("code").asInt(0);
            int subcode = error.path("error_subcode").asInt(0);
            String msg  = error.path("message").asText(responseBody);

            if (code == 190 || subcode == 463 || subcode == 467) {
                log.error("[FacebookGateway] Token expired (code={} subcode={}) – cần cập nhật PAGE_ACCESS_TOKEN",
                        code, subcode);
                return new FacebookNonRetryableException(
                        context + " – Token expired: " + msg, code, subcode);
            }
        } catch (Exception ignored) { /* JSON parse failed, fall through */ }
        return new FacebookApiException(context + ": " + responseBody);
    }


    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private Long longVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        return Long.parseLong(v.toString());
    }
}
