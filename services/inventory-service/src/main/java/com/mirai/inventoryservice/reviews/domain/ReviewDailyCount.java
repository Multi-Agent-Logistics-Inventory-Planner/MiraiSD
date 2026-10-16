package com.mirai.inventoryservice.reviews.domain;

import com.mirai.inventoryservice.identity.domain.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "review_daily_counts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDailyCount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @NotNull
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "review_count")
    @Builder.Default
    private Integer reviewCount = 0;

    /**
     * Coins granted by this row's reviews. Captured at write time using the rate active
     * at the start of the batch — so a later rate change can't retroactively re-price
     * past activity. The balance formula sums this, not review_count.
     */
    @Column(name = "coins_awarded")
    @Builder.Default
    private Integer coinsAwarded = 0;

    /**
     * Site the review source that produced this row's reviews is mapped to
     * (.specs/phase-7-kuji-lootbox T-2). The only production writer is the Python
     * messaging-service (supabase_repo.py::increment_daily_count), which resolves this from a
     * server-side REVIEW_SITE_CODE config value, never from any payload. Uniqueness is
     * (site_id, user_id, date) as of V79, replacing the legacy (user_id, date) constraint.
     */
    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    /** Generated column: date + 90 days. Read-only — DO NOT set via builder. */
    @Column(name = "expires_at", insertable = false, updatable = false)
    private LocalDate expiresAt;
}
