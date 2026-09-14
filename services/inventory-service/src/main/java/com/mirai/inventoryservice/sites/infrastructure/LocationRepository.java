package com.mirai.inventoryservice.sites.infrastructure;

import com.mirai.inventoryservice.sites.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    @Query("SELECT l FROM Location l JOIN FETCH l.storageLocation sl JOIN FETCH sl.site WHERE l.storageLocation.id = :storageLocationId")
    List<Location> findByStorageLocation_Id(@Param("storageLocationId") UUID storageLocationId);

    @Query("SELECT l FROM Location l JOIN FETCH l.storageLocation sl JOIN FETCH sl.site WHERE l.locationCode = :locationCode AND l.storageLocation.id = :storageLocationId")
    Optional<Location> findByLocationCodeAndStorageLocation_Id(@Param("locationCode") String locationCode, @Param("storageLocationId") UUID storageLocationId);

    @Query("SELECT l FROM Location l JOIN FETCH l.storageLocation sl JOIN FETCH sl.site WHERE l.locationCode = :locationCode AND sl.code = :storageLocationCode AND sl.site.id = :siteId")
    Optional<Location> findByLocationCodeAndStorageLocationCodeAndSiteId(
            @Param("locationCode") String locationCode,
            @Param("storageLocationCode") String storageLocationCode,
            @Param("siteId") UUID siteId);

    boolean existsByLocationCodeAndStorageLocation_Id(String locationCode, UUID storageLocationId);

    @Query("SELECT l FROM Location l JOIN FETCH l.storageLocation sl JOIN FETCH sl.site WHERE sl.site.id = :siteId")
    List<Location> findBySite_Id(@Param("siteId") UUID siteId);

    @Query("SELECT l FROM Location l JOIN FETCH l.storageLocation sl JOIN FETCH sl.site WHERE l.id = :id AND sl.site.id = :siteId")
    Optional<Location> findByIdAndSite_Id(@Param("id") UUID id, @Param("siteId") UUID siteId);

    @Query("SELECT l FROM Location l JOIN FETCH l.storageLocation sl JOIN FETCH sl.site WHERE sl.code = :storageLocationCode AND sl.site.id = :siteId")
    List<Location> findByStorageLocationCodeAndSiteId(@Param("storageLocationCode") String storageLocationCode, @Param("siteId") UUID siteId);

    /**
     * Scalar-only lookup of a location's site id, for planning/creating a
     * {@code location_inventory} row before any entity is loaded (.specs/phase-6-inventory 6c,
     * review-driven fix round 2: T-6c-6 P1 findings, {@code
     * StockMovementService.ensureAndLockInventoryRow}). A location's storage location -- and
     * therefore its site -- is immutable once set, so reading it unlocked here is safe.
     */
    @Query("SELECT l.storageLocation.site.id FROM Location l WHERE l.id = :id")
    Optional<UUID> findSiteIdById(@Param("id") UUID id);
}
