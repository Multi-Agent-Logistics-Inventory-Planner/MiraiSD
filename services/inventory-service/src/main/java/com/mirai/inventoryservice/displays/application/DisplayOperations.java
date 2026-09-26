package com.mirai.inventoryservice.displays.application;

import com.mirai.inventoryservice.catalog.application.CatalogEntityAccess;
import com.mirai.inventoryservice.displays.domain.MachineDisplay;
import com.mirai.inventoryservice.displays.infrastructure.MachineDisplayRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.sites.application.LocationService;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.LocationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Command facade for machine-display writes triggered from outside {@code displays} (today, only
 * kuji's auto-attach/detach on box open/close). Every method requires an existing caller
 * transaction (MANDATORY) so a display write and its caller's own write commit or roll back
 * together, matching the shape callers already relied on when they held the entity directly.
 *
 * <p>Intentionally does not write DISPLAY_SET/DISPLAY_REMOVE audit log or stock movement entries
 * the way {@link MachineDisplayService#setDisplay}/{@code clearDisplay} do -- that gap already
 * existed in kuji's direct-entity-access code and is preserved here as-is; closing it is a
 * product decision for the kuji slice, not something this move should do implicitly.
 */
@Service
@RequiredArgsConstructor
public class DisplayOperations {

    private final MachineDisplayRepository machineDisplayRepository;
    private final LocationService locationService;
    private final CatalogEntityAccess catalogEntityAccess;

    /**
     * Validates that a display exists and is still active, returning its id unchanged.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID requireActive(UUID displayId) {
        MachineDisplay display = machineDisplayRepository.findById(displayId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "MachineDisplay not found: " + displayId));
        if (display.getEndedAt() != null) {
            throw new IllegalArgumentException(
                    "MachineDisplay is no longer active: " + displayId);
        }
        return display.getId();
    }

    /**
     * Finds the active display for (machine, product) if one exists, else creates it. Site and
     * location type are derived from the machine's location, the same rule every other display
     * write path uses.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID attachProductAtMachine(UUID machineLocationId, UUID productId, UUID actorId, OffsetDateTime at) {
        Location location = locationService.findById(machineLocationId)
                .orElseThrow(() -> new LocationNotFoundException("Location not found: " + machineLocationId));
        LocationType locationType = mapLocationType(location);

        return machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(locationType, location.getId())
                .stream()
                .filter(d -> d.getProduct() != null && d.getProduct().getId().equals(productId))
                .findFirst()
                .map(MachineDisplay::getId)
                .orElseGet(() -> machineDisplayRepository.save(MachineDisplay.builder()
                        .location(location)
                        .locationType(locationType)
                        .machineId(location.getId())
                        .product(catalogEntityAccess.getReference(productId))
                        .startedAt(at)
                        .actorId(actorId)
                        .site(location.getStorageLocation().getSite())
                        .build()).getId());
    }

    /**
     * Ends a display if it is still active. Returns false if it was already ended (or missing).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean endIfActive(UUID displayId, OffsetDateTime at) {
        return machineDisplayRepository.findById(displayId)
                .filter(d -> d.getEndedAt() == null)
                .map(d -> {
                    d.setEndedAt(at);
                    machineDisplayRepository.save(d);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Transitional: exists only because {@code KujiBox.machineDisplay} is a {@code @ManyToOne}.
     * Remove once the kuji slice replaces that association with a UUID column.
     */
    public MachineDisplay getReference(UUID displayId) {
        return machineDisplayRepository.getReferenceById(displayId);
    }

    private LocationType mapLocationType(Location location) {
        if (location == null || location.getStorageLocation() == null) {
            return LocationType.NOT_ASSIGNED;
        }
        String code = location.getStorageLocation().getCode();
        return switch (code) {
            case "BOX_BINS" -> LocationType.BOX_BIN;
            case "RACKS" -> LocationType.RACK;
            case "CABINETS" -> LocationType.CABINET;
            case "SHELVES" -> LocationType.SHELF;
            case "WINDOWS" -> LocationType.WINDOW;
            case "SINGLE_CLAW" -> LocationType.SINGLE_CLAW_MACHINE;
            case "DOUBLE_CLAW" -> LocationType.DOUBLE_CLAW_MACHINE;
            case "FOUR_CORNER" -> LocationType.FOUR_CORNER_MACHINE;
            case "PUSHER" -> LocationType.PUSHER_MACHINE;
            case "GACHAPON" -> LocationType.GACHAPON;
            case "KEYCHAIN" -> LocationType.KEYCHAIN_MACHINE;
            case "NOT_ASSIGNED" -> LocationType.NOT_ASSIGNED;
            default -> LocationType.NOT_ASSIGNED;
        };
    }
}
