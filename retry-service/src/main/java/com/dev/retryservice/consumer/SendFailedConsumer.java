package com.dev.retryservice.consumer;

import com.dev.retryservice.Models.SendFailedEvent;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendFailedConsumer {

    private final KafkaTemplate<String, SendFailedEvent> retryKafkaTemplate;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4);
    private static final String SEND_RETRY_TOPIC  = "send_retry";
    private static final String DEAD_LETTER_TOPIC = "dead_letter";
    private static final int    MAX_RETRIES       = 3;

    @KafkaListener(
            topics = "send_failed",
            groupId = "retry-service-group",
            containerFactory = "sendFailedListenerFactory"
    )
    public void consume(
            @Payload SendFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        int retryCount = event.getRetryCount();
        String commandId = event.getCommandId();

        log.warn("[RetryService] command={} retryCount={} error={} partition={} offset={}",
                commandId, retryCount, event.getLastError(), partition, offset);

        if (retryCount >= MAX_RETRIES) {
            log.error("[RetryService] command={} exhausted {} retries → dead_letter",
                    commandId, MAX_RETRIES);
            publishDeadLetter(event);
            return;
        }

        // Exponential backoff: retry 0→1s, 1→2s, 2→4s
        long delayMs = (long) (1000 * Math.pow(2, retryCount));
        log.info("[RetryService] command={} waiting {}ms before retry attempt {}",
                commandId, delayMs, retryCount + 1);

        scheduler.schedule(
                () -> publishSendRetry(event, retryCount),
                delayMs,
                TimeUnit.MILLISECONDS
        );
    }

    private void publishSendRetry(SendFailedEvent original, int retryCount) {
        SendFailedEvent retryEvent = SendFailedEvent.builder()
                .schemaVersion(original.getSchemaVersion())
                .commandId(original.getCommandId())
                .eventId(original.getEventId())
                .retryCount(retryCount + 1)         // tăng counter
                .lastError(original.getLastError())
                .nextRetryAt(Instant.now())
                .failedAt(original.getFailedAt())
                .command(original.getCommand())     // giữ nguyên payload gốc
                .build();

        retryKafkaTemplate.send(SEND_RETRY_TOPIC, original.getCommandId(), retryEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[RetryService] Failed to publish send_retry for command={}: {}",
                                original.getCommandId(), ex.getMessage());
                    } else {
                        log.info("[RetryService] Published send_retry command={} attempt={}",
                                original.getCommandId(), retryCount + 1);
                    }
                });
    }

    private void publishDeadLetter(SendFailedEvent original) {
        retryKafkaTemplate.send(DEAD_LETTER_TOPIC, original.getCommandId(), original)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[RetryService] Failed to publish dead_letter for command={}: {}",
                                original.getCommandId(), ex.getMessage());
                    } else {
                        log.warn("[RetryService] Dead letter published for command={}",
                                original.getCommandId());
                    }
                });
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
