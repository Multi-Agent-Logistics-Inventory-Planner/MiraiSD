package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.MachineDisplay;
import com.mirai.inventoryservice.models.enums.LocationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MachineDisplayRepository extends JpaRepository<MachineDisplay, UUID> {

    /**
     * Find a display by ID with its product eagerly loaded (avoids lazy-load extra query).
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    @Query("SELECT md FROM MachineDisplay md WHERE md.id = :id")
    Optional<MachineDisplay> findByIdWithProduct(@Param("id") UUID id);

    /**
     * Find the current active display for a machine (where ended_at is null)
     * @deprecated Use findActiveByLocationTypeAndMachineId for multiple products support
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    Optional<MachineDisplay> findByLocationTypeAndMachineIdAndEndedAtIsNull(
            LocationType locationType, UUID machineId);

    /**
     * Find all active displays for a machine (supports multiple products)
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    @Query("SELECT md FROM MachineDisplay md WHERE md.locationType = :locationType AND md.machineId = :machineId AND md.endedAt IS NULL ORDER BY md.startedAt ASC")
    List<MachineDisplay> findActiveByLocationTypeAndMachineId(
            @Param("locationType") LocationType locationType,
            @Param("machineId") UUID machineId);

    /**
     * Find all active displays (current state across all machines)
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    List<MachineDisplay> findByEndedAtIsNullOrderByStartedAtAsc();

    /**
     * Find all active displays with pagination
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    Page<MachineDisplay> findByEndedAtIsNull(Pageable pageable);

    /**
     * Find active displays for a specific location type
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    List<MachineDisplay> findByLocationTypeAndEndedAtIsNullOrderByStartedAtAsc(LocationType locationType);

    /**
     * Find display history for a specific machine
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    List<MachineDisplay> findByLocationTypeAndMachineIdOrderByStartedAtDesc(
            LocationType locationType, UUID machineId);

    /**
     * Find display history for a specific machine with pagination
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    Page<MachineDisplay> findByLocationTypeAndMachineIdOrderByStartedAtDesc(
            LocationType locationType, UUID machineId, Pageable pageable);

    /**
     * Find display history for a specific product
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    List<MachineDisplay> findByProduct_IdOrderByStartedAtDesc(UUID productId);

    /**
     * Find stale displays (active for longer than threshold)
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    @Query("SELECT md FROM MachineDisplay md WHERE md.endedAt IS NULL AND md.startedAt < :threshold ORDER BY md.startedAt ASC")
    List<MachineDisplay> findStaleDisplays(@Param("threshold") OffsetDateTime threshold);

    /**
     * Find stale displays for a specific location type
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    @Query("SELECT md FROM MachineDisplay md WHERE md.locationType = :locationType AND md.endedAt IS NULL AND md.startedAt < :threshold ORDER BY md.startedAt ASC")
    List<MachineDisplay> findStaleDisplaysByLocationType(
            @Param("locationType") LocationType locationType,
            @Param("threshold") OffsetDateTime threshold);

    /**
     * Count active displays by location type
     */
    @Query("SELECT md.locationType, COUNT(md) FROM MachineDisplay md WHERE md.endedAt IS NULL GROUP BY md.locationType")
    List<Object[]> countActiveDisplaysByLocationType();

    /**
     * Delete all display records for a product (e.g. when deleting the product).
     */
    void deleteByProduct_Id(UUID productId);

    // ========= New methods using unified location_id FK =========

    /**
     * Find all active displays for a location (unified approach)
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    @Query("SELECT md FROM MachineDisplay md WHERE md.location.id = :locationId AND md.endedAt IS NULL ORDER BY md.startedAt ASC")
    List<MachineDisplay> findActiveByLocationId(@Param("locationId") UUID locationId);

    /**
     * Find display history for a location (unified approach)
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    List<MachineDisplay> findByLocation_IdOrderByStartedAtDesc(UUID locationId);

    /**
     * Find display history for a location with pagination (unified approach)
     */
    @EntityGraph(value = "MachineDisplay.withProduct")
    Page<MachineDisplay> findByLocation_IdOrderByStartedAtDesc(UUID locationId, Pageable pageable);

    // ========= Analytics methods =========

    /**
     * Calculate total display days per product within a date range.
     * Handles partial overlaps with period boundaries.
     * Returns [product_id, display_days] pairs.
     */
    @Query(value = """
        SELECT md.product_id,
               SUM(
                   GREATEST(0,
                       EXTRACT(EPOCH FROM (
                           LEAST(COALESCE(md.ended_at, :endDate), :endDate) -
                           GREATEST(md.started_at, :startDate)
                       )) / 86400.0
                   )
               ) as display_days
        FROM machine_display md
        WHERE md.started_at < :endDate
          AND (md.ended_at IS NULL OR md.ended_at > :startDate)
        GROUP BY md.product_id
        """, nativeQuery = true)
    List<Object[]> calculateDisplayDaysByProduct(
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate);
}
