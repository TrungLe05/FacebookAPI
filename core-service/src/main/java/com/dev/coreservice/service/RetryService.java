package com.dev.coreservice.service;

import com.dev.coreservice.model.Decision;
import com.dev.coreservice.model.NormalizedEvent;
import com.dev.coreservice.pipeline.FacebookActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Bọc FacebookActionExecutor với Spring Retry.
 * - Max 3 lần, backoff exponential 1s → 2s → 4s.
 * - Sau khi hết lần retry → @Recover: publish sang dead_letter_events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetryService {

    private final FacebookActionExecutor facebookActionExecutor;
    private final EventStatusService eventStatusService;
    private final KafkaTemplate<String, NormalizedEvent> kafkaTemplate;

    private static final String DEAD_LETTER_TOPIC = "dead_letter_events";

    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void executeWithRetry(NormalizedEvent event, Decision decision) {
        log.info("[RetryService] Executing {} for event {}", decision, event.getEventId());
        facebookActionExecutor.execute(event, decision);
    }

    @Recover
    public void recover(Exception e, NormalizedEvent event, Decision decision) {
        log.error("[RetryService] All retries exhausted for event {}. Decision={}. Error: {}",
                event.getEventId(), decision, e.getMessage());

        eventStatusService.markDeadLetter(event.getEventId());

        // Publish sang dead letter để audit / alert
        kafkaTemplate.send(DEAD_LETTER_TOPIC, event.getEventId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[RetryService] Failed to publish dead letter for {}: {}",
                                event.getEventId(), ex.getMessage());
                    } else {
                        log.warn("[RetryService] Dead letter published for event {}", event.getEventId());
                    }
                });
    }
}
