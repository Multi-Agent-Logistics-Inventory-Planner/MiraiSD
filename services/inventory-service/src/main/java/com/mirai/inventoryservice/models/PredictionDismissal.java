package com.mirai.inventoryservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Org-wide dismissal of a forecast prediction. One row per item -- when any
 * user dismisses, it's hidden for everyone. Auto-expires after 30 days at
 * read time (no cleanup cron).
 */
@Entity
@Table(name = "prediction_dismissals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionDismissal {

    @Id
    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "dismissed_at", nullable = false)
    private OffsetDateTime dismissedAt;

    @Column(name = "dismissed_by", nullable = false)
    private UUID dismissedBy;

    @Column(name = "computed_at")
    private OffsetDateTime computedAt;

    @Column(name = "reason")
    private String reason;
}
