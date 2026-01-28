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
public class KeychainMachineRequestDTO {
    @NotBlank(message = "KeychainMachine code is required")
    @Pattern(regexp = "^K\\d+$", message = "KeychainMachine code must follow format K1, K2, etc.")
    private String keychainMachineCode;
}

