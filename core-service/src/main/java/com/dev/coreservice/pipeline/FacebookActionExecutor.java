package com.dev.coreservice.pipeline;

import com.dev.coreservice.client.FacebookClient;
import com.dev.coreservice.model.Decision;
import com.dev.coreservice.model.NormalizedEvent;
import com.dev.coreservice.service.BlacklistService;
import com.dev.coreservice.service.EventStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Bước 4: Thực thi action lên Facebook Graph API dựa trên Decision.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FacebookActionExecutor {

    private final FacebookClient facebookClient;
    private final BlacklistService blacklistService;
    private final EventStatusService eventStatusService;
    private final KafkaTemplate<String, NormalizedEvent> kafkaTemplate;

    private static final String REVIEW_QUEUE_TOPIC = "review_queue";

    // Template auto-reply
    private static final String REPLY_ASK_PRICE =
            "Cảm ơn bạn đã quan tâm! Vui lòng inbox để được tư vấn giá chi tiết và ưu đãi tốt nhất nhé 😊";
    private static final String REPLY_THANK_YOU =
            "Cảm ơn bạn rất nhiều! Sự ủng hộ của bạn là động lực lớn nhất của chúng mình 🙏❤️";

    public void execute(NormalizedEvent event, Decision decision) {
        log.info("[FacebookActionExecutor] Executing {} for event {}", decision, event.getEventId());

        switch (decision) {
            case HIDE_IMMEDIATELY -> handleHideImmediately(event);
            case HIDE_AND_QUEUE_REVIEW -> handleHideAndQueue(event);
            case AUTO_REPLY -> handleAutoReply(event, REPLY_ASK_PRICE);
            case AUTO_REPLY_THANK_YOU -> handleAutoReply(event, REPLY_THANK_YOU);
            case QUEUE_FOR_MANUAL_REPLY -> handleQueueForManualReply(event);
            case BLACKLIST_AND_HIDE -> handleBlacklistAndHide(event);
            case IGNORE -> log.info("[FacebookActionExecutor] Ignoring event {}", event.getEventId());
        }
    }

    private void handleHideImmediately(NormalizedEvent event) {
        if (isComment(event)) {
            facebookClient.hideComment(event.getCommentId());
            eventStatusService.markHidden(event.getEventId(), "SPAM_HIDDEN");
        } else {
            // DM không thể hide, chỉ log
            log.info("[FacebookActionExecutor] Message event hidden (no API action): {}", event.getEventId());
            eventStatusService.markHidden(event.getEventId(), "MESSAGE_IGNORED");
        }
    }

    private void handleHideAndQueue(NormalizedEvent event) {
        if (isComment(event)) {
            facebookClient.hideComment(event.getCommentId());
        }
        // Publish sang review queue để admin xử lý thủ công
        kafkaTemplate.send(REVIEW_QUEUE_TOPIC, event.getEventId(), event);
        eventStatusService.markHidden(event.getEventId(), "SOFT_SPAM_QUEUED");
        log.info("[FacebookActionExecutor] Event queued for review: {}", event.getEventId());
    }

    private void handleAutoReply(NormalizedEvent event, String replyText) {
        if (isComment(event)) {
            facebookClient.replyToComment(event.getCommentId(), replyText);
        } else {
            // DM → reply qua Messenger API
            facebookClient.sendMessage(event.getSenderId(), replyText);
        }
        eventStatusService.markReplied(event.getEventId(), "AUTO_REPLIED");
    }

    private void handleQueueForManualReply(NormalizedEvent event) {
        kafkaTemplate.send(REVIEW_QUEUE_TOPIC, event.getEventId(), event);
        eventStatusService.markProcessed(event.getEventId(), "QUEUED_MANUAL_REPLY");
        log.info("[FacebookActionExecutor] Complaint queued for manual reply: {}", event.getEventId());
    }

    private void handleBlacklistAndHide(NormalizedEvent event) {
        blacklistService.addToBlacklist(event.getSenderId());
        if (isComment(event)) {
            facebookClient.hideComment(event.getCommentId());
        }
        eventStatusService.markHidden(event.getEventId(), "BLACKLISTED_AND_HIDDEN");
        log.warn("[FacebookActionExecutor] Sender {} blacklisted", event.getSenderId());
    }

    private boolean isComment(NormalizedEvent event) {
        return "comment".equals(event.getEventType())
                && event.getCommentId() != null
                && !event.getCommentId().isBlank();
    }
}
