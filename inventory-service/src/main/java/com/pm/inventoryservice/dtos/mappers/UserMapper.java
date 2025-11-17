package com.pm.inventoryservice.dtos.mappers;

import com.pm.inventoryservice.dtos.requests.UserRequestDTO;
import com.pm.inventoryservice.dtos.responses.UserResponseDTO;
import com.pm.inventoryservice.models.User;
import org.springframework.lang.NonNull;

public final class UserMapper {

    private UserMapper() {
        throw new IllegalStateException("Utility class");
    }

    @NonNull
    public static UserResponseDTO toDTO(@NonNull User user) {
        UserResponseDTO userDTO = new UserResponseDTO();
        if (user.getId() != null) {
            userDTO.setId(user.getId().toString());
        }
        if (user.getRole() != null) {
            userDTO.setRole(user.getRole().name());
        }
        userDTO.setFullName(user.getFullName());
        userDTO.setEmail(user.getEmail());
        return userDTO;
    }

    @NonNull
    public static User toEntity(@NonNull UserRequestDTO userRequestDTO) {
        User user = new User();
        user.setRole(userRequestDTO.getRole());
        user.setFullName(userRequestDTO.getFullName());
        user.setEmail(userRequestDTO.getEmail());
        return user;
    }
}
