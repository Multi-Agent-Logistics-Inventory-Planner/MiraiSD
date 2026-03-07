package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.CategoryResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ProductResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ProductSummaryDTO;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import org.hibernate.Hibernate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductMapper {
    @Mapping(target = "category", source = "category", qualifiedByName = "categoryToDTO")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "parentName", source = "product", qualifiedByName = "safeParentName")
    @Mapping(target = "parentSku", source = "product", qualifiedByName = "safeParentSku")
    @Mapping(target = "hasChildren", source = "product", qualifiedByName = "safeHasChildren")
    @Mapping(target = "children", source = "product", qualifiedByName = "safeChildren")
    @Mapping(target = "totalChildStock", ignore = true) // Computed separately in service
    ProductResponseDTO toResponseDTO(Product product);

    @Mapping(target = "category", source = "category", qualifiedByName = "categoryToDTO")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "hasChildren", source = "product", qualifiedByName = "safeHasChildren")
    ProductSummaryDTO toSummaryDTO(Product product);

    List<ProductResponseDTO> toResponseDTOList(List<Product> products);

    List<ProductSummaryDTO> toSummaryDTOList(List<Product> products);

    /**
     * Convert products to DTOs with precomputed hasChildren flags (avoids N+1)
     */
    default List<ProductResponseDTO> toResponseDTOList(List<Product> products, Set<UUID> parentIds) {
        return products.stream()
                .map(p -> toResponseDTOWithParentIds(p, parentIds))
                .collect(Collectors.toList());
    }

    /**
     * Convert product to DTO using precomputed parent IDs set
     */
    default ProductResponseDTO toResponseDTOWithParentIds(Product product, Set<UUID> parentIds) {
        ProductResponseDTO dto = toResponseDTO(product);
        dto.setHasChildren(parentIds.contains(product.getId()));
        return dto;
    }

    // Safe accessors that avoid triggering lazy loads on uninitialized proxies

    @Named("safeParentName")
    default String safeParentName(Product product) {
        if (product.getParent() == null || !Hibernate.isInitialized(product.getParent())) {
            return null;
        }
        return product.getParent().getName();
    }

    @Named("safeParentSku")
    default String safeParentSku(Product product) {
        if (product.getParent() == null || !Hibernate.isInitialized(product.getParent())) {
            return null;
        }
        return product.getParent().getSku();
    }

    @Named("safeHasChildren")
    default Boolean safeHasChildren(Product product) {
        // Only check if children collection is already initialized (fetched via JOIN FETCH)
        if (!Hibernate.isInitialized(product.getChildren())) {
            return null; // Will be set via parentIds set if needed
        }
        return product.getChildren() != null && !product.getChildren().isEmpty();
    }

    @Named("safeChildren")
    default List<ProductSummaryDTO> safeChildren(Product product) {
        // Only map children if already initialized (fetched via JOIN FETCH)
        if (!Hibernate.isInitialized(product.getChildren()) || product.getChildren() == null || product.getChildren().isEmpty()) {
            return Collections.emptyList();
        }
        return product.getChildren().stream()
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
