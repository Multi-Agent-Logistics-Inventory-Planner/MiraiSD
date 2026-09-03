package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.ShipmentMapper;
import com.mirai.inventoryservice.dtos.mappers.ShipmentMapperDecorator;
import com.mirai.inventoryservice.dtos.requests.ReceiveShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentItemResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentResponseDTO;
import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.identity.domain.Permission;
import com.mirai.inventoryservice.identity.domain.RolePermissions;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.services.ShipmentService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {
    private final ShipmentService shipmentService;
    private final ShipmentMapper shipmentMapper;
    private final ShipmentMapperDecorator shipmentMapperDecorator;
    private final UserRepository userRepository;

    public ShipmentController(ShipmentService shipmentService, ShipmentMapper shipmentMapper,
                              ShipmentMapperDecorator shipmentMapperDecorator,
                              UserRepository userRepository) {
        this.shipmentService = shipmentService;
        this.shipmentMapper = shipmentMapper;
        this.shipmentMapperDecorator = shipmentMapperDecorator;
        this.userRepository = userRepository;
    }

    /**
     * Extract actor info from authentication for audit logging
     */
    private ActorInfo getActorInfo(Authentication authentication) {
        if (authentication == null) {
            return new ActorInfo(null, null);
        }
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        UUID backendUserId = principal.backendUserId();
        if (backendUserId == null) {
            return new ActorInfo(null, null);
        }
        return userRepository.findById(backendUserId)
                .map(user -> new ActorInfo(user.getId(), user.getFullName()))
                .orElse(new ActorInfo(null, null));
    }

    private record ActorInfo(UUID id, String name) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ShipmentResponseDTO> createShipment(@Valid @RequestBody ShipmentRequestDTO requestDTO, Authentication authentication) {
        Shipment shipment = shipmentService.createShipment(requestDTO);
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public ResponseEntity<?> listShipments(
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) String displayStatus,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            Authentication authentication) {
        // If pagination params are provided, use paginated response
        if (page != null && size != null) {
            Sort sort = sortDir.equalsIgnoreCase("asc")
                    ? Sort.by(sortBy).ascending()
                    : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<Shipment> shipmentPage;

            // Use displayStatus if provided (ACTIVE, PARTIAL, COMPLETED), otherwise fall back to status
            if (displayStatus != null && !displayStatus.isBlank()) {
                shipmentPage = shipmentService.listShipmentsByDisplayStatus(displayStatus, search, pageable);
            } else {
                shipmentPage = shipmentService.listShipmentsPaged(status, search, pageable);
            }

            List<ShipmentResponseDTO> dtos = shipmentMapperDecorator.toResponseDTOListWithLocationCodes(shipmentPage.getContent());
            dtos.forEach(dto -> applyCostVisibility(dto, authentication));
            Page<ShipmentResponseDTO> dtoPage = new PageImpl<>(dtos, pageable, shipmentPage.getTotalElements());
            return ResponseEntity.ok(dtoPage);
        }
        // Legacy: return list without pagination
        List<Shipment> shipments = status != null
                ? shipmentService.listShipmentsByStatus(status)
                : shipmentService.listShipments();
        List<ShipmentResponseDTO> dtos = shipmentMapperDecorator.toResponseDTOListWithLocationCodes(shipments);
        dtos.forEach(dto -> applyCostVisibility(dto, authentication));
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/display-status-counts")
    public ResponseEntity<java.util.Map<String, Long>> getDisplayStatusCounts() {
        return ResponseEntity.ok(shipmentService.getDisplayStatusCounts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShipmentResponseDTO> getShipmentById(@PathVariable UUID id, Authentication authentication) {
        Shipment shipment = shipmentService.getShipmentById(id);
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/by-product/{productId}")
    public ResponseEntity<List<ShipmentResponseDTO>> getShipmentsByProduct(@PathVariable UUID productId, Authentication authentication) {
        List<Shipment> shipments = shipmentService.getShipmentsContainingProduct(productId);
        List<ShipmentResponseDTO> dtos = shipmentMapperDecorator.toResponseDTOListWithLocationCodes(shipments);
        dtos.forEach(dto -> applyCostVisibility(dto, authentication));
        return ResponseEntity.ok(dtos);
    }

    /**
     * Null out cost fields the caller's role isn't permitted to see - same gap and same fix as
     * ProductController.applyCostVisibility.
     */
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ShipmentResponseDTO> updateShipment(
            @PathVariable UUID id,
            @Valid @RequestBody ShipmentRequestDTO requestDTO,
            Authentication authentication) {
        ActorInfo actor = getActorInfo(authentication);
        Shipment shipment = shipmentService.updateShipment(id, requestDTO, actor.id(), actor.name());
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteShipment(@PathVariable UUID id, Authentication authentication) {
        ActorInfo actor = getActorInfo(authentication);
        shipmentService.deleteShipment(id, actor.id(), actor.name());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ShipmentResponseDTO> receiveShipment(
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveShipmentRequestDTO requestDTO,
            Authentication authentication) {
        Shipment shipment = shipmentService.receiveShipment(id, requestDTO);
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{shipmentId}/items/{itemId}/undo-receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ShipmentResponseDTO> undoReceiveShipmentItem(
            @PathVariable UUID shipmentId,
            @PathVariable UUID itemId,
            Authentication authentication) {
        ActorInfo actor = getActorInfo(authentication);
        Shipment shipment = shipmentService.undoReceiveShipmentItem(
                shipmentId, itemId, actor.id(), actor.name());
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    public record UndoReceiveRequest(java.util.List<UUID> itemIds) {}

    @PostMapping("/{shipmentId}/undo-receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ShipmentResponseDTO> undoReceiveShipmentItems(
            @PathVariable UUID shipmentId,
            @RequestBody UndoReceiveRequest request,
            Authentication authentication) {
        ActorInfo actor = getActorInfo(authentication);
        Shipment shipment = shipmentService.undoReceiveShipmentItems(
                shipmentId, request.itemIds(), actor.id(), actor.name());
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    public record StatusOverrideRequest(ShipmentStatus status, String reason) {}

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ShipmentResponseDTO> overrideShipmentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusOverrideRequest request,
            Authentication authentication) {
        ActorInfo actor = getActorInfo(authentication);
        Shipment shipment = shipmentService.overrideShipmentStatus(
                id, request.status(), request.reason(), actor.id(), actor.name());
        ShipmentResponseDTO dto = shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }
}
