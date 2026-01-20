package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.models.audit.Invitation;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.repositories.InvitationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional
public class InvitationService {
    private final InvitationRepository invitationRepository;
    private final SupabaseAdminService supabaseAdminService;
    private final UserService userService;

    public InvitationService(InvitationRepository invitationRepository,
                             SupabaseAdminService supabaseAdminService,
                             UserService userService) {
        this.invitationRepository = invitationRepository;
        this.supabaseAdminService = supabaseAdminService;
        this.userService = userService;
    }

    public Invitation inviteUser(String email, UserRole role, String inviterEmail, String inviterName) {
        if (invitationRepository.existsByEmail(email)) {
            throw new RuntimeException("Invitation already exists for email: " + email);
        }

        if (userService.existsByEmail(email)) {
            throw new RuntimeException("User already exists with email: " + email);
        }

        // Get or create inviter's User record (handles admin bootstrap case)
        User invitedBy;
        if (userService.existsByEmail(inviterEmail)) {
            invitedBy = userService.getUserByEmail(inviterEmail);
        } else {
            // Auto-create admin user record if not exists
            invitedBy = userService.createFromJwt(inviterEmail, inviterName, "ADMIN");
        }

        supabaseAdminService.inviteUserByEmail(email, role.name());

        Invitation invitation = Invitation.builder()
                .email(email)
                .role(role.name().toLowerCase())
                .invitedBy(invitedBy)
                .build();

        return invitationRepository.save(invitation);
    }

    public List<Invitation> getPendingInvitations() {
        return invitationRepository.findByAcceptedAtIsNull();
    }

    public Invitation resendInvitation(String email) {
        Invitation invitation = invitationRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invitation not found for email: " + email));

        if (invitation.getAcceptedAt() != null) {
            throw new RuntimeException("Invitation has already been accepted");
        }

        supabaseAdminService.resendInvitation(email, invitation.getRole());
        return invitation;
    }

    public void cancelInvitation(String email) {
        Invitation invitation = invitationRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invitation not found for email: " + email));

        if (invitation.getAcceptedAt() != null) {
            throw new RuntimeException("Cannot cancel an accepted invitation");
        }

        invitationRepository.delete(invitation);
    }

    public void markInvitationAccepted(String email) {
        invitationRepository.findByEmail(email).ifPresent(invitation -> {
            invitation.setAcceptedAt(OffsetDateTime.now());
            invitationRepository.save(invitation);
        });
    }

    public String getRoleForEmail(String email) {
        return invitationRepository.findByEmail(email)
                .map(Invitation::getRole)
                .orElse(null);
    }
}
