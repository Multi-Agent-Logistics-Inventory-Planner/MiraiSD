package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.LocationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MachineDisplayDTO {
    private UUID id;
    private LocationType locationType;
    private UUID machineId;
    private String machineCode;
    private UUID productId;
    private String productName;
    private String productSku;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private UUID actorId;
    private String actorName;

    // Computed fields
    private Long daysActive;
    private boolean stale;

    /**
     * Calculate days active from startedAt to endedAt (or now if still active)
     */
    public static MachineDisplayDTO fromEntity(
            com.mirai.inventoryservice.models.MachineDisplay display,
            String machineCode,
            String actorName,
            int staleThresholdDays) {

        OffsetDateTime end = display.getEndedAt() != null
                ? display.getEndedAt()
                : OffsetDateTime.now();

        long days = ChronoUnit.DAYS.between(display.getStartedAt(), end);

        return MachineDisplayDTO.builder()
                .id(display.getId())
                .locationType(display.getLocationType())
                .machineId(display.getMachineId())
                .machineCode(machineCode)
                .productId(display.getProduct().getId())
                .productName(display.getProduct().getName())
                .productSku(display.getProduct().getSku())
                .startedAt(display.getStartedAt())
                .endedAt(display.getEndedAt())
                .actorId(display.getActorId())
                .actorName(actorName)
                .daysActive(days)
                .stale(display.isActive() && days >= staleThresholdDays)
                .build();
    }
}
