package com.mirai.inventoryservice.kuji.infrastructure;

import com.mirai.inventoryservice.models.enums.KujiBoxStatus;
import com.mirai.inventoryservice.kuji.domain.KujiBox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KujiBoxRepository extends JpaRepository<KujiBox, UUID> {

    @Query("SELECT b FROM KujiBox b LEFT JOIN FETCH b.tiers t LEFT JOIN FETCH t.linkedProduct " +
            "WHERE b.product.id = :productId AND b.status = :status")
    Optional<KujiBox> findByProductIdAndStatusWithTiers(@Param("productId") UUID productId,
                                                       @Param("status") KujiBoxStatus status);

    Optional<KujiBox> findByProductIdAndStatus(UUID productId, KujiBoxStatus status);

    @Query("SELECT b FROM KujiBox b LEFT JOIN FETCH b.tiers t LEFT JOIN FETCH t.linkedProduct " +
            "WHERE b.id = :id")
    Optional<KujiBox> findByIdWithTiers(@Param("id") UUID id);

    @Query("SELECT b FROM KujiBox b WHERE b.product.id = :productId ORDER BY b.openedAt DESC")
    List<KujiBox> findByProductIdOrderByOpenedAtDesc(@Param("productId") UUID productId);

    /** Site-scoped variants added for .specs/phase-7-kuji-lootbox T-3 (AC-2). */
    @Query("SELECT b FROM KujiBox b LEFT JOIN FETCH b.tiers t LEFT JOIN FETCH t.linkedProduct " +
            "WHERE b.product.id = :productId AND b.siteId = :siteId AND b.status = :status")
    Optional<KujiBox> findByProductIdAndSiteIdAndStatusWithTiers(@Param("productId") UUID productId,
                                                                  @Param("siteId") UUID siteId,
                                                                  @Param("status") KujiBoxStatus status);

    Optional<KujiBox> findByProductIdAndSiteIdAndStatus(UUID productId, UUID siteId, KujiBoxStatus status);

    @Query("SELECT b FROM KujiBox b LEFT JOIN FETCH b.tiers t LEFT JOIN FETCH t.linkedProduct " +
            "WHERE b.id = :id AND b.siteId = :siteId")
    Optional<KujiBox> findByIdAndSiteIdWithTiers(@Param("id") UUID id, @Param("siteId") UUID siteId);

    Optional<KujiBox> findByIdAndSiteId(UUID id, UUID siteId);

    @Query("SELECT b FROM KujiBox b WHERE b.product.id = :productId AND b.siteId = :siteId ORDER BY b.openedAt DESC")
    List<KujiBox> findByProductIdAndSiteIdOrderByOpenedAtDesc(@Param("productId") UUID productId,
                                                               @Param("siteId") UUID siteId);

    @Query("SELECT b FROM KujiBox b WHERE b.machineDisplay.id = :machineDisplayId AND b.status = :status")
    Optional<KujiBox> findByMachineDisplayIdAndStatus(@Param("machineDisplayId") UUID machineDisplayId,
                                                     @Param("status") KujiBoxStatus status);

    @Query("SELECT b.product.id FROM KujiBox b WHERE b.status = :status")
    List<UUID> findProductIdsWithStatus(@Param("status") KujiBoxStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM KujiBox b WHERE b.product.id = :productId")
    int deleteByProductId(@Param("productId") UUID productId);
}
