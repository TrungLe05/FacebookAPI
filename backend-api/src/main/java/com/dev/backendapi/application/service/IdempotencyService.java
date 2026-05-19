package com.dev.backendapi.application.service;

import com.dev.backendapi.infrastructure.persistence.IdempotencyKey;
import com.dev.backendapi.infrastructure.persistence.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;

    /**
     * Kiểm tra commandId đã được xử lý chưa.
     * Dùng existsById thay vì findById để tránh load object không cần thiết.
     */
    public boolean alreadyProcessed(String commandId) {
        boolean exists = repository.existsById(commandId);
        if (exists) {
            log.info("[IdempotencyService] command={} already processed – skipping", commandId);
        }
        return exists;
    }

    /**
     * Lưu commandId sau khi xử lý thành công.
     * Dùng saveAndFlush để đảm bảo ghi ngay vào DB trước khi return.
     */
    public void markProcessed(String commandId) {
        repository.saveAndFlush(
                IdempotencyKey.builder()
                        .commandId(commandId)
                        .status("processed")
                        .processedAt(Instant.now())
                        .build()
        );
        log.info("[IdempotencyService] command={} marked as processed", commandId);
    }
}
