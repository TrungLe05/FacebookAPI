package com.dev.coreservice.consumer;

import com.dev.coreservice.config.AppProperties;
import com.dev.coreservice.model.*;
import com.dev.coreservice.pipeline.AiClassifier;
import com.dev.coreservice.pipeline.DecisionEngine;
import com.dev.coreservice.pipeline.ReplyCommandPublisher;
import com.dev.coreservice.pipeline.SpamDetector;
import com.dev.coreservice.service.EventStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final ReplyCommandPublisher replyCommandPublisher;  // ← THAY RetryService

    private final EventStatusService eventStatusService;
    private final AppProperties props;
    private final KafkaTemplate<String, NormalizedEvent> kafkaTemplate;

    @KafkaListener(
            topics = "raw_events",
            groupId = "core-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload NormalizedEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("[Consumer] event={} type={} partition={} offset={}",
                event.getEventId(), event.getEventType(), partition, offset);

        String pageId = props.getFacebook().getPageId();
        if (pageId != null && pageId.equals(event.getSenderId())) {
            log.info("[Consumer] Skipping own page event");
            return;
        }
        if (event.getSenderId() == null || event.getSenderId().isBlank()) {
            log.warn("[Consumer] Skipping blank senderId: {}", event.getEventId());
            return;
        }

        // đánh dấu là đã nhận event đó từ topic raw_events
        eventStatusService.markReceived(event.getEventId());

        try {
            // check spam trước khi call api AI -> decision engine
            SpamResult spamResult = spamDetector.check(event);

            ClassificationResult classification;
            //nếu event bị đánh dấu là spam thì build object classification với nội dung bên dưới
            if (spamResult.isHardSpam() || spamResult.isBlacklisted()) {
                classification = ClassificationResult.builder()
                        .intent("spam").sentiment("neutral")
                        .requiresReply(false).confidence(1.0).fallback(false)
                        .build();
            } else {
                // call api AI
                classification = aiClassifier.classify(event);
            }

            // đưa ra decision dựa vào classification
            Decision decision = decisionEngine.decide(event, spamResult, classification);

            // Publish command → backend-api sẽ thực thi
            replyCommandPublisher.publish(event, decision, classification);

        } catch (Exception e) {
            log.error("[Consumer] Unhandled error for event {}: {}", event.getEventId(), e.getMessage(), e);
            // đánh dấu là lỗi để send event vào topic dead_letter_events
            // và retry-service sẽ consume để tiếp tục process
            eventStatusService.markFailed(event.getEventId(), e.getMessage(), 0);
            kafkaTemplate.send("dead_letter_events", event.getEventId(), event);
        }
    }
}
