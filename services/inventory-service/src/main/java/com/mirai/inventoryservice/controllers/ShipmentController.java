package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.ShipmentMapper;
import com.mirai.inventoryservice.dtos.mappers.ShipmentMapperDecorator;
import com.mirai.inventoryservice.dtos.requests.ReceiveShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentResponseDTO;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.services.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {
    private final ShipmentService shipmentService;
    private final ShipmentMapper shipmentMapper;
    private final ShipmentMapperDecorator shipmentMapperDecorator;

    public ShipmentController(ShipmentService shipmentService, ShipmentMapper shipmentMapper,
                              ShipmentMapperDecorator shipmentMapperDecorator) {
        this.shipmentService = shipmentService;
        this.shipmentMapper = shipmentMapper;
        this.shipmentMapperDecorator = shipmentMapperDecorator;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShipmentResponseDTO> createShipment(@Valid @RequestBody ShipmentRequestDTO requestDTO) {
        Shipment shipment = shipmentService.createShipment(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment));
    }

    @GetMapping
    public ResponseEntity<?> listShipments(
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        // If pagination params are provided, use paginated response
        if (page != null && size != null) {
            Pageable pageable = PageRequest.of(page, size);
            Page<Shipment> shipmentPage = shipmentService.listShipmentsPaged(status, search, pageable);
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShipmentResponseDTO> updateShipment(
            @PathVariable UUID id,
            @Valid @RequestBody ShipmentRequestDTO requestDTO) {
        Shipment shipment = shipmentService.updateShipment(id, requestDTO);
        return ResponseEntity.ok(shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteShipment(@PathVariable UUID id) {
        shipmentService.deleteShipment(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<ShipmentResponseDTO> receiveShipment(
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveShipmentRequestDTO requestDTO) {
        Shipment shipment = shipmentService.receiveShipment(id, requestDTO);
        return ResponseEntity.ok(shipmentMapperDecorator.toResponseDTOWithLocationCodes(shipment));
    }
}
