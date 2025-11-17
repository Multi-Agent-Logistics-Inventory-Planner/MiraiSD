package com.pm.inventoryservice.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShelfRequestDTO {

    @NotBlank(message = "Shelf code is required")
    @Size(max = 10, message = "Shelf code cannot exceed 10 characters")
    @Pattern(regexp = "^R\\d+$", message = "Shelf code must follow the pattern R1, R2, etc.")
    private String shelfCode;
}


