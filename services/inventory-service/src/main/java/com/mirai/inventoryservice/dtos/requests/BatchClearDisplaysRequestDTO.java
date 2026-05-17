package com.mirai.inventoryservice.dtos.requests;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for batch-clearing multiple displays on a single machine in one transaction.
 * All displayIds must belong to the same machine; otherwise the request is rejected.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchClearDisplaysRequestDTO {

    @NotEmpty(message = "displayIds must not be empty")
    private List<UUID> displayIds;

    private UUID actorId;
}
