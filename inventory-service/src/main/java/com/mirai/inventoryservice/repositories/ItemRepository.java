package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.Item;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {
    Optional<Item> findBySku(String sku);
    
    List<Item> findByCategory(ProductCategory category);
    
    List<Item> findByIsActiveTrue();
    
    Page<Item> findByIsActiveTrue(Pageable pageable);
    
    Page<Item> findByCategory(ProductCategory category, Pageable pageable);
    
    Page<Item> findByCategoryAndIsActiveTrue(ProductCategory category, Pageable pageable);
    
    Optional<Item> findByCategoryAndNameAndDescription(
        ProductCategory category, 
        String name, 
        String description
    );
}

