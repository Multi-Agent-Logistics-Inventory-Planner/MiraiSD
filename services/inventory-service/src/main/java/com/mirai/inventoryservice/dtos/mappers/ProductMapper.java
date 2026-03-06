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
import java.util.stream.Collectors;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductMapper {
    @Mapping(target = "category", source = "category", qualifiedByName = "categoryToDTO")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "parentName", source = "parent.name")
    @Mapping(target = "parentSku", source = "parent.sku")
    @Mapping(target = "hasChildren", expression = "java(product.getChildren() != null && !product.getChildren().isEmpty())")
    @Mapping(target = "children", source = "children", qualifiedByName = "childrenToSummaryList")
    @Mapping(target = "totalChildStock", ignore = true) // Computed separately in service
    ProductResponseDTO toResponseDTO(Product product);

    @Mapping(target = "category", source = "category", qualifiedByName = "categoryToDTO")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "hasChildren", expression = "java(product.getChildren() != null && !product.getChildren().isEmpty())")
    ProductSummaryDTO toSummaryDTO(Product product);

    List<ProductResponseDTO> toResponseDTOList(List<Product> products);

    List<ProductSummaryDTO> toSummaryDTOList(List<Product> products);

    @Named("childrenToSummaryList")
    default List<ProductSummaryDTO> childrenToSummaryList(List<Product> children) {
        if (children == null || children.isEmpty()) {
            return Collections.emptyList();
        }
        return children.stream()
                .map(this::toSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create DTO with computed totalChildStock
     */
    default ProductResponseDTO toResponseDTOWithAggregates(Product product, Integer totalChildStock) {
        ProductResponseDTO dto = toResponseDTO(product);
        dto.setTotalChildStock(totalChildStock);
        return dto;
    }

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
