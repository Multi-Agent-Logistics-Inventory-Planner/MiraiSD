package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.ShipmentMapper;
import com.mirai.inventoryservice.dtos.mappers.ShipmentMapperDecorator;
import com.mirai.inventoryservice.dtos.requests.ReceiveShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentResponseDTO;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.repositories.UserRepository;
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
import java.util.Map;
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
    @SuppressWarnings("unchecked")
    private ActorInfo getActorInfo(Authentication authentication) {
        if (authentication == null) {
            return new ActorInfo(null, null);
        }
        Map<String, String> principal = (Map<String, String>) authentication.getPrincipal();
        String email = principal.get("email");
        if (email == null) {
            return new ActorInfo(null, null);
        }
        return userRepository.findByEmail(email)
                .map(user -> new ActorInfo(user.getId(), user.getFullName()))
                .orElse(new ActorInfo(null, null));
    }

    private record ActorInfo(UUID id, String name) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ShipmentResponseDTO> createShipment(@Valid @RequestBody ShipmentRequestDTO requestDTO) {
        Shipment shipment = shipmentService.createShipment(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment));
    }

    @GetMapping
    public ResponseEntity<?> listShipments(
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) String displayStatus,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
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
            Page<ShipmentResponseDTO> dtoPage = new PageImpl<>(dtos, pageable, shipmentPage.getTotalElements());
            return ResponseEntity.ok(dtoPage);
        }
        // Legacy: return list without pagination
        List<Shipment> shipments = status != null
                ? shipmentService.listShipmentsByStatus(status)
                : shipmentService.listShipments();
        return ResponseEntity.ok(shipmentMapperDecorator.toResponseDTOListWithLocationCodes(shipments));
    }

    @GetMapping("/display-status-counts")
    public ResponseEntity<java.util.Map<String, Long>> getDisplayStatusCounts() {
        return ResponseEntity.ok(shipmentService.getDisplayStatusCounts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShipmentResponseDTO> getShipmentById(@PathVariable UUID id) {
        Shipment shipment = shipmentService.getShipmentById(id);
        return ResponseEntity.ok(shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment));
    }

    @GetMapping("/by-product/{productId}")
    public ResponseEntity<List<ShipmentResponseDTO>> getShipmentsByProduct(@PathVariable UUID productId) {
        List<Shipment> shipments = shipmentService.getShipmentsContainingProduct(productId);
        return ResponseEntity.ok(shipmentMapperDecorator.toResponseDTOListWithLocationCodes(shipments));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ShipmentResponseDTO> updateShipment(
            @PathVariable UUID id,
            @Valid @RequestBody ShipmentRequestDTO requestDTO,
            Authentication authentication) {
        ActorInfo actor = getActorInfo(authentication);
        Shipment shipment = shipmentService.updateShipment(id, requestDTO, actor.id(), actor.name());
        return ResponseEntity.ok(shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment));
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
            @Valid @RequestBody ReceiveShipmentRequestDTO requestDTO) {
        Shipment shipment = shipmentService.receiveShipment(id, requestDTO);
        return ResponseEntity.ok(shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment));
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
        return ResponseEntity.ok(shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment));
    }
}
