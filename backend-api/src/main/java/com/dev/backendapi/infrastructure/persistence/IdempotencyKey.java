package com.dev.backendapi.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    @Column(name = "command_id", length = 100)
    private String commandId;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}