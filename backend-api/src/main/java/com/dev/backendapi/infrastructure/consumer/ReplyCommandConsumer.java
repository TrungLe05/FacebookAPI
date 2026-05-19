package com.dev.backendapi.infrastructure.consumer;

import com.dev.backendapi.application.service.FacebookSenderService;
import com.dev.backendapi.domain.model.ReplyCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReplyCommandConsumer {

    private final FacebookSenderService facebookSenderService;

    @KafkaListener(
            topics = "reply_commands",
            groupId = "backend-api-group",
            containerFactory = "replyCommandListenerFactory"
    )
    public void consume(
            @Payload ReplyCommand command,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("[ReplyCommandConsumer] command={} action={} partition={} offset={}",
                command.getCommandId(), command.getAction(), partition, offset);

        // retryCount = 0 vì đây là lần xử lý đầu tiên
        facebookSenderService.execute(command, 0);
    }
}
