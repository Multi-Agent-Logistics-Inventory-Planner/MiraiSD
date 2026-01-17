package com.mirai.inventoryservice.models.shipment;

import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    @NotNull
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    @NotNull
    private Product item;

    @NotNull
    @Min(1)
    @Column(name = "ordered_quantity", nullable = false)
    private Integer orderedQuantity;

    @NotNull
    @Min(0)
    @Column(name = "received_quantity", nullable = false)
    @Builder.Default
    private Integer receivedQuantity = 0;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_location_type")
    private LocationType destinationLocationType;

    @Column(name = "destination_location_id")
    private UUID destinationLocationId;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
