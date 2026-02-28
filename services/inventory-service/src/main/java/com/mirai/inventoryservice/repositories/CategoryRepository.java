package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // Find root categories (no parent)
    List<Category> findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();

    List<Category> findByParentIsNullOrderByDisplayOrderAsc();

    // Find children of a parent category
    List<Category> findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID parentId);

    List<Category> findByParentIdOrderByDisplayOrderAsc(UUID parentId);

    // Find by slug (for root categories)
    Optional<Category> findBySlugAndParentIsNull(String slug);

    // Find by slug within a parent
    Optional<Category> findBySlugAndParentId(String slug, UUID parentId);

    // Check existence for uniqueness validation
    boolean existsBySlugAndParentIsNull(String slug);

    boolean existsBySlugAndParentId(String slug, UUID parentId);

    // Count products using this category
    @Query("SELECT COUNT(p) FROM Product p WHERE p.category.id = :categoryId")
    long countProductsByCategoryId(@Param("categoryId") UUID categoryId);

    // Count children of a category
    @Query("SELECT COUNT(c) FROM Category c WHERE c.parent.id = :parentId")
    long countChildrenByParentId(@Param("parentId") UUID parentId);

    // Fetch category with parent eagerly loaded
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.id = :id")
    Optional<Category> findByIdWithParent(@Param("id") UUID id);

    // Fetch all active root categories with their children
    @Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.children WHERE c.parent IS NULL AND c.isActive = true ORDER BY c.displayOrder")
    List<Category> findRootCategoriesWithChildren();

    // Fetch category with children eagerly loaded
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.children WHERE c.id = :id")
    Optional<Category> findByIdWithChildren(@Param("id") UUID id);

    // Fetch category with both parent and children eagerly loaded
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent LEFT JOIN FETCH c.children WHERE c.id = :id")
    Optional<Category> findByIdWithParentAndChildren(@Param("id") UUID id);
}
