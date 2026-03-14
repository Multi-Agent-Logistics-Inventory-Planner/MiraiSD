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
public class GachaponRequestDTO {
    @NotBlank(message = "Gachapon code is required")
    @Pattern(regexp = "^G\\d+$", message = "Gachapon code must follow format G1, G2, etc.")
    private String gachaponCode;
}
