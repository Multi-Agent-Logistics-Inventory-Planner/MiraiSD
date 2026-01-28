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
public class PusherMachineRequestDTO {
    @NotBlank(message = "PusherMachine code is required")
    @Pattern(regexp = "^P\\d+$", message = "PusherMachine code must follow format P1, P2, etc.")
    private String pusherMachineCode;
}
