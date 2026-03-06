package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    // Optimized queries with JOIN FETCH to avoid N+1
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent")
    List<Product> findAllWithCategories();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.isActive = true")
    List<Product> findByIsActiveTrueWithCategories();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.category.id = :categoryId")
    List<Product> findByCategoryIdWithCategories(@Param("categoryId") UUID categoryId);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.category.id = :categoryId AND p.isActive = true")
    List<Product> findByCategoryIdAndIsActiveTrueWithCategories(@Param("categoryId") UUID categoryId);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.isActive = true AND " +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Product> searchWithCategories(@Param("query") String query);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.id = :id")
    Optional<Product> findByIdWithCategories(@Param("id") UUID id);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.sku = :sku")
    Optional<Product> findBySkuWithCategories(@Param("sku") String sku);

    // Keep original methods for cases where categories aren't needed
    List<Product> findByCategoryId(UUID categoryId);
    List<Product> findByIsActiveTrue();
    List<Product> findByCategoryIdAndIsActiveTrue(UUID categoryId);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND " +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Product> search(@Param("query") String query);

    // ==================== Parent-Child Methods ====================

    // Find root products only (no parent) - for main product list
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.parent IS NULL ORDER BY p.name")
    List<Product> findRootProductsWithCategories();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.parent IS NULL AND p.isActive = true ORDER BY p.name")
    List<Product> findRootProductsWithCategoriesActive();

    // Find children of a parent product
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.parent.id = :parentId ORDER BY p.sku")
    List<Product> findByParentIdWithCategories(@Param("parentId") UUID parentId);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.parent.id = :parentId AND p.isActive = true ORDER BY p.sku")
    List<Product> findByParentIdAndIsActiveTrueWithCategories(@Param("parentId") UUID parentId);

    // Count children of a product
    @Query("SELECT COUNT(p) FROM Product p WHERE p.parent.id = :parentId")
    long countChildrenByParentId(@Param("parentId") UUID parentId);

    // Fetch product with parent eagerly loaded
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.parent LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.id = :id")
    Optional<Product> findByIdWithParent(@Param("id") UUID id);

    // Fetch product with children eagerly loaded
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.children LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.id = :id")
    Optional<Product> findByIdWithChildren(@Param("id") UUID id);

    // Sum children quantities for aggregation
    @Query("SELECT COALESCE(SUM(p.quantity), 0) FROM Product p WHERE p.parent.id = :parentId")
    Integer sumChildrenQuantities(@Param("parentId") UUID parentId);
}
