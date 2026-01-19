package com.mirai.inventoryservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SingleClawMachineResponseDTO {
    private UUID id;
    private String singleClawMachineCode;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

