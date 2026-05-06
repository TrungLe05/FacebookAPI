package com.dev.coreservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Shared schema với webhook-service.
 * Bổ sung commentId và postId để thực thi FB API actions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedEvent {
    private String eventId;
    private String eventType;       // "comment" | "message"
    private String senderId;
    private String recipientId;
    private String content;
    private String pageId;
    private String commentId;       // ID comment để hide/reply
    private String postId;          // ID post gốc chứa comment
    private long timestamp;
    private Map<String, Object> rawPayload;
}
