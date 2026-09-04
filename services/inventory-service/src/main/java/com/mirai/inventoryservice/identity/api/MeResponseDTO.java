package com.mirai.inventoryservice.identity.api;

import com.mirai.inventoryservice.identity.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Response shape for {@code GET /api/v1/me}, per docs/specs/authentication-and-authorization.md section 7. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeResponseDTO {
    private UUID id;
    private String fullName;
    private String email;
    private UserRole role;
    private boolean systemAdmin;
}
