package com.pm.inventoryservice.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BinRequestDTO {

    @NotBlank(message = "Bin code is required")
    @Size(max = 10, message = "Bin code cannot exceed 10 characters")
    @Pattern(regexp = "^S\\d+$", message = "Bin code must follow the pattern S1, S2, etc.")
    private String binCode;
}


