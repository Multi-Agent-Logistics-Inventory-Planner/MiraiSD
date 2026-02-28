package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.CategoryResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ProductResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ProductSummaryDTO;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductMapper {
    @Mapping(target = "category", source = "category", qualifiedByName = "categoryToDTO")
    ProductResponseDTO toResponseDTO(Product product);

    @Mapping(target = "category", source = "category", qualifiedByName = "categoryToDTO")
    ProductSummaryDTO toSummaryDTO(Product product);

    List<ProductResponseDTO> toResponseDTOList(List<Product> products);

    List<ProductSummaryDTO> toSummaryDTOList(List<Product> products);

    @Named("categoryToDTO")
    default CategoryResponseDTO categoryToDTO(Category category) {
        if (category == null) {
            return null;
        }
        return CategoryResponseDTO.builder()
                .id(category.getId())
                .parentId(category.getParentId())
                .name(category.getName())
                .slug(category.getSlug())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .children(Collections.emptyList())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
