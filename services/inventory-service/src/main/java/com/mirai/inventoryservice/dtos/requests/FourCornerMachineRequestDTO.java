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
public class FourCornerMachineRequestDTO {
    @NotBlank(message = "FourCornerMachine code is required")
    @Pattern(regexp = "^M\\d+$", message = "FourCornerMachine code must follow format M1, M2, etc.")
    private String fourCornerMachineCode;
}
