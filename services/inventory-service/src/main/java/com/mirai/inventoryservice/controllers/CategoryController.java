package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.CategoryMapper;
import com.mirai.inventoryservice.dtos.requests.CategoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.CategoryResponseDTO;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.services.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    public CategoryController(
            CategoryService categoryService,
            CategoryMapper categoryMapper) {
        this.categoryService = categoryService;
        this.categoryMapper = categoryMapper;
    }

    /**
     * Get root categories with their children (subcategories)
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getCategories() {
        List<Category> categories = categoryService.getRootCategoriesWithChildren();
        return ResponseEntity.ok(categoryMapper.toResponseDTOList(categories));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> getCategoryById(@PathVariable UUID id) {
        // Use flat DTO since single category doesn't need nested children
        // Children can be fetched via /{id}/children endpoint if needed
        Category category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(categoryMapper.toResponseDTOFlat(category));
    }

    /**
     * Get children (subcategories) of a specific category
     */
    @GetMapping("/{parentId}/children")
    public ResponseEntity<List<CategoryResponseDTO>> getChildCategories(@PathVariable UUID parentId) {
        List<Category> children = categoryService.getChildCategories(parentId);
        return ResponseEntity.ok(categoryMapper.toResponseDTOListFlat(children));
    }

    /**
     * Create a root category
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<CategoryResponseDTO> createCategory(@Valid @RequestBody CategoryRequestDTO requestDTO) {
        Category category = categoryService.createCategory(
                requestDTO.getName(),
                requestDTO.getParentId(),
                requestDTO.getDisplayOrder()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryMapper.toResponseDTOFlat(category));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<CategoryResponseDTO> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequestDTO requestDTO) {
        Category category = categoryService.updateCategory(
                id,
                requestDTO.getName(),
                requestDTO.getDisplayOrder()
        );
        // Use flat DTO - updated category doesn't need nested children
        return ResponseEntity.ok(categoryMapper.toResponseDTOFlat(category));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deactivateCategory(@PathVariable UUID id) {
        categoryService.deactivateCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> activateCategory(@PathVariable UUID id) {
        categoryService.activateCategory(id);
        return ResponseEntity.noContent().build();
    }
}
