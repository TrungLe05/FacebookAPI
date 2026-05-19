package com.dev.backendapi.domain.model;
import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendFailedEvent {
    private int schemaVersion;
    private String commandId;
    private String eventId;
    private int retryCount;
    private String lastError;
    private Instant nextRetryAt;
    private Instant failedAt;
    private ReplyCommand command;   // giữ nguyên payload để retry-service không cần query DB
}
