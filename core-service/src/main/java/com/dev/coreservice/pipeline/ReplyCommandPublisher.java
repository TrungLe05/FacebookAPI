package com.dev.coreservice.pipeline;

import com.dev.coreservice.model.ClassificationResult;
import com.dev.coreservice.model.Decision;
import com.dev.coreservice.model.NormalizedEvent;
import com.dev.coreservice.model.ReplyCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class ReplyCommandPublisher {
    private final KafkaTemplate<String, ReplyCommand> replyCommandKafkaTemplate;

    private static final String REPLY_COMMANDS_TOPIC = "reply_commands";

    // Map Decision → action string
    private static final String REPLY_ASK_PRICE =
            "Cảm ơn bạn đã quan tâm! Vui lòng inbox để được tư vấn giá chi tiết nhé 😊";
    private static final String REPLY_THANK_YOU =
            "Cảm ơn bạn rất nhiều! Sự ủng hộ của bạn là động lực lớn nhất 🙏❤️";

    public void publish(NormalizedEvent event, Decision decision, ClassificationResult classification) {
        String action = mapDecisionToAction(decision);
        String replyText = resolveReplyText(decision);

        ReplyCommand command = ReplyCommand.builder()
                .schemaVersion(1)
                .commandId(UUID.randomUUID().toString())
                .eventId(event.getEventId())
                .action(action)
                .pageId(event.getPageId())
                .commentId(event.getCommentId())
                .senderId(event.getSenderId())
                .eventType(event.getEventType())
                .replyText(replyText)
                .intent(classification.getIntent())
                .sentiment(classification.getSentiment())
                .createdAt(Instant.now())
                .build();

        replyCommandKafkaTemplate.send(REPLY_COMMANDS_TOPIC, command.getCommandId(), command)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[ReplyCommandPublisher] Failed to publish command for event {}: {}",
                                event.getEventId(), ex.getMessage());
                    } else {
                        log.info("[ReplyCommandPublisher] Published command={} action={} for event={}",
                                command.getCommandId(), action, event.getEventId());
                    }
                });
    }

    private String mapDecisionToAction(Decision decision) {
        return switch (decision) {
            case HIDE_IMMEDIATELY -> "hide";
            case HIDE_AND_QUEUE_REVIEW -> "hide_and_queue";
            case AUTO_REPLY -> "reply";
            case AUTO_REPLY_THANK_YOU -> "reply";
            case QUEUE_FOR_MANUAL_REPLY -> "queue_review";
            case BLACKLIST_AND_HIDE -> "blacklist_and_hide";
            case IGNORE -> "ignore";
        };
    }

    private String resolveReplyText(Decision decision) {
        return switch (decision) {
            case AUTO_REPLY -> REPLY_ASK_PRICE;
            case AUTO_REPLY_THANK_YOU -> REPLY_THANK_YOU;
            default -> null;
        };
    }
}
