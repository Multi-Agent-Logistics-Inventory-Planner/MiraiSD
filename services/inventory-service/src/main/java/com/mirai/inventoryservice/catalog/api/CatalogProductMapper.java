package com.mirai.inventoryservice.catalog.api;

import com.mirai.inventoryservice.catalog.domain.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CatalogProductMapper {
    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "categoryName", source = "product.category.name")
    // Product.parentId is a read-only shadow column (insertable = false, updatable = false) that
    // only reflects the DB value after a reload - a freshly created/updated Product in the same
    // transaction has it null even when product.parent is set, so map from the association itself.
    @Mapping(target = "parentId", source = "product.parent.id")
    CatalogProductResponse toResponse(Product product);
}
