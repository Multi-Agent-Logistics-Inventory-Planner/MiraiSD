package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.UserNotFoundException;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(String username, String email, UserRole role, String supabaseUid) {
        User user = User.builder()
                .username(username)
                .email(email)
                .role(role)
                .supabaseUid(supabaseUid)
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

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
    }

    public User getUserBySupabaseUid(String supabaseUid) {
        return userRepository.findBySupabaseUid(supabaseUid)
                .orElseThrow(() -> new UserNotFoundException("User not found with supabaseUid: " + supabaseUid));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(UUID id, String username, String email, UserRole role, String supabaseUid) {
        User user = getUserById(id);
        
        if (username != null) user.setUsername(username);
        if (email != null) user.setEmail(email);
        if (role != null) user.setRole(role);
        if (supabaseUid != null) user.setSupabaseUid(supabaseUid);
        
        return userRepository.save(user);
    }

    public void deleteUser(UUID id) {
        User user = getUserById(id);
        userRepository.delete(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
}

