package com.dev.coreservice.pipeline;

import com.dev.coreservice.config.AppProperties;
import com.dev.coreservice.model.NormalizedEvent;
import com.dev.coreservice.model.SpamResult;
import com.dev.coreservice.service.BlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Bước 1 trong pipeline: phát hiện spam bằng rule-based (không cần AI).
 * Nhanh, chạy trước AI classifier để tiết kiệm API call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpamDetector {

    private final BlacklistService blacklistService;
    private final AppProperties props;
    private final RedisTemplate<String, Object> redisTemplate;

    // Key prefix cho repeat tracking
    private static final String REPEAT_KEY_PREFIX = "spam:repeat:";

    public SpamResult check(NormalizedEvent event) {
        String senderId = event.getSenderId();
        String content = event.getContent();

        if (content == null || content.isBlank()) {
            return SpamResult.builder().spam(false).build();
        }

        // 1. Kiểm tra blacklist
        if (blacklistService.isBlacklisted(senderId)) {
            log.info("[SpamDetector] Sender {} is blacklisted", senderId);
            return SpamResult.builder()
                    .spam(true)
                    .hardSpam(true)
                    .blacklisted(true)
                    .reason("Sender in blacklist")
                    .build();
        }

        // 2. Kiểm tra URL / link rút gọn (hard spam)
        Pattern urlPattern = Pattern.compile(props.getSpam().getUrlPattern(),
                Pattern.CASE_INSENSITIVE);
        if (urlPattern.matcher(content).find()) {
            log.info("[SpamDetector] Hard spam - URL detected from sender {}", senderId);
            return SpamResult.builder()
                    .spam(true)
                    .hardSpam(true)
                    .reason("Contains URL or short link")
                    .build();
        }

        // 3. Kiểm tra nội dung lặp lại (soft → hard nếu vượt threshold)
        String contentHash = hashContent(content);
        String repeatKey = REPEAT_KEY_PREFIX + senderId + ":" + contentHash;
        Long repeatCount = redisTemplate.opsForValue().increment(repeatKey);
        if (repeatCount == 1) {
            // Set TTL chỉ lần đầu tiên
            redisTemplate.expire(repeatKey,
                    props.getSpam().getRepeatWindowHours(), TimeUnit.HOURS);
        }

        int threshold = props.getSpam().getRepeatThreshold();
        if (repeatCount != null && repeatCount >= threshold) {
            log.info("[SpamDetector] Repeat spam {} times from sender {}", repeatCount, senderId);
            return SpamResult.builder()
                    .spam(true)
                    .hardSpam(true)
                    .repeatOffender(true)
                    .reason("Repeated content " + repeatCount + " times in "
                            + props.getSpam().getRepeatWindowHours() + "h")
                    .build();
        }
        if (repeatCount != null && repeatCount == 2) {
            // Lần 2: soft spam, warning
            return SpamResult.builder()
                    .spam(true)
                    .softSpam(true)
                    .reason("Duplicate content detected (" + repeatCount + "/3)")
                    .build();
        }

        return SpamResult.builder().spam(false).reason("Clean").build();
    }

    private String hashContent(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.trim().toLowerCase()
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }
}
