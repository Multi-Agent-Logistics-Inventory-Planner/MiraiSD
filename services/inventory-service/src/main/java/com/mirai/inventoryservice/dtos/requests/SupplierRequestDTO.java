package com.mirai.inventoryservice.dtos.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierRequestDTO {
    @NotBlank(message = "Supplier name is required")
    @Size(max = 255, message = "Supplier name must be at most 255 characters")
    private String displayName;

    @Email(message = "Contact email must be a valid email address")
    @Size(max = 255, message = "Contact email must be at most 255 characters")
    private String contactEmail;

    private Boolean isActive;
}
