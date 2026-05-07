package com.mirai.inventoryservice.dtos.requests.kuji;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloseKujiBoxRequestDTO {
    @NotNull
    private UUID actorId;

    /**
     * For each tier whose linked product still has inventory at the box's location, the
     * destination location to transfer remaining product back to. Free-text tiers and zero-inventory
     * tiers don't need an entry. The service rejects close if a linked tier has remaining inventory
     * but no destination is provided.
     */
    private List<TierTransferDestination> transferOutTargets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierTransferDestination {
        @NotNull
        private UUID tierId;
        @NotNull
        private UUID destinationLocationId;
    }
}
