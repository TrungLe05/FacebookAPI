package com.dev.coreservice.service;

import com.dev.coreservice.config.AppProperties;
import com.dev.coreservice.model.EventStatusRecord;
import com.dev.coreservice.model.EventStatusRecord.Status;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Track trạng thái xử lý của mỗi event trong Redis.
 *
 * Flow:  RECEIVED → PROCESSING → PROCESSED | REPLIED | HIDDEN | FAILED → DEAD_LETTER
 *
 * Key:   event:{eventId}:status
 * TTL:   7 ngày (cấu hình được qua spam.statusTtlSeconds)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventStatusService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    private static final String PREFIX = "event:";
    private static final String SUFFIX = ":status";

    public void markReceived(String eventId) {
        save(EventStatusRecord.builder()
                .eventId(eventId)
                .status(Status.RECEIVED)
                .receivedAt(System.currentTimeMillis())
                .retryCount(0)
                .build());
        log.debug("[StatusService] {} → RECEIVED", eventId);
    }

    public void markProcessed(String eventId, String action) {
        EventStatusRecord record = load(eventId);
        if (record != null) {
            record.setStatus(Status.PROCESSED);
            record.setAction(action);
            record.setProcessedAt(System.currentTimeMillis());
        } else {
            record = EventStatusRecord.builder()
                    .eventId(eventId).status(Status.PROCESSED)
                    .action(action).processedAt(System.currentTimeMillis()).build();
        }
        save(record);
        log.info("[StatusService] {} → PROCESSED ({})", eventId, action);
    }

    public void markReplied(String eventId, String action) {
        EventStatusRecord record = loadOrNew(eventId);
        record.setStatus(Status.REPLIED);
        record.setAction(action);
        record.setProcessedAt(System.currentTimeMillis());
        save(record);
        log.info("[StatusService] {} → REPLIED", eventId);
    }

    public void markHidden(String eventId, String action) {
        EventStatusRecord record = loadOrNew(eventId);
        record.setStatus(Status.HIDDEN);
        record.setAction(action);
        record.setProcessedAt(System.currentTimeMillis());
        save(record);
        log.info("[StatusService] {} → HIDDEN ({})", eventId, action);
    }

    public void markFailed(String eventId, String errorMessage, int retryCount) {
        EventStatusRecord record = loadOrNew(eventId);
        record.setStatus(Status.FAILED);
        record.setErrorMessage(errorMessage);
        record.setRetryCount(retryCount);
        record.setProcessedAt(System.currentTimeMillis());
        save(record);
        log.error("[StatusService] {} → FAILED (retries={}): {}", eventId, retryCount, errorMessage);
    }

    public void markDeadLetter(String eventId) {
        EventStatusRecord record = loadOrNew(eventId);
        record.setStatus(Status.DEAD_LETTER);
        record.setProcessedAt(System.currentTimeMillis());
        save(record);
        log.error("[StatusService] {} → DEAD_LETTER", eventId);
    }

    public EventStatusRecord getStatus(String eventId) {
        return load(eventId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void save(EventStatusRecord record) {
        try {
            String key = PREFIX + record.getEventId() + SUFFIX;
            redisTemplate.opsForValue().set(key, record,
                    props.getSpam().getStatusTtlSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[StatusService] Failed to save status for {}: {}", record.getEventId(), e.getMessage());
        }
    }

    private EventStatusRecord load(String eventId) {
        try {
            Object raw = redisTemplate.opsForValue().get(PREFIX + eventId + SUFFIX);
            if (raw == null) return null;
            return objectMapper.convertValue(raw, EventStatusRecord.class);
        } catch (Exception e) {
            log.warn("[StatusService] Failed to load status for {}: {}", eventId, e.getMessage());
            return null;
        }
    }

    private EventStatusRecord loadOrNew(String eventId) {
        EventStatusRecord record = load(eventId);
        return record != null ? record : EventStatusRecord.builder()
                .eventId(eventId)
                .receivedAt(System.currentTimeMillis())
                .retryCount(0)
                .build();
    }
}
