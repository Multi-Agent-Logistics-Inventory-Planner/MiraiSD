package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.kuji.KujiBoxTier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KujiBoxTierRepository extends JpaRepository<KujiBoxTier, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM KujiBoxTier t WHERE t.id = :id")
    Optional<KujiBoxTier> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM KujiBoxTier t WHERE t.box.product.id = :productId")
    int deleteByBoxProductId(@Param("productId") UUID productId);

    /**
     * Tiers from OPEN boxes at a given location, with linked product set.
     * Includes tiers with any active or inactive slips. Caller projects to DTOs.
     */
    @Query("""
            SELECT t FROM KujiBoxTier t
            JOIN FETCH t.box b
            JOIN FETCH t.linkedProduct p
            LEFT JOIN FETCH b.machineDisplay md
            WHERE b.status = com.mirai.inventoryservice.models.enums.KujiBoxStatus.OPEN
              AND b.location.id = :locationId
              AND t.linkedProduct IS NOT NULL
              AND (t.activeCount + t.inactiveCount) > 0
            """)
    List<KujiBoxTier> findOpenAllocationsByLocation(@Param("locationId") UUID locationId);

    /**
     * Tiers from OPEN boxes whose linkedProduct matches; carries box + machineDisplay.
     */
    @Query("""
            SELECT t FROM KujiBoxTier t
            JOIN FETCH t.box b
            JOIN FETCH b.location loc
            LEFT JOIN FETCH b.machineDisplay md
            WHERE b.status = com.mirai.inventoryservice.models.enums.KujiBoxStatus.OPEN
              AND t.linkedProduct.id = :productId
              AND (t.activeCount + t.inactiveCount) > 0
            """)
    List<KujiBoxTier> findOpenAllocationsByProduct(@Param("productId") UUID productId);
}
