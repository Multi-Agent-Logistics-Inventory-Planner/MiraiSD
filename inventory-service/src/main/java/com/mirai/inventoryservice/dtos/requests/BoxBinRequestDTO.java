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
public class BoxBinRequestDTO {
    @NotBlank(message = "BoxBin code is required")
    @Pattern(regexp = "^B\\d+$", message = "BoxBin code must follow format B1, B2, etc.")
    private String boxBinCode;
}

