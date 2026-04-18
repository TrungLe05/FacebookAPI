package com.dev.fbapi.infrastructure.adapter;

import com.dev.fbapi.domain.model.*;
import com.dev.fbapi.domain.port.FacebookGateway;
import com.dev.fbapi.infrastructure.config.FacebookProperties;
import com.dev.fbapi.infrastructure.exception.FacebookApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> get(String url) {
        return call(url, HttpMethod.GET, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String url, HttpMethod method, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, method, entity, Map.class);
            return resp.getBody() != null ? resp.getBody() : Map.of("success", true);
        } catch (Exception e) {
            log.error("Facebook API error: {}", e.getMessage());
            throw new FacebookApiException(e.getMessage());
        }
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
