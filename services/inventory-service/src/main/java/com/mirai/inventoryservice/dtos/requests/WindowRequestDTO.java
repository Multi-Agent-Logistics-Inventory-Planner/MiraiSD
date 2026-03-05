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
public class WindowRequestDTO {
    @NotBlank(message = "Window code is required")
    @Pattern(regexp = "^W\\d+$", message = "Window code must follow format W1, W2, etc.")
    private String windowCode;
}

