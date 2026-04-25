package com.dev.webhookservice.Dtos;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NormalizedEvent {
    private String eventId;
    private String eventType;   // "comment" | "message"
    private String senderId;
    private String recipientId;
    private String content;
    private String pageId;
    private long timestamp;
    private Map<String, Object> rawPayload;
}
