package com.dev.coreservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Quản lý danh sách sender bị blacklist, lưu trong Redis (không TTL – vĩnh viễn).
 * Key: blacklist:{senderId}  →  "1"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PREFIX = "blacklist:";

    public boolean isBlacklisted(String senderId) {
        if (senderId == null || senderId.isBlank()) return false;
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + senderId));
    }

    public void addToBlacklist(String senderId) {
        if (senderId == null || senderId.isBlank()) return;
        redisTemplate.opsForValue().set(PREFIX + senderId, "1");
        log.warn("[BlacklistService] Added senderId {} to blacklist", senderId);
    }

    public void removeFromBlacklist(String senderId) {
        redisTemplate.delete(PREFIX + senderId);
        log.info("[BlacklistService] Removed senderId {} from blacklist", senderId);
    }
}
