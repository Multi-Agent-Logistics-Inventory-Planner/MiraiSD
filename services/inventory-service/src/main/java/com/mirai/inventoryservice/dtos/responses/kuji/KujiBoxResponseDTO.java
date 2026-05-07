package com.mirai.inventoryservice.dtos.responses.kuji;

import com.mirai.inventoryservice.models.enums.KujiBoxStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KujiBoxResponseDTO {
    private UUID id;
    private UUID productId;
    private String productName;
    private UUID locationId;
    private String locationCode;
    private String locationName;
    private UUID machineDisplayId;
    private KujiBoxStatus status;
    private String label;
    private String notes;
    private OffsetDateTime openedAt;
    private UUID openedBy;
    private String openedByName;
    private OffsetDateTime closedAt;
    private UUID closedBy;
    private String closedByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<KujiBoxTierResponseDTO> tiers;
    private Integer totalCount;
}
