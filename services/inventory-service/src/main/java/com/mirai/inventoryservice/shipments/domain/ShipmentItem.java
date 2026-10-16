package com.mirai.inventoryservice.shipments.domain;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.sites.domain.Site;
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
import java.util.ArrayList;
import java.util.List;
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

    @NotNull
    @Min(0)
    @Column(name = "damaged_quantity", nullable = false)
    @Builder.Default
    private Integer damagedQuantity = 0;

    @NotNull
    @Min(0)
    @Column(name = "display_quantity", nullable = false)
    @Builder.Default
    private Integer displayQuantity = 0;

    @NotNull
    @Min(0)
    @Column(name = "shop_quantity", nullable = false)
    @Builder.Default
    private Integer shopQuantity = 0;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_location_type")
    private LocationType destinationLocationType;

    @Column(name = "destination_location_id")
    private UUID destinationLocationId;

    /**
     * The site this item belongs to. Nullable at the JPA level pending V69's separate
     * constraining migration (.specs/phase-7-shipments T-3); set explicitly by
     * {@code ShipmentService} from the destination location's site, or the parent shipment's
     * site when there is no destination location yet.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @Column(columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "shipmentItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    private List<ShipmentItemAllocation> allocations = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
