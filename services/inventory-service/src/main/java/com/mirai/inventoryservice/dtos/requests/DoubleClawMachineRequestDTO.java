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
public class DoubleClawMachineRequestDTO {
    @NotBlank(message = "DoubleClawMachine code is required")
    @Pattern(regexp = "^D\\d+$", message = "DoubleClawMachine code must follow format D1, D2, etc.")
    private String doubleClawMachineCode;
}

