package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    // Find all movements for a specific product, newest first
    List<StockMovement> findByItem_IdOrderByAtDesc(UUID productId);

    // Paginated version for large histories
    Page<StockMovement> findByItem_IdOrderByAtDesc(UUID productId, Pageable pageable);

    // Filter by location type
    List<StockMovement> findByItem_IdAndLocationTypeOrderByAtDesc(UUID productId, LocationType locationType);

    // Find recent movements (last 30 days, etc)
    List<StockMovement> findByItem_IdAndAtAfterOrderByAtDesc(UUID productId, OffsetDateTime since);
}

