package com.dev.backendapi.infrastructure.consumer;

import com.dev.backendapi.application.service.FacebookSenderService;
import com.dev.backendapi.domain.model.SendFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Component
public class SendRetryConsumer {
    private final FacebookSenderService facebookSenderService;

    @KafkaListener(
            topics = "send_retry",
            groupId = "backend-api-retry-group",
            containerFactory = "sendRetryListenerFactory"
    )
    public void consume(
            @Payload SendFailedEvent failedEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("[SendRetryConsumer] command={} retryCount={} partition={} offset={}",
                failedEvent.getCommandId(), failedEvent.getRetryCount(), partition, offset);

        facebookSenderService.execute(
                failedEvent.getCommand(),
                failedEvent.getRetryCount()
        );
    }
}
