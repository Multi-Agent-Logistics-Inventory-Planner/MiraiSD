package com.mirai.inventoryservice.dtos.responses;

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
public class CategoryResponseDTO {
    private UUID id;
    private UUID parentId;
    private String name;
    private String slug;
    private Integer displayOrder;
    private Boolean isActive;
    private List<CategoryResponseDTO> children;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
