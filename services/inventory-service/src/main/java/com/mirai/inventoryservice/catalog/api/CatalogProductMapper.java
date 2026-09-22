package com.mirai.inventoryservice.catalog.api;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import org.hibernate.Hibernate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.Collections;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CatalogProductMapper {
    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "categoryName", source = "product.category.name")
    // Product.parentId is a read-only shadow column (insertable = false, updatable = false) that
    // only reflects the DB value after a reload - a freshly created/updated Product in the same
    // transaction has it null even when product.parent is set, so map from the association itself.
    @Mapping(target = "parentId", source = "product.parent.id")
    // Without this explicit mapping, MapStruct auto-generates a nested Category ->
    // CategoryResponseDTO sub-mapper for the same-named "category" property (both sides expose
    // matching field names), which recursively maps the LAZY children association -- triggering
    // an unbounded chain of lazy-load queries at mapping time and returning a subtree shape that
    // disagrees with the list endpoint's flat, childless category (CATALOG_LIST_SELECT's
    // CategoryResponseDTO.builder()...children(emptyList())). flatCategory below matches that
    // same flat shape explicitly instead.
    @Mapping(target = "category", source = "product.category", qualifiedByName = "flatCategory")
    // preferredSupplierId auto-maps from Product.preferredSupplierId (a real, same-named shadow
    // column), but preferredSupplierName has no matching source property on Product -- it only
    // exists on the associated Supplier -- so MapStruct silently leaves it unmapped (null)
    // without this explicit mapping. Same safe-accessor pattern as ProductMapper's
    // safePreferredSupplierName: guards a not-yet-initialized lazy association rather than
    // assuming every caller already JOIN FETCHed it.
    @Mapping(target = "preferredSupplierName", source = "product", qualifiedByName = "safePreferredSupplierName")
    // hasChildren has no source at all on Product; every caller sets it explicitly afterward
    // (single-product reads via ProductService.countChildren, list reads via the query's own
    // correlated EXISTS) - map it away here so MapStruct doesn't warn about an unmapped target.
    @Mapping(target = "hasChildren", ignore = true)
    CatalogProductResponse toResponse(Product product);

    @Named("flatCategory")
    default CategoryResponseDTO flatCategory(Category category) {
        if (category == null) {
            return null;
        }
        return CategoryResponseDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .parentId(category.getParentId())
                .slug(category.getSlug())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .usesPacks(category.getUsesPacks())
                .children(Collections.emptyList())
                .build();
    }

    @Named("safePreferredSupplierName")
    default String safePreferredSupplierName(Product product) {
        if (product.getPreferredSupplier() == null || !Hibernate.isInitialized(product.getPreferredSupplier())) {
            return null;
        }
        return product.getPreferredSupplier().getDisplayName();
    }
}
