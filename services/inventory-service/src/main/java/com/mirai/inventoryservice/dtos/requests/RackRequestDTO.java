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
public class RackRequestDTO {
    @NotBlank(message = "Rack code is required")
    @Pattern(regexp = "^R\\d+$", message = "Rack code must follow format R1, R2, etc.")
    private String rackCode;
}

