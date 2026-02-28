package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.CategoryInUseException;
import com.mirai.inventoryservice.exceptions.CategoryNotFoundException;
import com.mirai.inventoryservice.exceptions.DuplicateCategoryException;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.repositories.CategoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class CategoryService {
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * Create a root category (no parent)
     */
    public Category createCategory(String name, Integer displayOrder) {
        return createCategory(name, null, displayOrder);
    }

    /**
     * Create a category with optional parent (subcategory if parent specified)
     */
    public Category createCategory(String name, UUID parentId, Integer displayOrder) {
        String slug = slugify(name);

        // Validate slug uniqueness within same parent scope
        Category parent = null;
        if (parentId == null) {
            if (categoryRepository.existsBySlugAndParentIsNull(slug)) {
                throw new DuplicateCategoryException("Category with this name already exists");
            }
        } else {
            // Verify parent exists (load once and reuse)
            parent = getCategoryById(parentId);
            if (categoryRepository.existsBySlugAndParentId(slug, parentId)) {
                throw new DuplicateCategoryException("Subcategory with this name already exists in this category");
            }
        }

        Category category = Category.builder()
                .name(name.trim())
                .slug(slug)
                .parent(parent)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .isActive(true)
                .build();

        return categoryRepository.save(category);
    }

    public Category getCategoryById(UUID id) {
        return categoryRepository.findByIdWithParent(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + id));
    }

    /**
     * Get category by ID with children eagerly loaded (for when nested data is needed)
     */
    public Category getCategoryByIdWithChildren(UUID id) {
        return categoryRepository.findByIdWithChildren(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + id));
    }

    /**
     * Get root categories only (for top-level category dropdown)
     */
    public List<Category> getRootCategories() {
        return categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();
    }

    /**
     * Get children of a category (for subcategory dropdown)
     */
    public List<Category> getChildCategories(UUID parentId) {
        return categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(parentId);
    }

    /**
     * Get root categories with their children eagerly loaded
     */
    public List<Category> getRootCategoriesWithChildren() {
        return categoryRepository.findRootCategoriesWithChildren();
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category updateCategory(UUID id, String name, Integer displayOrder) {
        Category category = getCategoryById(id);

        if (name != null && !name.trim().equals(category.getName())) {
            String newSlug = slugify(name);

            // Check uniqueness within same parent scope
            boolean slugExists;
            if (category.getParentId() == null) {
                slugExists = categoryRepository.existsBySlugAndParentIsNull(newSlug);
            } else {
                slugExists = categoryRepository.existsBySlugAndParentId(newSlug, category.getParentId());
            }

            if (slugExists && !newSlug.equals(category.getSlug())) {
                throw new DuplicateCategoryException("Category with this name already exists");
            }
            category.setName(name.trim());
            category.setSlug(newSlug);
        }

        if (displayOrder != null) {
            category.setDisplayOrder(displayOrder);
        }

        return categoryRepository.save(category);
    }

    public void deleteCategory(UUID id) {
        Category category = getCategoryById(id);

        // Check for products using this category
        long productCount = categoryRepository.countProductsByCategoryId(id);
        if (productCount > 0) {
            throw new CategoryInUseException(
                "Cannot delete category with existing products. " + productCount + " product(s) use this category."
            );
        }

        // Check for child categories
        long childCount = categoryRepository.countChildrenByParentId(id);
        if (childCount > 0) {
            throw new CategoryInUseException(
                "Cannot delete category with subcategories. " + childCount + " subcategory(ies) exist."
            );
        }

        categoryRepository.delete(category);
    }

    public void deactivateCategory(UUID id) {
        Category category = getCategoryById(id);
        category.setIsActive(false);
        categoryRepository.save(category);
    }

    public void activateCategory(UUID id) {
        Category category = getCategoryById(id);
        category.setIsActive(true);
        categoryRepository.save(category);
    }

    private String slugify(String input) {
        String noWhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }
}
