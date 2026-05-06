package com.dev.coreservice.consumer;

import com.dev.coreservice.model.*;
import com.dev.coreservice.pipeline.AiClassifier;
import com.dev.coreservice.pipeline.DecisionEngine;
import com.dev.coreservice.pipeline.SpamDetector;
import com.dev.coreservice.service.EventStatusService;
import com.dev.coreservice.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer cho topic raw_events.
 * Orchestrate toàn bộ processing pipeline:
 *
 *  raw_events → SpamDetector → AiClassifier → DecisionEngine → RetryService(FacebookActionExecutor)
 *            └──────────────────────────────────────────────→ EventStatusService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RawEventConsumer {

    private final SpamDetector spamDetector;
    private final AiClassifier aiClassifier;
    private final DecisionEngine decisionEngine;
    private final RetryService retryService;
    private final EventStatusService eventStatusService;
    private final org.springframework.kafka.core.KafkaTemplate<String, NormalizedEvent> kafkaTemplate;

    @KafkaListener(
            topics = "raw_events",
            groupId = "core-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload NormalizedEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("[Consumer] Received event={} type={} partition={} offset={}",
                event.getEventId(), event.getEventType(), partition, offset);

        // Bước 1: Đánh dấu đã nhận
        eventStatusService.markReceived(event.getEventId());

        try {
            // Bước 2: Kiểm tra spam (rule-based, nhanh)
            SpamResult spamResult = spamDetector.check(event);

            // Bước 3: AI classification (chỉ chạy khi không phải hard spam)
            ClassificationResult classification;
            if (spamResult.isHardSpam() || spamResult.isBlacklisted()) {
                // Short-circuit: không cần AI cho hard spam rõ ràng
                classification = ClassificationResult.builder()
                        .intent("spam").sentiment("neutral")
                        .requiresReply(false).confidence(1.0).fallback(false)
                        .build();
            } else {
                classification = aiClassifier.classify(event);
            }

            // Bước 4: Ra quyết định
            Decision decision = decisionEngine.decide(event, spamResult, classification);

            // Bước 5: Thực thi (với retry)
            retryService.executeWithRetry(event, decision);

        } catch (Exception e) {
            log.error("[Consumer] Unhandled error for event {}: {}", event.getEventId(), e.getMessage(), e);
            eventStatusService.markFailed(event.getEventId(), e.getMessage(), 0);
            kafkaTemplate.send("dead_letter_events", event.getEventId(), event);
        }
    }
}
