package com.dev.backendapi.domain.model;


import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyCommand {
    private int schemaVersion;
    private String commandId;
    private String eventId;
    private String action;       // reply | hide | hide_and_queue | blacklist_and_hide | queue_review | ignore
    private String pageId;
    private String commentId;
    private String senderId;
    private String eventType;    // comment | message
    private String replyText;
    private String intent;
    private String sentiment;
    private Instant createdAt;
}
