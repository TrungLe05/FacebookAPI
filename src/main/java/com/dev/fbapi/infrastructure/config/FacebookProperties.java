package com.dev.fbapi.infrastructure.config;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "facebook")
public class FacebookProperties {

    Page page = new Page();
    Graph graph = new Graph();

    @FieldDefaults(level = AccessLevel.PRIVATE)
    @Getter
    @Setter
    public static class Page {
        String id;
        String accessToken;
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    @Getter
    @Setter
    public static class Graph {
        Api api = new Api();

        @FieldDefaults(level = AccessLevel.PRIVATE)
        @Getter
        @Setter
        public static class Api {
            String url;
        }
    }

    public String getBaseUrl() {
        return graph.getApi().getUrl();
    }

    public String getToken() {
        return page.getAccessToken();
    }
}
