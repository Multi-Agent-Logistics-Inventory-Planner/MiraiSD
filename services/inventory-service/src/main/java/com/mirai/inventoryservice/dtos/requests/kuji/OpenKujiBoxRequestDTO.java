package com.mirai.inventoryservice.dtos.requests.kuji;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class OpenKujiBoxRequestDTO {
    @NotNull
    private UUID productId;

    @NotNull
    private UUID locationId;

    /** Optional link to a machine_display row when the kuji is on display. */
    private UUID machineDisplayId;

    @Size(max = 120)
    private String label;

    private String notes;

    @NotEmpty
    @Valid
    private List<NewKujiBoxTierDTO> tiers;

    /** Required for actor attribution on the audit log. */
    @NotNull
    private UUID actorId;
}
