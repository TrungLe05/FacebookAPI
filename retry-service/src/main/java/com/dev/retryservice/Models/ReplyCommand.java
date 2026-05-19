package com.dev.retryservice.Models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReplyCommand {
    private int schemaVersion;      // = 1
    private String commandId;       // UUID
    private String eventId;         // từ NormalizedEvent
    private String action;          // "reply" | "hide" | "delete" | "send_message" | "queue_review" | "ignore"
    private String pageId;
    private String commentId;
    private String senderId;
    private String eventType;       // "comment" | "message"
    private String replyText;       // null nếu action không phải reply/send_message
    private String intent;
    private String sentiment;
    private Instant createdAt;
}
