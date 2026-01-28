package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.ShipmentItemAllocationResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentItemResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentResponseDTO;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.models.shipment.ShipmentItem;
import com.mirai.inventoryservice.models.shipment.ShipmentItemAllocation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {ProductMapper.class, UserMapper.class})
public interface ShipmentMapper {
    ShipmentResponseDTO toResponseDTO(Shipment shipment);

    List<ShipmentResponseDTO> toResponseDTOList(List<Shipment> shipments);

    @Mapping(source = "item", target = "item")
    @Mapping(source = "allocations", target = "allocations")
    ShipmentItemResponseDTO toItemResponseDTO(ShipmentItem shipmentItem);

    List<ShipmentItemResponseDTO> toItemResponseDTOList(List<ShipmentItem> shipmentItems);

    ShipmentItemAllocationResponseDTO toAllocationResponseDTO(ShipmentItemAllocation allocation);

    List<ShipmentItemAllocationResponseDTO> toAllocationResponseDTOList(List<ShipmentItemAllocation> allocations);
}
