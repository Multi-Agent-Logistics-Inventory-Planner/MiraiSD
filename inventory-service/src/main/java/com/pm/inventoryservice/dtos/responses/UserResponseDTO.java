package com.pm.inventoryservice.dtos.responses;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponseDTO {
    private String id;
    private String role;
    private String fullName;
    private String email;
}
