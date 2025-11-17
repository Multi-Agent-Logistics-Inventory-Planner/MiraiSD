package com.pm.inventoryservice.controllers;

import com.pm.inventoryservice.dtos.requests.AdjustStockRequestDTO;
import com.pm.inventoryservice.dtos.requests.TransferInventoryRequestDTO;
import com.pm.inventoryservice.dtos.responses.StockMovementResponseDTO;
import com.pm.inventoryservice.models.enums.LocationType;
import com.pm.inventoryservice.services.StockMovementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-movements")
public class StockMovementController {
    private final StockMovementService stockMovementService;

    public StockMovementController(StockMovementService stockMovementService) {
        this.stockMovementService = stockMovementService;
    }

    // Adjust inventory quantity
    @PostMapping("/{locationType}/{inventoryId}/adjust")
    public ResponseEntity<Void> adjustInventory(
            @PathVariable LocationType locationType,
            @PathVariable UUID inventoryId,
            @Valid @RequestBody AdjustStockRequestDTO request){
        stockMovementService.adjustInventory(locationType, inventoryId, request);
        return ResponseEntity.ok().build();
    }

    // Transfer between locations
    @PostMapping("/transfer")
    public ResponseEntity<Void> transferInventory(
            @Valid @RequestBody TransferInventoryRequestDTO request
    ) {
        stockMovementService.transferInventory(request);
        return ResponseEntity.ok().build();
    }

    // Get movement history
    @GetMapping("/history/{itemId}")
    public ResponseEntity<List<StockMovementResponseDTO>> getHistory(
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        List<StockMovementResponseDTO> history = stockMovementService.getMovementHistory(itemId, pageable);
        return ResponseEntity.ok(history);
    }
}
