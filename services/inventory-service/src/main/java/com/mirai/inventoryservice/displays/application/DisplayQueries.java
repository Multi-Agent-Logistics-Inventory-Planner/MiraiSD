package com.mirai.inventoryservice.displays.application;

import com.mirai.inventoryservice.displays.domain.MachineDisplay;
import com.mirai.inventoryservice.displays.infrastructure.MachineDisplayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only facade for display data consumed outside {@code displays}, so callers never touch
 * {@link MachineDisplayRepository} or the {@link MachineDisplay} entity directly. Global/unscoped
 * for now, matching every current caller; site-scoped variants belong to the analytics slice.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisplayQueries {

    private final MachineDisplayRepository machineDisplayRepository;

    /**
     * Total display days per product within a date range, handling partial period overlaps.
     */
    public Map<UUID, Double> displayDaysByProduct(OffsetDateTime startDate, OffsetDateTime endDate) {
        return machineDisplayRepository.calculateDisplayDaysByProduct(startDate, endDate).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).doubleValue()));
    }

    /**
     * Whether a product currently has any active (not ended) display.
     */
    public boolean isProductOnDisplay(UUID productId) {
        return machineDisplayRepository.existsByProduct_IdAndEndedAtIsNull(productId);
    }

    /**
     * Currently-active displays for a product.
     */
    public List<ActiveDisplaySummary> findActiveByProduct(UUID productId) {
        return machineDisplayRepository.findActiveByProduct_Id(productId).stream()
                .map(md -> new ActiveDisplaySummary(
                        md.getId(),
                        md.getLocation() != null ? md.getLocation().getId() : null,
                        md.getLocationType(),
                        md.getMachineId(),
                        md.getStartedAt()))
                .toList();
    }
}
