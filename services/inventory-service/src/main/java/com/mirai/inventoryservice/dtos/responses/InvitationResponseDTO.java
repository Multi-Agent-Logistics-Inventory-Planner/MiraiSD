package com.mirai.inventoryservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponseDTO {
    private UUID id;
    private String email;
    private String role;
    private String invitedByEmail;
    private OffsetDateTime invitedAt;
    private OffsetDateTime acceptedAt;
    private String status;
}
