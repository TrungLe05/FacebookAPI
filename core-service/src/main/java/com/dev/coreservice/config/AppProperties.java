package com.dev.coreservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "")
public class AppProperties {

    private Facebook facebook = new Facebook();
    private Ai ai = new Ai();
    private Spam spam = new Spam();

    @Data
    public static class Facebook {
        private String pageAccessToken;
        private String baseUrl = "https://graph.facebook.com/v21.0";
        private String graphApiVersion = "v21.0";
        private String pageId;
    }

    @Data
    public static class Ai {
        private Gemini gemini = new Gemini();
        private Anthropic anthropic = new Anthropic();
        private Groq groq = new Groq(); // Thêm Groq

        @Data
        public static class Groq {
            private String apiKey;
            private String model = "llama-3.1-8b-instant";
            private String endpoint = "https://api.groq.com/openai/v1/chat/completions";
        }

        @Data
        public static class Anthropic {
            private String apiKey;
            private String model = "claude-haiku-4-5-20251001";
            private String endpoint = "https://api.anthropic.com/v1/messages";
        }

        @Data
        public static class Gemini {
            private String apiKey;
            private String model = "gemini-2.0-flash-lite";
            private String endpoint = "https://generativelanguage.googleapis.com/v1beta/models";
        }
    }

    @Data
    public static class Spam {
        // Regex pattern phát hiện URL / link rút gọn
        private String urlPattern = "(https?://|bit\\.ly|tinyurl\\.com|t\\.co|goo\\.gl|fb\\.me)";
        // Số lần lặp trong cùng 1 cửa sổ thời gian → hard spam
        private int repeatThreshold = 3;
        // Cửa sổ thời gian tính lặp (giờ)
        private int repeatWindowHours = 24;
        // TTL của status record trong Redis (giây) – mặc định 7 ngày
        private long statusTtlSeconds = 7 * 24 * 3600L;
    }
}
