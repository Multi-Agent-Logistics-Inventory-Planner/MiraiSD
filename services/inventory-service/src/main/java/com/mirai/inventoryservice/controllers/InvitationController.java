package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.InvitationMapper;
import com.mirai.inventoryservice.dtos.requests.InvitationRequestDTO;
import com.mirai.inventoryservice.dtos.responses.InvitationResponseDTO;
import com.mirai.inventoryservice.models.audit.Invitation;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.services.InvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/invitations")
@PreAuthorize("hasRole('ADMIN')")
public class InvitationController {
    private final InvitationService invitationService;
    private final InvitationMapper invitationMapper;

    public InvitationController(InvitationService invitationService, InvitationMapper invitationMapper) {
        this.invitationService = invitationService;
        this.invitationMapper = invitationMapper;
    }

    @GetMapping
    public ResponseEntity<List<InvitationResponseDTO>> getPendingInvitations() {
        List<Invitation> invitations = invitationService.getPendingInvitations();
        return ResponseEntity.ok(invitationMapper.toResponseDTOList(invitations));
    }

    @PostMapping
    public ResponseEntity<InvitationResponseDTO> inviteUser(
            @Valid @RequestBody InvitationRequestDTO requestDTO,
            Authentication authentication) {
        @SuppressWarnings("unchecked")
        Map<String, String> principal = (Map<String, String>) authentication.getPrincipal();
        String inviterEmail = principal.get("email");
        String inviterName = principal.get("personName");

        UserRole role = UserRole.valueOf(requestDTO.getRole().toUpperCase());

        Invitation invitation = invitationService.inviteUser(
                requestDTO.getEmail(),
                role,
                inviterEmail,
                inviterName);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invitationMapper.toResponseDTO(invitation));
    }

    @PostMapping("/{email}/resend")
    public ResponseEntity<InvitationResponseDTO> resendInvitation(@PathVariable String email) {
        Invitation invitation = invitationService.resendInvitation(email);
        return ResponseEntity.ok(invitationMapper.toResponseDTO(invitation));
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<Void> cancelInvitation(@PathVariable String email) {
        invitationService.cancelInvitation(email);
        return ResponseEntity.noContent().build();
    }
}
