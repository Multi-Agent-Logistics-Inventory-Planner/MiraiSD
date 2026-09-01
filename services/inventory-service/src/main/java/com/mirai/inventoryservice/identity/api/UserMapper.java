package com.mirai.inventoryservice.identity.api;

import com.mirai.inventoryservice.identity.api.UserResponseDTO;
import com.mirai.inventoryservice.identity.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    UserResponseDTO toResponseDTO(User user);
    List<UserResponseDTO> toResponseDTOList(List<User> users);
}

