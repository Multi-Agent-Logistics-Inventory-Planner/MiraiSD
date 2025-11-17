package com.pm.inventoryservice.services;

import com.pm.inventoryservice.dtos.mappers.UserMapper;
import com.pm.inventoryservice.dtos.requests.UserRequestDTO;
import com.pm.inventoryservice.dtos.responses.UserResponseDTO;
import com.pm.inventoryservice.exceptions.EmailAlreadyExistsException;
import com.pm.inventoryservice.exceptions.UserNotFoundException;
import com.pm.inventoryservice.models.User;
import com.pm.inventoryservice.repositories.UserRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(UserMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(@NonNull UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
        return UserMapper.toDTO(user);
    }

    @Transactional
    public UserResponseDTO createUser(@NonNull UserRequestDTO userRequestDTO) {
        if (userRepository.existsByEmail(userRequestDTO.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "A user with this email already exists: " + userRequestDTO.getEmail());
        }
        User newUser = userRepository.save(UserMapper.toEntity(userRequestDTO));
        return UserMapper.toDTO(newUser);
    }

    @Transactional
    public UserResponseDTO updateUser(@NonNull UUID id, @NonNull UserRequestDTO userRequestDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));

        if (userRepository.existsByEmailAndIdNot(userRequestDTO.getEmail(), id)) {
            throw new EmailAlreadyExistsException(
                    "A user with this email already exists: " + userRequestDTO.getEmail());
        }

        user.setFullName(userRequestDTO.getFullName());
        user.setRole(userRequestDTO.getRole());
        user.setEmail(userRequestDTO.getEmail());

        User updatedUser = userRepository.save(user);
        return UserMapper.toDTO(updatedUser);
    }

    @Transactional
    public void deleteUser(@NonNull UUID id) {
        userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
        userRepository.deleteById(id);
    }
}
