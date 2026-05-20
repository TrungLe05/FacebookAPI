package com.dev.backendapi.application.service;

import com.dev.backendapi.application.usecase.HideCommentUseCase;
import com.dev.backendapi.application.usecase.ReplyToCommentUseCase;
import com.dev.backendapi.application.usecase.SendMessageUseCase;
import com.dev.backendapi.domain.model.ReplyCommand;
import com.dev.backendapi.domain.model.SendFailedEvent;
import com.dev.backendapi.infrastructure.exception.FacebookApiException;
import com.dev.backendapi.infrastructure.exception.FacebookNonRetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacebookSenderService {

    private final HideCommentUseCase hideCommentUseCase;
    private final ReplyToCommentUseCase replyToCommentUseCase;
    private final SendMessageUseCase sendMessageUseCase;
    private final IdempotencyService idempotencyService;
    private final KafkaTemplate<String, SendFailedEvent> kafkaTemplate;

    private static final String SEND_FAILED_TOPIC = "send_failed";
    private static final String DEAD_LETTER_TOPIC  = "dead_letter";

    public void execute(ReplyCommand command, int retryCount) {
        String commandId = command.getCommandId();
        log.info("[FacebookSenderService] Received command={} action={} retryCount={}",
                commandId, command.getAction(), retryCount);
        // ── Idempotency check ─────────────────────────────────────────────
        if (idempotencyService.alreadyProcessed(commandId)) {
            log.info("[FacebookSenderService] command={} already processed, skip", commandId);
            return;
        }

        try {
            dispatch(command);
            idempotencyService.markProcessed(commandId);
            log.info("[FacebookSenderService] command={} action={} done",
                    commandId, command.getAction());

        } catch (FacebookNonRetryableException e) {
            // Token hết hạn → thẳng vào dead_letter, không retry
            log.error("[FacebookSenderService] Non-retryable error command={}: {}", commandId, e.getMessage());
            publishDeadLetter(command, retryCount, e.getMessage());

        } catch (FacebookApiException e) {
            // Lỗi tạm thời → publish send_failed để retry-service xử lý
            log.error("[FacebookSenderService] command={} retry={} error={}",
                    commandId, retryCount, e.getMessage());
            publishSendFailed(command, retryCount, e.getMessage());
        } catch (Exception e) {
            // Lỗi tạm thời → publish send_failed để retry-service xử lý
            log.error("[FacebookSenderService] command={} retry={} error={}",
                    commandId, retryCount, e.getMessage());
            publishSendFailed(command, retryCount, e.getMessage());
        }
    }

    private void dispatch(ReplyCommand command) {
        switch (command.getAction()) {
            case "reply" -> {
                if (isComment(command)) {
                    replyToCommentUseCase.execute(command.getCommentId(), command.getReplyText());
                } else {
                    sendMessageUseCase.execute(command.getSenderId(), command.getReplyText());
                }
            }
            case "hide", "hide_and_queue", "blacklist_and_hide" -> {
                if (isComment(command)) {
                    hideCommentUseCase.execute(command.getCommentId());
                }
            }
            case "queue_review", "ignore" ->
                    log.info("[FacebookSenderService] No FB API call for action={}", command.getAction());
            default ->
                    log.warn("[FacebookSenderService] Unknown action={}", command.getAction());
        }
    }

    private void publishSendFailed(ReplyCommand command, int retryCount, String error) {
        SendFailedEvent event = SendFailedEvent.builder()
                .schemaVersion(1)
                .commandId(command.getCommandId())
                .eventId(command.getEventId())
                .retryCount(retryCount)
                .lastError(error)
                .nextRetryAt(Instant.now())
                .command(command)
                .failedAt(Instant.now())
                .build();
        kafkaTemplate.send(SEND_FAILED_TOPIC, command.getCommandId(), event);
    }

    private void publishDeadLetter(ReplyCommand command, int retryCount, String error) {
        SendFailedEvent event = SendFailedEvent.builder()
                .schemaVersion(1)
                .commandId(command.getCommandId())
                .eventId(command.getEventId())
                .retryCount(retryCount)
                .lastError(error)
                .failedAt(Instant.now())
                .command(command)
                .build();
        kafkaTemplate.send(DEAD_LETTER_TOPIC, command.getCommandId(), event);
    }

    private boolean isComment(ReplyCommand command) {
        return "comment".equals(command.getEventType())
                && command.getCommentId() != null
                && !command.getCommentId().isBlank();
    }
}
