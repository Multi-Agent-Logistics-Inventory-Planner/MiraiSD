package com.mirai.inventoryservice.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One durable record per completed idempotent command (.specs/phase-6-inventory Q-6c-3, T-6c-10).
 * Uniqueness on (site_id, user_id, idempotency_key) is enforced at the database level
 * (V65's idx_command_idempotency_site_user_key), not just here - {@link CommandIdempotencyService}
 * relies on that constraint, not merely a prior read, to guarantee at most one committed effect
 * per key under concurrent replay.
 */
@Entity
@Table(name = "command_idempotency", uniqueConstraints =
        @UniqueConstraint(columnNames = {"site_id", "user_id", "idempotency_key"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotNull
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @NotNull
    @Column(name = "command_type", nullable = false)
    private String commandType;

    @NotNull
    @Column(name = "request_fingerprint", nullable = false)
    private String requestFingerprint;

    @NotNull
    @Column(name = "result_status", nullable = false)
    private Integer resultStatus;

    @Column(name = "result_body", columnDefinition = "TEXT")
    private String resultBody;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
