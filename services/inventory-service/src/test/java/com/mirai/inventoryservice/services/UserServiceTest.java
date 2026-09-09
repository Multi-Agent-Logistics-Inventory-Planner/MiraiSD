package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.identity.application.LastActorActivityPort;
import com.mirai.inventoryservice.identity.application.UserService;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.infrastructure.InvitationRepository;
import com.mirai.inventoryservice.identity.infrastructure.SupabaseAdminService;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers resolveBySupabaseIdOrEmail's three branches per
 * docs/specs/authentication-and-authorization.md section 2: prefer the JWT sub, fall back to
 * email for rows not yet backfilled, and lazily persist the sub onto that row when found that
 * way.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LastActorActivityPort lastActorActivityPort;

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private SupabaseAdminService supabaseAdminService;

    @InjectMocks
    private UserService userService;

    private static User userWithId(UUID id) {
        return User.builder()
                .id(id)
                .fullName("Test User")
                .email("user@example.com")
                .role(UserRole.EMPLOYEE)
                .build();
    }

    private static User userWithId(UUID id, UUID supabaseUserId) {
        return User.builder()
                .id(id)
                .fullName("Test User")
                .email("user@example.com")
                .role(UserRole.EMPLOYEE)
                .supabaseUserId(supabaseUserId)
                .build();
    }

    @Test
    void resolveBySupabaseIdOrEmail_SubMatch_ReturnsUserWithoutTouchingEmail() {
        UUID sub = UUID.randomUUID();
        User user = userWithId(UUID.randomUUID());
        when(userRepository.findBySupabaseUserId(sub)).thenReturn(Optional.of(user));

        Optional<User> result = userService.resolveBySupabaseIdOrEmail(sub, "user@example.com");

        assertTrue(result.isPresent());
        assertEquals(user, result.get());
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resolveBySupabaseIdOrEmail_EmailFallback_BackfillsSupabaseUserId() {
        UUID sub = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User existing = userWithId(userId);
        User backfilled = userWithId(userId, sub);
        when(userRepository.findBySupabaseUserId(sub)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.backfillSupabaseUserId(userId, sub)).thenReturn(1);
        when(userRepository.findById(userId)).thenReturn(Optional.of(backfilled));

        Optional<User> result = userService.resolveBySupabaseIdOrEmail(sub, "user@example.com");

        assertTrue(result.isPresent());
        assertEquals(sub, result.get().getSupabaseUserId());
    }

    @Test
    void resolveBySupabaseIdOrEmail_LosesBackfillRace_ReResolvesBySub() {
        // Simulates two concurrent requests both reading the same row with
        // supabase_user_id = NULL: this request's conditional update affects zero rows because
        // another request already won and bound the row first.
        UUID sub = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User existing = userWithId(userId);
        User winnerBoundRow = userWithId(userId, sub);
        when(userRepository.findBySupabaseUserId(sub))
                .thenReturn(Optional.empty(), Optional.of(winnerBoundRow));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.backfillSupabaseUserId(userId, sub)).thenReturn(0);

        Optional<User> result = userService.resolveBySupabaseIdOrEmail(sub, "user@example.com");

        assertTrue(result.isPresent());
        assertEquals(sub, result.get().getSupabaseUserId());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resolveBySupabaseIdOrEmail_EmailMatchesUserBoundToDifferentSub_RejectsAndDoesNotRebind() {
        // A different Supabase account (a reused/shared email, or a deleted-and-recreated
        // account) must NEVER take over an already-bound backend user just by sharing its
        // email - that would let a signed token for the wrong account inherit this user's role.
        UUID incomingSub = UUID.randomUUID();
        UUID boundSub = UUID.randomUUID();
        User boundUser = userWithId(UUID.randomUUID(), boundSub);
        when(userRepository.findBySupabaseUserId(incomingSub)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(boundUser));

        Optional<User> result = userService.resolveBySupabaseIdOrEmail(incomingSub, "user@example.com");

        assertFalse(result.isPresent());
        verify(userRepository, never()).save(any());
        assertEquals(boundSub, boundUser.getSupabaseUserId(), "existing binding must be untouched");
    }

    @Test
    void resolveBySupabaseIdOrEmail_NoMatch_ReturnsEmptyWithoutSaving() {
        UUID sub = UUID.randomUUID();
        when(userRepository.findBySupabaseUserId(sub)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.resolveBySupabaseIdOrEmail(sub, "nobody@example.com");

        assertFalse(result.isPresent());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resolveBySupabaseIdOrEmail_NullSubAndEmail_ReturnsEmpty() {
        Optional<User> result = userService.resolveBySupabaseIdOrEmail(null, null);

        assertFalse(result.isPresent());
    }

    @Test
    void deleteUser_SupabaseDeleteSucceeds_DeletesBackendUser() {
        UUID userId = UUID.randomUUID();
        User user = userWithId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(supabaseAdminService.deleteUserByEmail("user@example.com")).thenReturn(true);

        userService.deleteUser(userId);

        verify(userRepository).delete(user);
        verify(invitationRepository).deleteByEmail("user@example.com");
    }

    @Test
    void deleteUser_SupabaseDeleteFails_ThrowsSoBackendDeleteRollsBack() {
        // deleteUserByEmail reports failure by return value rather than throwing. Left
        // unchecked, the backend row would be deleted and committed while a still-valid
        // Supabase account survived - a ghost that authenticates but resolves to no backend
        // user, and that no admin screen lists.
        UUID userId = UUID.randomUUID();
        User user = userWithId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(supabaseAdminService.deleteUserByEmail("user@example.com")).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> userService.deleteUser(userId));
    }

    // Phase 6a T-2 (.specs/phase-6-inventory/log.md, R-3): UserService no longer reaches
    // StockMovementRepository directly -- it delegates to LastActorActivityPort, so this proves
    // the delegation, not stock-movement query logic (that lives in the port's adapter).

    @Test
    void getLastAuditDate_DelegatesToPort() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime lastActivity = OffsetDateTime.now();
        when(lastActorActivityPort.lastActivityFor(userId)).thenReturn(Optional.of(lastActivity));

        Optional<OffsetDateTime> result = userService.getLastAuditDate(userId);

        assertEquals(Optional.of(lastActivity), result);
    }

    @Test
    void getLastAuditDate_NoActivity_ReturnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(lastActorActivityPort.lastActivityFor(userId)).thenReturn(Optional.empty());

        assertFalse(userService.getLastAuditDate(userId).isPresent());
    }

    @Test
    void getAllLastAuditDates_DelegatesToPort() {
        UUID actorId = UUID.randomUUID();
        OffsetDateTime lastActivity = OffsetDateTime.now();
        Map<UUID, OffsetDateTime> activity = Map.of(actorId, lastActivity);
        when(lastActorActivityPort.lastActivityByActor()).thenReturn(activity);

        Map<UUID, OffsetDateTime> result = userService.getAllLastAuditDates();

        assertEquals(activity, result);
    }
}
