package com.dev.coreservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Record lưu trong Redis để theo dõi trạng thái xử lý từng event.
 * Key: event:{eventId}:status  |  TTL: 7 ngày
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStatusRecord {

    public enum Status {
        RECEIVED, PROCESSING, PROCESSED, REPLIED, HIDDEN, FAILED, DEAD_LETTER
    }

    private String eventId;
    private Status status;
    private String action;          // decision name
    private long receivedAt;
    private long processedAt;
    private int retryCount;
    private String errorMessage;
}
