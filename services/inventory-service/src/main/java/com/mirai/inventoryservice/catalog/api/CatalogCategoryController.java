package com.mirai.inventoryservice.catalog.api;

import com.mirai.inventoryservice.catalog.application.CategoryService;
import com.mirai.inventoryservice.catalog.domain.Category;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Global catalog v1 category routes. Categories are wholly global (no site scope), so this
 * mirrors {@link CategoryController} 1:1 under the {@code /api/v1/catalog} prefix, with distinct
 * handler and operation names so springdoc doesn't collide the legacy operation IDs. The legacy
 * {@code /api/categories} routes are untouched.
 */
@RestController
@RequestMapping("/api/v1/catalog/categories")
public class CatalogCategoryController {
    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    public CatalogCategoryController(CategoryService categoryService, CategoryMapper categoryMapper) {
        this.categoryService = categoryService;
        this.categoryMapper = categoryMapper;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getCatalogCategories() {
        List<Category> categories = categoryService.getRootCategoriesWithChildren();
        return ResponseEntity.ok(categoryMapper.toResponseDTOList(categories));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> getCatalogCategoryById(@PathVariable UUID id) {
        Category category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(categoryMapper.toResponseDTOFlat(category));
    }

    @GetMapping("/{parentId}/children")
    public ResponseEntity<List<CategoryResponseDTO>> getCatalogChildCategories(@PathVariable UUID parentId) {
        List<Category> children = categoryService.getChildCategories(parentId);
        return ResponseEntity.ok(categoryMapper.toResponseDTOListFlat(children));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<CategoryResponseDTO> createCatalogCategory(@Valid @RequestBody CategoryRequestDTO requestDTO) {
        Category category = categoryService.createCategory(
                requestDTO.getName(),
                requestDTO.getParentId(),
                requestDTO.getDisplayOrder(),
                requestDTO.getUsesPacks());
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryMapper.toResponseDTOFlat(category));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<CategoryResponseDTO> updateCatalogCategory(
            @PathVariable UUID id, @Valid @RequestBody CategoryRequestDTO requestDTO) {
        Category category = categoryService.updateCategory(
                id, requestDTO.getName(), requestDTO.getDisplayOrder(), requestDTO.getUsesPacks());
        return ResponseEntity.ok(categoryMapper.toResponseDTOFlat(category));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteCatalogCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deactivateCatalogCategory(@PathVariable UUID id) {
        categoryService.deactivateCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> activateCatalogCategory(@PathVariable UUID id) {
        categoryService.activateCategory(id);
        return ResponseEntity.noContent().build();
    }
}
