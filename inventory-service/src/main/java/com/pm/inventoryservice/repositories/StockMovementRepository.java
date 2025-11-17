package com.pm.inventoryservice.repositories;

import com.pm.inventoryservice.models.StockMovement;
import com.pm.inventoryservice.models.enums.LocationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    // Find all movements for a specific inventory item, newest first
    List<StockMovement> findByItemIdOrderByAtDesc(UUID itemId);
    // Paginated version for large histories
    Page<StockMovement> findByItemIdOrderByAtDesc(UUID itemId, Pageable pageable);
    //Filter by location type
    List<StockMovement> findByItemIdAndLocationTypeOrderByAtDesc(UUID itemId, LocationType locationType);
    // Find recent movements (last 30 days, etc)
    List<StockMovement> findByItemIdAndAtAfterOrderByAtDesc(UUID itemId, OffsetDateTime since);
}
