package com.dev.coreservice.pipeline;

import com.dev.coreservice.model.ClassificationResult;
import com.dev.coreservice.model.Decision;
import com.dev.coreservice.model.NormalizedEvent;
import com.dev.coreservice.model.SpamResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bước 3: Ra quyết định dựa trên kết quả SpamDetector + AiClassifier.
 *
 * Ma trận quyết định:
 * ┌─────────────────┬────────────────┬──────────┬───────────────────────────┐
 * │ Spam            │ Intent         │ Sentiment│ Decision                  │
 * ├─────────────────┼────────────────┼──────────┼───────────────────────────┤
 * │ BLACKLISTED     │ any            │ any      │ HIDE_IMMEDIATELY           │
 * │ HARD_SPAM       │ any            │ any      │ HIDE_IMMEDIATELY           │
 * │ HARD + repeat   │ any            │ any      │ BLACKLIST_AND_HIDE         │
 * │ SOFT_SPAM       │ any            │ any      │ HIDE_AND_QUEUE_REVIEW      │
 * │ clean           │ ask_price      │ pos/neu  │ AUTO_REPLY                 │
 * │ clean           │ complaint      │ negative │ QUEUE_FOR_MANUAL_REPLY     │
 * │ clean           │ compliment     │ positive │ AUTO_REPLY_THANK_YOU       │
 * │ clean           │ spam (AI)      │ any      │ HIDE_IMMEDIATELY           │
 * │ clean           │ other          │ any      │ IGNORE                     │
 * └─────────────────┴────────────────┴──────────┴───────────────────────────┘
 */
@Slf4j
@Component
public class DecisionEngine {

    public Decision decide(NormalizedEvent event,
                           SpamResult spam,
                           ClassificationResult classification) {
        Decision decision;

        if (spam.isBlacklisted()) {
            decision = Decision.HIDE_IMMEDIATELY;

        } else if (spam.isHardSpam() && spam.isRepeatOffender()) {
            decision = Decision.BLACKLIST_AND_HIDE;

        } else if (spam.isHardSpam() && spam.isMalicious()) {
            // Link độc hại / scam → ẩn + queue review cho admin
            decision = Decision.HIDE_AND_QUEUE_REVIEW;

        } else if (spam.isHardSpam()) {
            // Spam nhẹ có link thường → ẩn ngay
            decision = Decision.HIDE_IMMEDIATELY;

        } else if (spam.isSoftSpam()) {
            decision = Decision.HIDE_AND_QUEUE_REVIEW;

        } else {
            decision = decideByAi(classification);
        }

        log.info("[DecisionEngine] event={} → spam={}/{} intent={} sentiment={} → decision={}",
                event.getEventId(),
                spam.isHardSpam() ? "HARD" : spam.isSoftSpam() ? "SOFT" : "NONE",
                spam.isRepeatOffender() ? "+REPEAT" : "",
                classification.getIntent(),
                classification.getSentiment(),
                decision);

        return decision;
    }

    private Decision decideByAi(ClassificationResult c) {
        // Confidence quá thấp (AI fail hoàn toàn, không có keyword nào khớp) → bỏ qua
        if (c.getConfidence() < 0.3) {
            log.info("[DecisionEngine] Confidence={} too low → IGNORE", c.getConfidence());
            return Decision.IGNORE;
        }

        // Nếu là keyword fallback (confidence=0.6) vẫn cho qua để AUTO_REPLY
        if (c.isFallback()) {
            log.info("[DecisionEngine] Keyword fallback with confidence={} → proceeding with intent={}",
                    c.getConfidence(), c.getIntent());
        }

        return switch (c.getIntent()) {
            case "ask_price" -> Decision.AUTO_REPLY;
            case "complaint" -> Decision.QUEUE_FOR_MANUAL_REPLY;
            case "compliment" -> Decision.AUTO_REPLY_THANK_YOU;
            case "spam" -> Decision.HIDE_IMMEDIATELY;
            case "ask_info" -> Decision.AUTO_REPLY;
            default -> Decision.IGNORE;
        };
    }
}
