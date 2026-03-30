package com.mirai.inventoryservice.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageLocationRequestDTO {
    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    private String codePrefix;

    private String icon;

    @NotNull
    @Builder.Default
    private Boolean hasDisplay = false;

    @NotNull
    @Builder.Default
    private Boolean isDisplayOnly = false;

    @NotNull
    @Builder.Default
    private Integer displayOrder = 0;
}
