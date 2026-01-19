package com.mirai.inventoryservice.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetRequestDTO {
    @NotBlank(message = "Cabinet code is required")
    @Pattern(regexp = "^C\\d+$", message = "Cabinet code must follow format C1, C2, etc.")
    private String cabinetCode;
}

