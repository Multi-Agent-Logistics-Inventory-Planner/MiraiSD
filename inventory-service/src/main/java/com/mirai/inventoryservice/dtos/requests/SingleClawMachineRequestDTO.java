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
public class SingleClawMachineRequestDTO {
    @NotBlank(message = "SingleClawMachine code is required")
    @Pattern(regexp = "^S\\d+$", message = "SingleClawMachine code must follow format S1, S2, etc.")
    private String singleClawMachineCode;
}

