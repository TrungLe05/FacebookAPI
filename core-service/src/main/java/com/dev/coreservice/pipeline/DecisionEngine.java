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

        } else if (spam.isHardSpam()) {
            decision = Decision.HIDE_AND_QUEUE_REVIEW;

        } else if (spam.isSoftSpam()) {
            decision = Decision.HIDE_IMMEDIATELY;

        } else {
            // Không phải spam → quyết định dựa trên AI
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
        // Confidence quá thấp → bỏ qua
        if (c.isFallback() || c.getConfidence() < 0.3) {
            return Decision.IGNORE;
        }

        return switch (c.getIntent()) {
            case "ask_price" -> Decision.AUTO_REPLY;
            case "complaint" -> Decision.QUEUE_FOR_MANUAL_REPLY;
            case "compliment" -> Decision.AUTO_REPLY_THANK_YOU;
            case "spam" -> Decision.HIDE_IMMEDIATELY;
            default -> Decision.IGNORE;
        };
    }
}
