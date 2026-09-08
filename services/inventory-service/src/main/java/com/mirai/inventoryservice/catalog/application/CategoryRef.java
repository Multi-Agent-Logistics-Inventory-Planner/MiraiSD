package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Category;

import java.util.UUID;

/** Immutable, catalog-external view of a {@link Category}. */
public record CategoryRef(
        UUID id,
        String name,
        String slug,
        UUID parentId,
        Integer displayOrder,
        Boolean isActive,
        Boolean usesPacks
) {

    public static CategoryRef from(Category category) {
        return new CategoryRef(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getParentId(),
                category.getDisplayOrder(),
                category.getIsActive(),
                category.getUsesPacks()
        );
    }
}
