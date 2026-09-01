package com.mirai.inventoryservice.identity.api;

import com.mirai.inventoryservice.identity.api.InvitationMapper;
import com.mirai.inventoryservice.identity.api.InvitationRequestDTO;
import com.mirai.inventoryservice.identity.api.InvitationResponseDTO;
import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.identity.domain.Invitation;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.application.InvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/invitations")
public class InvitationController {
    private final InvitationService invitationService;
    private final InvitationMapper invitationMapper;

    public InvitationController(InvitationService invitationService, InvitationMapper invitationMapper) {
        this.invitationService = invitationService;
        this.invitationMapper = invitationMapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<InvitationResponseDTO>> getPendingInvitations() {
        List<Invitation> invitations = invitationService.getPendingInvitations();
        return ResponseEntity.ok(invitationMapper.toResponseDTOList(invitations));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvitationResponseDTO> inviteUser(
            @Valid @RequestBody InvitationRequestDTO requestDTO,
            Authentication authentication) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        String inviterEmail = principal.email();
        String inviterName = principal.personName();

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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvitationResponseDTO> resendInvitation(@PathVariable String email) {
        Invitation invitation = invitationService.resendInvitation(email);
        return ResponseEntity.ok(invitationMapper.toResponseDTO(invitation));
    }

    @DeleteMapping("/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cancelInvitation(@PathVariable String email) {
        invitationService.cancelInvitation(email);
        return ResponseEntity.noContent().build();
    }
}
