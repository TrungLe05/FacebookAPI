package com.dev.coreservice.config;

import com.dev.coreservice.service.BlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin REST API để quản lý blacklist và kiểm tra trạng thái hệ thống.
 * Chỉ dùng trong môi trường dev/test.
 */
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final BlacklistService blacklistService;
    private final RedisTemplate<String, Object> redisTemplate;

    // ── Blacklist Management ───────────────────────────────────────────────────

    /** Xem danh sách tất cả sender bị blacklist */
    @GetMapping("/blacklist")
    public ResponseEntity<Map<String, Object>> getBlacklist() {
        Set<String> keys = redisTemplate.keys("blacklist:*");
        List<String> senderIds = keys == null ? List.of() :
                keys.stream()
                    .map(k -> k.replace("blacklist:", ""))
                    .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
                "count", senderIds.size(),
                "blacklisted", senderIds
        ));
    }

    /** Kiểm tra một sender có bị blacklist không */
    @GetMapping("/blacklist/{senderId}")
    public ResponseEntity<Map<String, Object>> checkBlacklist(@PathVariable String senderId) {
        boolean blacklisted = blacklistService.isBlacklisted(senderId);
        return ResponseEntity.ok(Map.of(
                "senderId", senderId,
                "blacklisted", blacklisted
        ));
    }

    /** Xóa sender khỏi blacklist */
    @DeleteMapping("/blacklist/{senderId}")
    public ResponseEntity<Map<String, Object>> removeFromBlacklist(@PathVariable String senderId) {
        blacklistService.removeFromBlacklist(senderId);
        log.info("[AdminController] Removed {} from blacklist", senderId);
        return ResponseEntity.ok(Map.of(
                "senderId", senderId,
                "message", "Removed from blacklist successfully"
        ));
    }

    /** Thêm sender vào blacklist */
    @PostMapping("/blacklist/{senderId}")
    public ResponseEntity<Map<String, Object>> addToBlacklist(@PathVariable String senderId) {
        blacklistService.addToBlacklist(senderId);
        log.info("[AdminController] Added {} to blacklist", senderId);
        return ResponseEntity.ok(Map.of(
                "senderId", senderId,
                "message", "Added to blacklist successfully"
        ));
    }

    // ── Spam Repeat Tracking ───────────────────────────────────────────────────

    /** Xóa spam repeat tracking của 1 sender (để reset counter) */
    @DeleteMapping("/spam-tracking/{senderId}")
    public ResponseEntity<Map<String, Object>> clearSpamTracking(@PathVariable String senderId) {
        Set<String> keys = redisTemplate.keys("spam:repeat:" + senderId + ":*");
        int count = 0;
        if (keys != null) {
            for (String key : keys) {
                redisTemplate.delete(key);
                count++;
            }
        }
        log.info("[AdminController] Cleared {} spam tracking keys for sender {}", count, senderId);
        return ResponseEntity.ok(Map.of(
                "senderId", senderId,
                "keysDeleted", count,
                "message", "Spam tracking cleared"
        ));
    }

    /** Reset toàn bộ: xóa blacklist + spam tracking của 1 sender */
    @DeleteMapping("/reset/{senderId}")
    public ResponseEntity<Map<String, Object>> resetSender(@PathVariable String senderId) {
        // Xóa blacklist
        blacklistService.removeFromBlacklist(senderId);

        // Xóa spam tracking
        Set<String> spamKeys = redisTemplate.keys("spam:repeat:" + senderId + ":*");
        int spamCount = 0;
        if (spamKeys != null) {
            for (String key : spamKeys) {
                redisTemplate.delete(key);
                spamCount++;
            }
        }

        log.info("[AdminController] Full reset for sender {}: blacklist removed, {} spam keys deleted",
                senderId, spamCount);
        return ResponseEntity.ok(Map.of(
                "senderId", senderId,
                "blacklistRemoved", true,
                "spamKeysDeleted", spamCount,
                "message", "Sender fully reset"
        ));
    }

    // ── Health Check ───────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "core-service"));
    }
}
