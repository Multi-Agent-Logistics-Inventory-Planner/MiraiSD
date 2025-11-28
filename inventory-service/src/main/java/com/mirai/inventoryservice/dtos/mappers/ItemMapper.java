package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.requests.ItemRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ItemResponseDTO;
import com.mirai.inventoryservice.models.Item;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ItemMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Item toEntity(ItemRequestDTO dto);
    
    ItemResponseDTO toResponseDTO(Item entity);
    
    List<ItemResponseDTO> toResponseDTOList(List<Item> entities);
    
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDTO(ItemRequestDTO dto, @MappingTarget Item entity);
}


