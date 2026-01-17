package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.ProductResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ProductSummaryDTO;
import com.mirai.inventoryservice.models.Product;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductMapper {
    ProductResponseDTO toResponseDTO(Product product);

    ProductSummaryDTO toSummaryDTO(Product product);

    List<ProductResponseDTO> toResponseDTOList(List<Product> products);

    List<ProductSummaryDTO> toSummaryDTOList(List<Product> products);
}
