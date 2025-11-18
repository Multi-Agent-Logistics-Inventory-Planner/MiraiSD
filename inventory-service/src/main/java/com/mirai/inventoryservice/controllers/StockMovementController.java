package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.StockMovementMapper;
import com.mirai.inventoryservice.dtos.requests.AdjustStockRequestDTO;
import com.mirai.inventoryservice.dtos.requests.TransferInventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.StockMovementResponseDTO;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.services.StockMovementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-movements")
public class StockMovementController {
    private final StockMovementService stockMovementService;
    private final StockMovementMapper stockMovementMapper;

    public StockMovementController(
            StockMovementService stockMovementService,
            StockMovementMapper stockMovementMapper) {
        this.stockMovementService = stockMovementService;
        this.stockMovementMapper = stockMovementMapper;
    }

    @PostMapping("/{locationType}/{inventoryId}/adjust")
    public ResponseEntity<StockMovementResponseDTO> adjustInventory(
            @PathVariable LocationType locationType,
            @PathVariable UUID inventoryId,
            @Valid @RequestBody AdjustStockRequestDTO requestDTO) {
        StockMovement movement = stockMovementService.adjustInventory(locationType, inventoryId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stockMovementMapper.toResponseDTO(movement));
    }

    @PostMapping("/transfer")
    public ResponseEntity<Void> transferInventory(@Valid @RequestBody TransferInventoryRequestDTO requestDTO) {
        stockMovementService.transferInventory(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/history/{itemId}")
    public ResponseEntity<Page<StockMovementResponseDTO>> getMovementHistory(
            @PathVariable UUID itemId,
            @PageableDefault(size = 20, sort = "at", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<StockMovement> movements = stockMovementService.getMovementHistory(itemId, pageable);
        return ResponseEntity.ok(movements.map(stockMovementMapper::toResponseDTO));
    }

    @GetMapping("/history/{itemId}/all")
    public ResponseEntity<List<StockMovementResponseDTO>> getAllMovementHistory(@PathVariable UUID itemId) {
        List<StockMovement> movements = stockMovementService.getMovementHistory(itemId);
        return ResponseEntity.ok(stockMovementMapper.toResponseDTOList(movements));
    }
}

