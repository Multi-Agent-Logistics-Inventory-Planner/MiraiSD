package com.mirai.inventoryservice.models.shipment;

import com.mirai.inventoryservice.models.enums.LocationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_item_allocations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentItemAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_item_id", nullable = false)
    @NotNull
    private ShipmentItem shipmentItem;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false)
    private LocationType locationType;

    @Column(name = "location_id")
    private UUID locationId;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer quantity;

    @CreationTimestamp
    @Column(name = "received_at", updatable = false)
    private OffsetDateTime receivedAt;
}
