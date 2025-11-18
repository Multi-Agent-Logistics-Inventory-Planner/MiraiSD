package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.UserMapper;
import com.mirai.inventoryservice.dtos.requests.UserRequestDTO;
import com.mirai.inventoryservice.dtos.responses.UserResponseDTO;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(userMapper.toResponseDTOList(users));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable UUID id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(userMapper.toResponseDTO(user));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponseDTO> getUserByEmail(@PathVariable String email) {
        User user = userService.getUserByEmail(email);
        return ResponseEntity.ok(userMapper.toResponseDTO(user));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<UserResponseDTO> getUserByUsername(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        return ResponseEntity.ok(userMapper.toResponseDTO(user));
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody UserRequestDTO requestDTO) {
        User user = userService.createUser(
                requestDTO.getUsername(),
                requestDTO.getEmail(),
                requestDTO.getRole(),
                requestDTO.getSupabaseUid());
        return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toResponseDTO(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UserRequestDTO requestDTO) {
        User user = userService.updateUser(
                id,
                requestDTO.getUsername(),
                requestDTO.getEmail(),
                requestDTO.getRole(),
                requestDTO.getSupabaseUid());
        return ResponseEntity.ok(userMapper.toResponseDTO(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}

