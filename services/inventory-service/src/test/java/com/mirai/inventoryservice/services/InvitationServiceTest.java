package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.models.audit.Invitation;
import com.mirai.inventoryservice.repositories.InvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private SupabaseAdminService supabaseAdminService;

    @Mock
    private EmailService emailService;

    @Mock
    private UserService userService;

    @InjectMocks
    private InvitationService invitationService;

    private static final String EMAIL = "invitee@example.com";

    private Invitation pendingInvitation;

    @BeforeEach
    void setUp() {
        pendingInvitation = Invitation.builder()
                .email(EMAIL)
                .role("employee")
                .build();
    }

    @Test
    @DisplayName("cancelInvitation removes the local invite and the orphan Supabase auth user")
    void cancelInvitation_pendingInvite_deletesSupabaseAuthUser() {
        when(invitationRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pendingInvitation));

        invitationService.cancelInvitation(EMAIL);

        verify(invitationRepository).delete(pendingInvitation);
        verify(supabaseAdminService, times(1)).deleteUserByEmail(EMAIL);
    }

    @Test
    @DisplayName("cancelInvitation refuses to cancel an accepted invite and leaves Supabase untouched")
    void cancelInvitation_acceptedInvite_throwsAndDoesNotTouchSupabase() {
        pendingInvitation.setAcceptedAt(OffsetDateTime.now());
        when(invitationRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pendingInvitation));

        assertThrows(RuntimeException.class, () -> invitationService.cancelInvitation(EMAIL));

        verify(invitationRepository, never()).delete(pendingInvitation);
        verify(supabaseAdminService, never()).deleteUserByEmail(EMAIL);
    }

    @Test
    @DisplayName("cancelInvitation throws when no invite exists and never calls Supabase")
    void cancelInvitation_notFound_throwsAndDoesNotTouchSupabase() {
        when(invitationRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> invitationService.cancelInvitation(EMAIL));

        verify(supabaseAdminService, never()).deleteUserByEmail(EMAIL);
    }
}
