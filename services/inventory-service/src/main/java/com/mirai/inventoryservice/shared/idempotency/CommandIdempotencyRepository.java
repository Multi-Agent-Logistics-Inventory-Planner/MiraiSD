package com.mirai.inventoryservice.shared.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommandIdempotencyRepository extends JpaRepository<CommandIdempotency, UUID> {

    Optional<CommandIdempotency> findBySiteIdAndUserIdAndIdempotencyKey(
            UUID siteId, UUID userId, String idempotencyKey);

    @Modifying
    @Query("DELETE FROM CommandIdempotency c WHERE c.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
