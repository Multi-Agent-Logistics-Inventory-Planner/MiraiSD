package com.mirai.inventoryservice.models.lootbox;

import com.mirai.inventoryservice.models.audit.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "coin_adjustments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @NotNull
    @Column(nullable = false)
    private Integer delta;

    @NotBlank
    @Column(nullable = false)
    private String reason;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id", nullable = false)
    @ToString.Exclude
    private User grantedBy;

    @Column(name = "granted_by_user_id", insertable = false, updatable = false)
    private UUID grantedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    /** Generated column: created_at + 90 days. Read-only — DO NOT set via builder. */
    @Column(name = "expires_at", insertable = false, updatable = false)
    private OffsetDateTime expiresAt;
}
