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
    /**
     * Lists displayable inventory at a location. Excludes:
     *  - child products (parent IS NOT NULL): kuji prize children, PREMADE (tracking-only)
     *    and CUSTOM (managed in the dedicated kuji UI), never appear as stockable rack items.
     *  - CUSTOM kuji parents: their lifecycle lives on KujiBox; convention is no
     *    location_inventory row, but the filter defends against stray ones.
     */
    @Query("""
        SELECT li FROM LocationInventory li
        JOIN FETCH li.location l
        JOIN FETCH l.storageLocation sl
        JOIN FETCH li.product p
        WHERE li.location.id = :locationId
          AND p.parent IS NULL
          AND (p.kujiType IS NULL OR p.kujiType <> com.mirai.inventoryservice.models.enums.KujiType.CUSTOM)
        """)
    List<LocationInventory> findByLocation_Id(@Param("locationId") UUID locationId);

    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl WHERE li.product.id = :productId")
    List<LocationInventory> findByProduct_Id(@Param("productId") UUID productId);

    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl JOIN FETCH li.product WHERE li.location.id = :locationId AND li.product.id = :productId")
    Optional<LocationInventory> findByLocation_IdAndProduct_Id(@Param("locationId") UUID locationId, @Param("productId") UUID productId);

    @Query("SELECT li FROM LocationInventory li WHERE li.location.id = :locationId AND li.product.id IN :productIds")
    List<LocationInventory> findByLocation_IdAndProduct_IdIn(@Param("locationId") UUID locationId, @Param("productIds") Collection<UUID> productIds);

    /**
     * Batch fetch LocationInventory rows by id with all associations needed for
     * stock-movement bookkeeping eager-loaded (location, storage location, product,
     * product.parent). Used by batch-adjust and batch-transfer to avoid N+1 lazy
     * fetches during the validation + outbox-event loops.
     */
    @Query("""
        SELECT li FROM LocationInventory li
        JOIN FETCH li.location l
        JOIN FETCH l.storageLocation sl
        JOIN FETCH li.product p
        LEFT JOIN FETCH p.parent
        WHERE li.id IN :ids
        """)
    List<LocationInventory> findAllByIdWithGraph(@Param("ids") Collection<UUID> ids);

    /**
     * Sum on-hand quantity grouped by product id for the given products in one query.
     * Returns rows of [productId UUID, totalQuantity Long]; missing products mean total = 0.
     */
    @Query("""
        SELECT li.product.id, SUM(li.quantity)
        FROM LocationInventory li
        WHERE li.product.id IN :productIds
        GROUP BY li.product.id
        """)
    List<Object[]> sumQuantitiesByProductIds(@Param("productIds") Collection<UUID> productIds);

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
