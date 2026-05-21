package com.mirai.inventoryservice.models.lootbox;

import com.mirai.inventoryservice.models.audit.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Singleton row (id = 1) carrying admin-editable coin-economy settings.
 *
 * Today there's one knob — review_coin_rate — but the table is shaped to grow
 * (additional rate fields, feature flags) without further migrations. A CHECK
 * constraint pins the row to id = 1 so accidental inserts can't fork the config.
 */
@Entity
@Table(name = "coin_economy_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinEconomyConfig {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @NotNull
    @PositiveOrZero
    @Column(name = "review_coin_rate", nullable = false)
    private Integer reviewCoinRate;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    @Column(name = "updated_by_user_id", insertable = false, updatable = false)
    private UUID updatedByUserId;
}
