package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.NotificationResponseDTO;
import com.mirai.inventoryservice.models.audit.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface NotificationMapper {
    NotificationResponseDTO toResponseDTO(Notification notification);
    List<NotificationResponseDTO> toResponseDTOList(List<Notification> notifications);
}

