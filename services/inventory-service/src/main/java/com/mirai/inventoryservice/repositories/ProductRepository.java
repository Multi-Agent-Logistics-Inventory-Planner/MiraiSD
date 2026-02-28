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
}
