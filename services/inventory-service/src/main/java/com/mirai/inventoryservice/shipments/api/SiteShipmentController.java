package com.mirai.inventoryservice.shipments.api;

import com.mirai.inventoryservice.dtos.mappers.ShipmentMapper;
import com.mirai.inventoryservice.dtos.responses.ShipmentItemResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentResponseDTO;
import com.mirai.inventoryservice.identity.application.UserService;
import com.mirai.inventoryservice.identity.domain.Permission;
import com.mirai.inventoryservice.identity.domain.RolePermissions;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import com.mirai.inventoryservice.shipments.application.ShipmentService;
import com.mirai.inventoryservice.shipments.domain.Shipment;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Site-scoped shipment routes (.specs/phase-7-shipments T-5, AC-5), reusing the generic
 * {@code /api/v1/sites/{siteId}/**} authorization filter -- {@code siteId} is still declared on
 * every handler so springdoc/the generated client expose it (matching
 * {@code catalog.api.SiteProductController}'s precedent), but the trusted value always comes from
 * {@link AuthorizedSiteContextHolder}. Every {@link ShipmentService} method this delegates to was
 * already made site-parameterized in T-4; this controller adds no new business logic, only a v1
 * route shape mirroring {@link ShipmentController}'s legacy one route-for-route. Distinct handler
 * names from {@link ShipmentController}'s so springdoc doesn't collide operation IDs.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/shipments")
public class SiteShipmentController {

    private final ShipmentService shipmentService;
    private final ShipmentMapper shipmentMapper;
    private final ShipmentMapperDecorator shipmentMapperDecorator;
    private final UserService userService;

    public SiteShipmentController(ShipmentService shipmentService, ShipmentMapper shipmentMapper,
                                   ShipmentMapperDecorator shipmentMapperDecorator,
                                   UserService userService) {
        this.shipmentService = shipmentService;
        this.shipmentMapper = shipmentMapper;
        this.shipmentMapperDecorator = shipmentMapperDecorator;
        this.userService = userService;
    }

    private record ActorInfo(UUID id, String name) {}

    private ActorInfo getActorInfo() {
        UUID backendUserId = AuthorizedSiteContextHolder.require().backendUserId();
        if (backendUserId == null) {
            return new ActorInfo(null, null);
        }
        return userService.findById(backendUserId)
                .map(user -> new ActorInfo(user.getId(), user.getFullName()))
                .orElse(new ActorInfo(null, null));
    }

    private void applyCostVisibility(ShipmentResponseDTO dto, Authentication authentication) {
        if (dto == null || RolePermissions.hasPermission(authentication, Permission.COSTS_VIEW)) {
            return;
        }
        dto.setTotalCost(null);
        if (dto.getItems() != null) {
            for (ShipmentItemResponseDTO item : dto.getItems()) {
                item.setUnitCost(null);
            }
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ShipmentResponseDTO> createSiteShipment(
            @PathVariable UUID siteId,
            @Valid @RequestBody ShipmentRequestDTO requestDTO,
            Authentication authentication) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        Shipment shipment = shipmentService.createShipment(contextSiteId, requestDTO);
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(contextSiteId, shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<?> listSiteShipments(
            @PathVariable UUID siteId,
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) String displayStatus,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            Authentication authentication) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        if (page != null && size != null) {
            Sort sort = sortDir.equalsIgnoreCase("asc")
                    ? Sort.by(sortBy).ascending()
                    : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<Shipment> shipmentPage;

            if (displayStatus != null && !displayStatus.isBlank()) {
                shipmentPage = shipmentService.listShipmentsByDisplayStatus(contextSiteId, displayStatus, search, pageable);
            } else {
                shipmentPage = shipmentService.listShipmentsPaged(contextSiteId, status, search, pageable);
            }

            List<ShipmentResponseDTO> dtos = shipmentMapperDecorator.toResponseDTOListWithLocationCodes(contextSiteId, shipmentPage.getContent());
            dtos.forEach(dto -> applyCostVisibility(dto, authentication));
            Page<ShipmentResponseDTO> dtoPage = new PageImpl<>(dtos, pageable, shipmentPage.getTotalElements());
            return ResponseEntity.ok(dtoPage);
        }

        List<Shipment> shipments = status != null
                ? shipmentService.listShipmentsByStatus(contextSiteId, status)
                : shipmentService.listShipments(contextSiteId);
        List<ShipmentResponseDTO> dtos = shipmentMapperDecorator.toResponseDTOListWithLocationCodes(contextSiteId, shipments);
        dtos.forEach(dto -> applyCostVisibility(dto, authentication));
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/display-status-counts")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Long>> getSiteDisplayStatusCounts(@PathVariable UUID siteId) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        return ResponseEntity.ok(shipmentService.getDisplayStatusCounts(contextSiteId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ShipmentResponseDTO> getSiteShipmentById(
            @PathVariable UUID siteId, @PathVariable UUID id, Authentication authentication) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        Shipment shipment = shipmentService.getShipmentById(contextSiteId, id);
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(contextSiteId, shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/by-product/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<ShipmentResponseDTO>> getSiteShipmentsByProduct(
            @PathVariable UUID siteId, @PathVariable UUID productId, Authentication authentication) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        List<Shipment> shipments = shipmentService.getShipmentsContainingProduct(contextSiteId, productId);
        List<ShipmentResponseDTO> dtos = shipmentMapperDecorator.toResponseDTOListWithLocationCodes(contextSiteId, shipments);
        dtos.forEach(dto -> applyCostVisibility(dto, authentication));
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ShipmentResponseDTO> updateSiteShipment(
            @PathVariable UUID siteId,
            @PathVariable UUID id,
            @Valid @RequestBody ShipmentRequestDTO requestDTO,
            Authentication authentication) {
        ActorInfo actor = getActorInfo();
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        Shipment shipment = shipmentService.updateShipment(contextSiteId, id, requestDTO, actor.id(), actor.name());
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(contextSiteId, shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteSiteShipment(@PathVariable UUID siteId, @PathVariable UUID id) {
        ActorInfo actor = getActorInfo();
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        shipmentService.deleteShipment(contextSiteId, id, actor.id(), actor.name());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ShipmentResponseDTO> receiveSiteShipment(
            @PathVariable UUID siteId,
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveShipmentRequestDTO requestDTO,
            Authentication authentication) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        Shipment shipment = shipmentService.receiveShipment(contextSiteId, id, requestDTO);
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(contextSiteId, shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{shipmentId}/items/{itemId}/undo-receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ShipmentResponseDTO> undoReceiveSiteShipmentItem(
            @PathVariable UUID siteId,
            @PathVariable UUID shipmentId,
            @PathVariable UUID itemId,
            Authentication authentication) {
        ActorInfo actor = getActorInfo();
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        Shipment shipment = shipmentService.undoReceiveShipmentItem(
                contextSiteId, shipmentId, itemId, actor.id(), actor.name());
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(contextSiteId, shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{shipmentId}/undo-receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ShipmentResponseDTO> undoReceiveSiteShipmentItems(
            @PathVariable UUID siteId,
            @PathVariable UUID shipmentId,
            @RequestBody ShipmentController.UndoReceiveRequest request,
            Authentication authentication) {
        ActorInfo actor = getActorInfo();
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        Shipment shipment = shipmentService.undoReceiveShipmentItems(
                contextSiteId, shipmentId, request.itemIds(), actor.id(), actor.name());
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(contextSiteId, shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ShipmentResponseDTO> overrideSiteShipmentStatus(
            @PathVariable UUID siteId,
            @PathVariable UUID id,
            @Valid @RequestBody ShipmentController.StatusOverrideRequest request,
            Authentication authentication) {
        ActorInfo actor = getActorInfo();
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        Shipment shipment = shipmentService.overrideShipmentStatus(
                contextSiteId, id, request.status(), request.reason(), actor.id(), actor.name());
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(contextSiteId, shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }
}
