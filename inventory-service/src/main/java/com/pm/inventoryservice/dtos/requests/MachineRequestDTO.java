package com.pm.inventoryservice.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MachineRequestDTO {

    @NotBlank(message = "Machine Code is required")
    private String machineCode;
}
