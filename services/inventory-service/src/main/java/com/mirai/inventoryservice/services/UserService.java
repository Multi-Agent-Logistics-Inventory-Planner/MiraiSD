package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.UserNotFoundException;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.repositories.InvitationRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InvitationRepository invitationRepository;
    private final SupabaseAdminService supabaseAdminService;

    public UserService(UserRepository userRepository,
                       StockMovementRepository stockMovementRepository,
                       InvitationRepository invitationRepository,
                       SupabaseAdminService supabaseAdminService) {
        this.userRepository = userRepository;
        this.stockMovementRepository = stockMovementRepository;
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

        if (fullName != null) user.setFullName(fullName);
        if (email != null) user.setEmail(email);
        if (role != null) user.setRole(role);

        User savedUser = userRepository.save(user);

        // Sync name change to Supabase auth
        if (nameChanged) {
            supabaseAdminService.updateUserMetadata(user.getEmail(), fullName);
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

        // Delete user from Supabase auth
        supabaseAdminService.deleteUserByEmail(email);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByFullName(String fullName) {
        return userRepository.existsByFullName(fullName);
    }

    public User createFromJwt(String email, String name, String role) {
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
        return stockMovementRepository.findTopByActorIdOrderByAtDesc(userId)
                .map(StockMovement::getAt);
    }

    public Map<UUID, OffsetDateTime> getAllLastAuditDates() {
        List<Object[]> results = stockMovementRepository.findLatestMovementTimestampsByActor();
        Map<UUID, OffsetDateTime> map = new HashMap<>();
        for (Object[] row : results) {
            UUID actorId = (UUID) row[0];
            OffsetDateTime timestamp = (OffsetDateTime) row[1];
            map.put(actorId, timestamp);
        }
        return map;
    }
}

