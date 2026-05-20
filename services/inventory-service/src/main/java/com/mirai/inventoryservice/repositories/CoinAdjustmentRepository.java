package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.lootbox.CoinAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CoinAdjustmentRepository extends JpaRepository<CoinAdjustment, UUID> {

    @Query("SELECT COALESCE(SUM(a.delta), 0) FROM CoinAdjustment a WHERE a.user.id = :userId")
    long sumDeltaByUserId(@Param("userId") UUID userId);

    List<CoinAdjustment> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
