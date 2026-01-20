package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.UserNotFoundException;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;

    public UserService(UserRepository userRepository, StockMovementRepository stockMovementRepository) {
        this.userRepository = userRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    public User createUser(String fullName, String email, UserRole role) {
        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .role(role)
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

        if (fullName != null) user.setFullName(fullName);
        if (email != null) user.setEmail(email);
        if (role != null) user.setRole(role);

        return userRepository.save(user);
    }

    public void deleteUser(UUID id) {
        User user = getUserById(id);
        userRepository.delete(user);
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

        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .role(userRole)
                .build();
        return userRepository.save(user);
    }

    public Optional<OffsetDateTime> getLastAuditDate(UUID userId) {
        return stockMovementRepository.findTopByActorIdOrderByAtDesc(userId)
                .map(StockMovement::getAt);
    }
}

