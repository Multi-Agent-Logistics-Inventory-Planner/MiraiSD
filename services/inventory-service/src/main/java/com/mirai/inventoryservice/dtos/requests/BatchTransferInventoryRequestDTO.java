package com.mirai.inventoryservice.dtos.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchTransferInventoryRequestDTO {

    @NotNull(message = "Transfers list is required")
    @Size(min = 1, message = "At least one transfer is required")
    private List<@Valid TransferInventoryRequestDTO> transfers;
}
