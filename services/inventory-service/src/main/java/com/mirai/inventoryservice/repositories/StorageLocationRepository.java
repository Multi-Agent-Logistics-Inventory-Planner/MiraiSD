package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.storage.StorageLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StorageLocationRepository extends JpaRepository<StorageLocation, UUID> {
    @Query("SELECT sl FROM StorageLocation sl JOIN FETCH sl.site WHERE sl.site.id = :siteId ORDER BY sl.displayOrder")
    List<StorageLocation> findBySite_IdOrderByDisplayOrder(@Param("siteId") UUID siteId);

    @Query("SELECT sl FROM StorageLocation sl JOIN FETCH sl.site WHERE sl.site.code = :siteCode ORDER BY sl.displayOrder")
    List<StorageLocation> findBySite_CodeOrderByDisplayOrder(@Param("siteCode") String siteCode);

    @Query("SELECT sl FROM StorageLocation sl JOIN FETCH sl.site WHERE sl.code = :code AND sl.site.id = :siteId")
    Optional<StorageLocation> findByCodeAndSite_Id(@Param("code") String code, @Param("siteId") UUID siteId);

    @Query("SELECT sl FROM StorageLocation sl JOIN FETCH sl.site WHERE sl.code = :code AND sl.site.code = :siteCode")
    Optional<StorageLocation> findByCodeAndSite_Code(@Param("code") String code, @Param("siteCode") String siteCode);

    boolean existsByCodeAndSite_Id(String code, UUID siteId);

    @Query("SELECT sl FROM StorageLocation sl JOIN FETCH sl.site WHERE sl.hasDisplay = true AND sl.site.id = :siteId ORDER BY sl.displayOrder")
    List<StorageLocation> findDisplayLocationsBySite_Id(@Param("siteId") UUID siteId);

    @Query("SELECT sl FROM StorageLocation sl JOIN FETCH sl.site WHERE sl.isDisplayOnly = false AND sl.site.id = :siteId ORDER BY sl.displayOrder")
    List<StorageLocation> findInventoryLocationsBySite_Id(@Param("siteId") UUID siteId);
}
