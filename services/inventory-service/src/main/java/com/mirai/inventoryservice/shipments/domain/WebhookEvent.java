package com.mirai.inventoryservice.shipments.domain;

import com.mirai.inventoryservice.sites.domain.Site;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @NotBlank
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @NotBlank
    @Column(nullable = false)
    @Builder.Default
    private String source = "easypost";

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload;

    /**
     * The site of the {@code Shipment} this event resolved to, if any. Stays nullable
     * indefinitely (.specs/phase-7-shipments T-3, V69) -- only tracker.created/tracker.updated
     * events resolve a shipment at all; every other EasyPost event type is recorded here for
     * idempotency with no site to derive.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;
}
