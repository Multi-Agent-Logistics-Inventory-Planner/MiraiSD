package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.enums.KujiBoxStatus;
import com.mirai.inventoryservice.models.kuji.KujiBox;
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

    @Query("SELECT b FROM KujiBox b WHERE b.machineDisplay.id = :machineDisplayId AND b.status = :status")
    Optional<KujiBox> findByMachineDisplayIdAndStatus(@Param("machineDisplayId") UUID machineDisplayId,
                                                     @Param("status") KujiBoxStatus status);

    @Query("SELECT b.product.id FROM KujiBox b WHERE b.status = :status")
    List<UUID> findProductIdsWithStatus(@Param("status") KujiBoxStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM KujiBox b WHERE b.product.id = :productId")
    int deleteByProductId(@Param("productId") UUID productId);
}
