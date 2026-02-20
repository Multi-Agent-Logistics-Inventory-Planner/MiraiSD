package com.mirai.inventoryservice.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewEmployeeRequestDTO {
    @NotBlank(message = "Canonical name is required")
    private String canonicalName;

    @NotNull(message = "Name variants are required")
    private List<String> nameVariants;

    private Boolean isActive;
}
