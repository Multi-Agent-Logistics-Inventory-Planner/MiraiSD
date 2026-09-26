package com.mirai.inventoryservice.displays.api;

import com.mirai.inventoryservice.displays.application.MachineDisplayService;
import com.mirai.inventoryservice.displays.domain.MachineDisplay;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Site-scoped machine-display routes (.specs/phase-7-displays T-4, AC-3/AC-5), reusing the
 * generic {@code /api/v1/sites/{siteId}/**} authorization filter -- {@code siteId} is still
 * declared on every handler so springdoc/the generated client expose it (matching
 * {@code shipments.api.SiteShipmentController}'s precedent), but the trusted value always comes
 * from {@link AuthorizedSiteContextHolder}. Every {@link MachineDisplayService} method this
 * delegates to was made site-parameterized in T-4; this controller adds no new business logic,
 * only a v1 route shape mirroring {@link MachineDisplayController}'s legacy one route-for-route.
 * Distinct handler names from {@link MachineDisplayController}'s so springdoc doesn't collide
 * operation IDs.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/machine-displays")
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class SiteMachineDisplayController {

    private final MachineDisplayService machineDisplayService;

    public SiteMachineDisplayController(MachineDisplayService machineDisplayService) {
        this.machineDisplayService = machineDisplayService;
    }

    private UUID contextSiteId() {
        return AuthorizedSiteContextHolder.require().siteId();
    }

    /**
     * The actor for every v1 mutation is always the authenticated principal, never a
     * client-supplied value: {@code SetMachineDisplayRequestDTO.actorId} and friends are
     * overwritten with this before the request reaches the service, and the {@code actorId}
     * query parameter the legacy routes accept is not exposed here at all.
     */
    private UUID contextActorId() {
        return AuthorizedSiteContextHolder.require().backendUserId();
    }

    @PostMapping
    public ResponseEntity<MachineDisplayDTO> setSiteDisplay(
            @PathVariable UUID siteId,
            @Valid @RequestBody SetMachineDisplayRequestDTO request) {
        UUID contextSiteId = contextSiteId();
        request.setActorId(contextActorId());
        MachineDisplay display = machineDisplayService.setDisplay(contextSiteId, request);
        MachineDisplayDTO dto = machineDisplayService.toResponseDTO(display);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<MachineDisplayDTO>> setSiteDisplayBatch(
            @PathVariable UUID siteId,
            @Valid @RequestBody SetMachineDisplayBatchRequestDTO request) {
        UUID contextSiteId = contextSiteId();
        request.setActorId(contextActorId());
        machineDisplayService.setDisplayBatch(contextSiteId, request);
        List<MachineDisplayDTO> dtos = machineDisplayService.getActiveDisplaysForMachine(
                contextSiteId, request.getLocationType(), request.getMachineId());
        return ResponseEntity.status(HttpStatus.CREATED).body(dtos);
    }

    @PostMapping("/swap")
    public ResponseEntity<List<MachineDisplayDTO>> swapSiteDisplay(
            @PathVariable UUID siteId,
            @Valid @RequestBody SwapMachineDisplayRequestDTO request) {
        request.setActorId(contextActorId());
        return ResponseEntity.ok(machineDisplayService.swapDisplay(contextSiteId(), request));
    }

    @PostMapping("/batch-swap")
    public ResponseEntity<List<MachineDisplayDTO>> batchSwapSiteDisplay(
            @PathVariable UUID siteId,
            @Valid @RequestBody BatchDisplaySwapRequestDTO request) {
        request.setActorId(contextActorId());
        return ResponseEntity.ok(machineDisplayService.batchSwapDisplay(contextSiteId(), request));
    }

    @PostMapping("/batch-clear")
    public ResponseEntity<List<MachineDisplayDTO>> batchClearSiteDisplays(
            @PathVariable UUID siteId,
            @Valid @RequestBody BatchClearDisplaysRequestDTO request) {
        request.setActorId(contextActorId());
        return ResponseEntity.ok(machineDisplayService.batchClearDisplays(contextSiteId(), request));
    }

    @PostMapping("/renew")
    public ResponseEntity<List<MachineDisplayDTO>> renewSiteDisplays(
            @PathVariable UUID siteId,
            @Valid @RequestBody RenewDisplayRequestDTO request) {
        request.setActorId(contextActorId());
        return ResponseEntity.ok(machineDisplayService.renewDisplays(contextSiteId(), request));
    }

    @DeleteMapping("/{locationType}/{machineId}")
    public ResponseEntity<Void> clearSiteDisplay(
            @PathVariable UUID siteId,
            @PathVariable LocationType locationType,
            @PathVariable UUID machineId) {
        machineDisplayService.clearDisplay(contextSiteId(), locationType, machineId, contextActorId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/by-id/{displayId}")
    public ResponseEntity<Void> clearSiteDisplayById(
            @PathVariable UUID siteId,
            @PathVariable UUID displayId) {
        machineDisplayService.clearDisplayById(contextSiteId(), displayId, contextActorId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{locationType}/{machineId}/active")
    public ResponseEntity<List<MachineDisplayDTO>> getSiteActiveDisplaysForMachine(
            @PathVariable UUID siteId,
            @PathVariable LocationType locationType,
            @PathVariable UUID machineId) {
        return ResponseEntity.ok(
                machineDisplayService.getActiveDisplaysForMachine(contextSiteId(), locationType, machineId));
    }

    @GetMapping("/{locationType}/{machineId}")
    public ResponseEntity<MachineDisplayDTO> getSiteCurrentDisplay(
            @PathVariable UUID siteId,
            @PathVariable LocationType locationType,
            @PathVariable UUID machineId) {
        return machineDisplayService.getCurrentDisplay(contextSiteId(), locationType, machineId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<MachineDisplayDTO>> getAllSiteActiveDisplays(@PathVariable UUID siteId) {
        return ResponseEntity.ok(machineDisplayService.getAllActiveDisplays(contextSiteId()));
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<MachineDisplayDTO>> getAllSiteActiveDisplaysPaged(
            @PathVariable UUID siteId,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(machineDisplayService.getAllActiveDisplays(contextSiteId(), pageable));
    }

    @GetMapping("/by-type/{locationType}")
    public ResponseEntity<List<MachineDisplayDTO>> getSiteActiveDisplaysByType(
            @PathVariable UUID siteId,
            @PathVariable LocationType locationType) {
        return ResponseEntity.ok(machineDisplayService.getActiveDisplaysByLocationType(contextSiteId(), locationType));
    }

    @GetMapping("/stale")
    public ResponseEntity<List<MachineDisplayDTO>> getSiteStaleDisplays(
            @PathVariable UUID siteId,
            @RequestParam(required = false) Integer thresholdDays) {
        UUID contextSiteId = contextSiteId();
        if (thresholdDays != null) {
            return ResponseEntity.ok(machineDisplayService.getStaleDisplays(contextSiteId, thresholdDays));
        }
        return ResponseEntity.ok(machineDisplayService.getStaleDisplays(contextSiteId));
    }

    @GetMapping("/stale/{locationType}")
    public ResponseEntity<List<MachineDisplayDTO>> getSiteStaleDisplaysByType(
            @PathVariable UUID siteId,
            @PathVariable LocationType locationType) {
        return ResponseEntity.ok(machineDisplayService.getStaleDisplaysByLocationType(contextSiteId(), locationType));
    }

    @GetMapping("/{locationType}/{machineId}/history")
    public ResponseEntity<List<MachineDisplayDTO>> getSiteMachineHistory(
            @PathVariable UUID siteId,
            @PathVariable LocationType locationType,
            @PathVariable UUID machineId) {
        return ResponseEntity.ok(machineDisplayService.getMachineHistory(contextSiteId(), locationType, machineId));
    }

    @GetMapping("/{locationType}/{machineId}/history/paged")
    public ResponseEntity<Page<MachineDisplayDTO>> getSiteMachineHistoryPaged(
            @PathVariable UUID siteId,
            @PathVariable LocationType locationType,
            @PathVariable UUID machineId,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                machineDisplayService.getMachineHistory(contextSiteId(), locationType, machineId, pageable));
    }

    @GetMapping("/product/{productId}/history")
    public ResponseEntity<List<MachineDisplayDTO>> getSiteProductHistory(
            @PathVariable UUID siteId,
            @PathVariable UUID productId) {
        return ResponseEntity.ok(machineDisplayService.getProductHistory(contextSiteId(), productId));
    }

    @DeleteMapping("/history/{displayId}")
    public ResponseEntity<Void> deleteSiteDisplayHistory(
            @PathVariable UUID siteId,
            @PathVariable UUID displayId) {
        machineDisplayService.deleteDisplayHistory(contextSiteId(), displayId);
        return ResponseEntity.noContent().build();
    }
}
