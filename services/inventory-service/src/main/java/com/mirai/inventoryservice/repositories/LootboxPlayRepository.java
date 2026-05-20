package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.lootbox.LootboxPlay;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LootboxPlayRepository extends JpaRepository<LootboxPlay, UUID> {

    @Query("SELECT COALESCE(SUM(p.cost), 0) FROM LootboxPlay p WHERE p.user.id = :userId")
    long sumCostByUserId(@Param("userId") UUID userId);

    List<LootboxPlay> findByUserIdOrderByPlayedAtDesc(UUID userId);

    Optional<LootboxPlay> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Page<LootboxPlay> findByStatusOrderByPlayedAtDesc(String status, Pageable pageable);
}
