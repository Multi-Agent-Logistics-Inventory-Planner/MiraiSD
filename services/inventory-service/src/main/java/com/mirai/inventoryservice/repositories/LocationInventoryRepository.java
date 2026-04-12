package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.LocationInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationInventoryRepository extends JpaRepository<LocationInventory, UUID> {
    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl JOIN FETCH li.product WHERE li.location.id = :locationId")
    List<LocationInventory> findByLocation_Id(@Param("locationId") UUID locationId);

    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl WHERE li.product.id = :productId")
    List<LocationInventory> findByProduct_Id(@Param("productId") UUID productId);

    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl JOIN FETCH li.product WHERE li.location.id = :locationId AND li.product.id = :productId")
    Optional<LocationInventory> findByLocation_IdAndProduct_Id(@Param("locationId") UUID locationId, @Param("productId") UUID productId);

    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl JOIN FETCH li.product WHERE li.site.id = :siteId")
    List<LocationInventory> findBySite_Id(@Param("siteId") UUID siteId);

    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl JOIN FETCH li.product WHERE sl.id = :storageLocationId")
    List<LocationInventory> findByStorageLocation_Id(@Param("storageLocationId") UUID storageLocationId);

    @Query("SELECT SUM(li.quantity) FROM LocationInventory li WHERE li.product.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);

    @Query("SELECT SUM(li.quantity) FROM LocationInventory li WHERE li.product.id = :productId AND li.site.id = :siteId")
    Integer sumQuantityByProductIdAndSiteId(@Param("productId") UUID productId, @Param("siteId") UUID siteId);

    void deleteByProduct_Id(UUID productId);

    // Batch delete all inventory records for multiple products (optimized for N+1 prevention)
    @Modifying
    @Query("DELETE FROM LocationInventory li WHERE li.product.id IN :productIds")
    void deleteAllByProductIdIn(@Param("productIds") Collection<UUID> productIds);

    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl JOIN FETCH li.product WHERE sl.code = :storageLocationCode AND li.site.id = :siteId")
    List<LocationInventory> findByStorageLocationCodeAndSiteId(@Param("storageLocationCode") String storageLocationCode, @Param("siteId") UUID siteId);
}
