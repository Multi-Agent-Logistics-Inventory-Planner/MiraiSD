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
@Table(name = "lootbox_plays")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LootboxPlay {
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
    @Builder.Default
    private Integer cost = 1;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prize_id", nullable = false)
    @ToString.Exclude
    private LootboxPrize prize;

    @Column(name = "prize_id", insertable = false, updatable = false)
    private UUID prizeId;

    @NotBlank
    @Column(name = "prize_name_snapshot", nullable = false)
    private String prizeNameSnapshot;

    @Column(name = "prize_description_snapshot")
    private String prizeDescriptionSnapshot;

    @Column(name = "prize_image_url_snapshot")
    private String prizeImageUrlSnapshot;

    @NotBlank
    @Column(name = "prize_tier_name_snapshot", nullable = false)
    private String prizeTierNameSnapshot;

    @NotBlank
    @Column(nullable = false)
    @Builder.Default
    private String status = "WON";

    @Column(name = "redeemed_at")
    private OffsetDateTime redeemedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redeemed_by_user_id")
    @ToString.Exclude
    private User redeemedBy;

    @Column(name = "redeemed_by_user_id", insertable = false, updatable = false)
    private UUID redeemedByUserId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "played_at", updatable = false)
    private OffsetDateTime playedAt;
}
