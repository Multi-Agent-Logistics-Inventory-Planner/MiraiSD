package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.WindowInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.WindowInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {ProductMapper.class})
public interface WindowInventoryMapper {
    @Mapping(source = "window.id", target = "windowId")
    @Mapping(source = "window.windowCode", target = "windowCode")
    @Mapping(source = "item", target = "item")
    WindowInventoryResponseDTO toResponseDTO(WindowInventory inventory);

    List<WindowInventoryResponseDTO> toResponseDTOList(List<WindowInventory> inventories);
}

