package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.ShipmentMapper;
import com.mirai.inventoryservice.dtos.requests.ReceiveShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentResponseDTO;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.services.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {
    private final ShipmentService shipmentService;
    private final ShipmentMapper shipmentMapper;

    public ShipmentController(ShipmentService shipmentService, ShipmentMapper shipmentMapper) {
        this.shipmentService = shipmentService;
        this.shipmentMapper = shipmentMapper;
    }

    @PostMapping
    public ResponseEntity<ShipmentResponseDTO> createShipment(@Valid @RequestBody ShipmentRequestDTO requestDTO) {
        Shipment shipment = shipmentService.createShipment(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shipmentMapper.toResponseDTO(shipment));
    }

    @GetMapping
    public ResponseEntity<List<ShipmentResponseDTO>> listShipments(
            @RequestParam(required = false) ShipmentStatus status) {
        List<Shipment> shipments = status != null
                ? shipmentService.listShipmentsByStatus(status)
                : shipmentService.listShipments();
        return ResponseEntity.ok(shipmentMapper.toResponseDTOList(shipments));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShipmentResponseDTO> getShipmentById(@PathVariable UUID id) {
        Shipment shipment = shipmentService.getShipmentById(id);
        return ResponseEntity.ok(shipmentMapper.toResponseDTO(shipment));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShipmentResponseDTO> updateShipment(
            @PathVariable UUID id,
            @Valid @RequestBody ShipmentRequestDTO requestDTO) {
        Shipment shipment = shipmentService.updateShipment(id, requestDTO);
        return ResponseEntity.ok(shipmentMapper.toResponseDTO(shipment));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShipment(@PathVariable UUID id) {
        shipmentService.deleteShipment(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<ShipmentResponseDTO> receiveShipment(
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveShipmentRequestDTO requestDTO) {
        Shipment shipment = shipmentService.receiveShipment(id, requestDTO);
        return ResponseEntity.ok(shipmentMapper.toResponseDTO(shipment));
    }
}
