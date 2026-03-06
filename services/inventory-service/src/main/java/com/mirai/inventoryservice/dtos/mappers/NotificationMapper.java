package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.NotificationResponseDTO;
import com.mirai.inventoryservice.models.audit.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface NotificationMapper {
    @Mapping(target = "itemName", ignore = true)
    NotificationResponseDTO toResponseDTO(Notification notification);
    List<NotificationResponseDTO> toResponseDTOList(List<Notification> notifications);
}

