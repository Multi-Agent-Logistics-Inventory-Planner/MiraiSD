package com.pm.inventoryservice.dtos.requests;

import com.pm.inventoryservice.models.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRequestDTO {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    private String fullName;

    @NotNull(message = "Role is required")
    private UserRole role;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;
}