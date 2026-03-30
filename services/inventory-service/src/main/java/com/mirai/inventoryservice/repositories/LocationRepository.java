package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.storage.Location;
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

    @Query("SELECT l FROM Location l JOIN FETCH l.storageLocation sl JOIN FETCH sl.site WHERE sl.code = :storageLocationCode AND sl.site.id = :siteId")
    List<Location> findByStorageLocationCodeAndSiteId(@Param("storageLocationCode") String storageLocationCode, @Param("siteId") UUID siteId);
}
