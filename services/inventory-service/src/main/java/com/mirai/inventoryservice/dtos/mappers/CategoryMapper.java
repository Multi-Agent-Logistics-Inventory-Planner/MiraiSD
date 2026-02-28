package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.CategoryResponseDTO;
import com.mirai.inventoryservice.models.Category;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CategoryMapper {

    /**
     * Convert category to DTO with children included
     */
    public CategoryResponseDTO toResponseDTO(Category category) {
        return CategoryResponseDTO.builder()
                .id(category.getId())
                .parentId(category.getParentId())
                .name(category.getName())
                .slug(category.getSlug())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .children(toChildrenDTOList(category.getChildren()))
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    /**
     * Convert category to DTO without children (flat representation)
     */
    public CategoryResponseDTO toResponseDTOFlat(Category category) {
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

    public List<CategoryResponseDTO> toResponseDTOList(List<Category> categories) {
        return categories.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    public List<CategoryResponseDTO> toResponseDTOListFlat(List<Category> categories) {
        return categories.stream()
                .map(this::toResponseDTOFlat)
                .collect(Collectors.toList());
    }

    private List<CategoryResponseDTO> toChildrenDTOList(List<Category> children) {
        // Safety check: avoid lazy loading if children collection isn't initialized
        if (children == null || !Hibernate.isInitialized(children)) {
            return Collections.emptyList();
        }
        return children.stream()
                .filter(Category::getIsActive)
                .map(this::toResponseDTOFlat) // Children don't need nested children
                .collect(Collectors.toList());
    }
}
