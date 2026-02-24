package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private UUID id;
    private String fullName;
    private String email;
    private UserRole role;
    private List<String> nameVariants;
    private Boolean isReviewTracked;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

