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
}
