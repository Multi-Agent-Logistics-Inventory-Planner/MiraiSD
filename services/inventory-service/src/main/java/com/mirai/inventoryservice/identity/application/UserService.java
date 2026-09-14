package com.mirai.inventoryservice.identity.application;

import com.mirai.inventoryservice.identity.domain.UserNotFoundException;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.infrastructure.InvitationRepository;
import com.mirai.inventoryservice.identity.infrastructure.SupabaseAdminService;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final LastActorActivityPort lastActorActivityPort;
    private final InvitationRepository invitationRepository;
    private final SupabaseAdminService supabaseAdminService;

    public UserService(UserRepository userRepository,
                       LastActorActivityPort lastActorActivityPort,
                       InvitationRepository invitationRepository,
                       SupabaseAdminService supabaseAdminService) {
        this.userRepository = userRepository;
        this.lastActorActivityPort = lastActorActivityPort;
        this.invitationRepository = invitationRepository;
        this.supabaseAdminService = supabaseAdminService;
    }

    public User createUser(String fullName, String email, UserRole role) {
        String firstName = extractFirstName(fullName);

        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .role(role)
                .canonicalName(firstName)
                .isReviewTracked(true)
                .build();
        return userRepository.save(user);
    }

    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }

    public User getUserByFullName(String fullName) {
        return userRepository.findByFullName(fullName)
                .orElseThrow(() -> new UserNotFoundException("User not found with fullName: " + fullName));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(UUID id, String fullName, String email, UserRole role) {
        User user = getUserById(id);

        boolean nameChanged = fullName != null && !fullName.equals(user.getFullName());
        boolean roleChanged = role != null && role != user.getRole();

        if (fullName != null) user.setFullName(fullName);
        if (email != null) user.setEmail(email);
        if (role != null) user.setRole(role);

        User savedUser = userRepository.save(user);

        // Sync changes to Supabase auth (name and/or role)
        if (nameChanged || roleChanged) {
            supabaseAdminService.updateUserMetadata(
                user.getEmail(),
                nameChanged ? fullName : null,
                roleChanged ? role.name() : null
            );
        }

        return savedUser;
    }

    public void deleteUser(UUID id) {
        User user = getUserById(id);
        String email = user.getEmail();

        // Delete local user record
        userRepository.delete(user);

        // Delete invitation record if exists
        invitationRepository.deleteByEmail(email);

        // Delete user from Supabase auth. deleteUserByEmail swallows its own errors and reports
        // failure through the return value, so an unchecked call would let the backend row be
        // deleted while a still-valid Supabase account survives - a ghost that can authenticate
        // but resolves to no backend user, and that no admin screen lists. Fail the transaction
        // instead so the backend delete rolls back and the operation can be retried.
        if (!supabaseAdminService.deleteUserByEmail(email)) {
            throw new IllegalStateException(
                    "Deleted backend user " + id + " but failed to delete the matching Supabase "
                            + "auth account; rolling back so the two stores stay consistent.");
        }
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByFullName(String fullName) {
        return userRepository.existsByFullName(fullName);
    }

    /**
     * Resolves a backend user for an authenticated request, per
     * docs/specs/authentication-and-authorization.md section 2: the JWT {@code sub} claim
     * (supabaseUserId) is the stable identifier and must be preferred over email.
     * <p>
     * Existing rows created before supabase_user_id existed have no value there yet, so this
     * falls back to an email match and lazily backfills the column on that row when found -
     * every existing user is backfilled automatically on their first request after this change
     * ships, with no separate migration script.
     * <p>
     * The email fallback only ever backfills a row whose supabaseUserId is still null. A row
     * already bound to a different, non-null supabaseUserId is NEVER rebound by an email match:
     * email is not a stable identifier (a Supabase account can be deleted and its email reused
     * by a different account), so treating "same email" as authorization to take over an
     * already-bound identity would let a signed token for one Supabase account impersonate a
     * different backend account - and inherit its role - purely by sharing an email address.
     * That case returns no match and is logged for audit visibility.
     * <p>
     * The backfill write itself is an atomic conditional update (see
     * {@link UserRepository#backfillSupabaseUserId}), not a plain save: two concurrent requests
     * can both read the same row with supabase_user_id still null before either writes, and a
     * plain save would let the second writer silently overwrite the first with no version
     * column or row lock to catch it. If this request loses that race, it re-resolves by sub -
     * honoring the row if the winner bound it to the same sub this request presented, otherwise
     * rejecting rather than trusting its now-stale read.
     */
    public Optional<User> resolveBySupabaseIdOrEmail(UUID supabaseUserId, String email) {
        if (supabaseUserId != null) {
            Optional<User> bySub = userRepository.findBySupabaseUserId(supabaseUserId);
            if (bySub.isPresent()) {
                return bySub;
            }
        }

        if (email == null) {
            return Optional.empty();
        }

        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isEmpty()) {
            return Optional.empty();
        }

        User user = byEmail.get();
        UUID existingSupabaseUserId = user.getSupabaseUserId();

        if (existingSupabaseUserId != null) {
            if (!existingSupabaseUserId.equals(supabaseUserId)) {
                log.warn("Rejected identity rebind attempt: email {} is already bound to "
                                + "supabaseUserId {}, but the request presented sub {}",
                        email, existingSupabaseUserId, supabaseUserId);
                return Optional.empty();
            }
            return byEmail;
        }

        if (supabaseUserId == null) {
            return byEmail;
        }

        int updated = userRepository.backfillSupabaseUserId(user.getId(), supabaseUserId);
        if (updated == 0) {
            // Lost the race to another concurrent request. Re-resolve by sub rather than trust
            // this request's stale in-memory read of the row.
            return userRepository.findBySupabaseUserId(supabaseUserId);
        }
        return userRepository.findById(user.getId());
    }

    public User createFromJwt(String email, String name, String role, UUID supabaseUserId) {
        UserRole userRole = UserRole.EMPLOYEE;
        if (role != null) {
            try {
                userRole = UserRole.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Default to EMPLOYEE if invalid role
            }
        }

        String fullName = name != null ? name : email.split("@")[0];
        String firstName = extractFirstName(fullName);

        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .role(userRole)
                .canonicalName(firstName)
                .isReviewTracked(true)
                .supabaseUserId(supabaseUserId)
                .build();
        return userRepository.save(user);
    }

    /**
     * Extracts the first name from a full name string.
     * @param fullName the full name (e.g., "John Doe")
     * @return the first name (e.g., "John")
     */
    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    public Optional<OffsetDateTime> getLastAuditDate(UUID userId) {
        return lastActorActivityPort.lastActivityFor(userId);
    }

    public Map<UUID, OffsetDateTime> getAllLastAuditDates() {
        return lastActorActivityPort.lastActivityByActor();
    }
}

