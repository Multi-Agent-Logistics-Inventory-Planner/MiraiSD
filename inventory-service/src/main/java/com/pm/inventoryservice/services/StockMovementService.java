package com.pm.inventoryservice.services;

import com.pm.inventoryservice.dtos.mappers.StockMovementMapper;
import com.pm.inventoryservice.dtos.requests.AdjustStockRequestDTO;
import com.pm.inventoryservice.dtos.requests.TransferInventoryRequestDTO;
import com.pm.inventoryservice.dtos.responses.StockMovementResponseDTO;
import com.pm.inventoryservice.exceptions.*;
import com.pm.inventoryservice.models.*;
import com.pm.inventoryservice.models.enums.LocationType;
import com.pm.inventoryservice.models.enums.StockMovementReason;
import com.pm.inventoryservice.repositories.*;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class StockMovementService {
    private final StockMovementRepository stockMovementRepository;
    private final MachineInventoryRepository machineInventoryRepository;
    private final BinInventoryRepository binInventoryRepository;
    private final ShelfInventoryRepository shelfInventoryRepository;
    private final MachineRepository machineRepository;
    private final BinRepository binRepository;
    private final ShelfRepository shelfRepository;
    private final StockMovementMapper stockMovementMapper;

    public StockMovementService(
            StockMovementRepository stockMovementRepository,
            MachineInventoryRepository machineInventoryRepository,
            BinInventoryRepository binInventoryRepository,
            ShelfInventoryRepository shelfInventoryRepository,
            MachineRepository machineRepository,
            BinRepository binRepository,
            ShelfRepository shelfRepository,
            StockMovementMapper stockMovementMapper
    ) {
        this.stockMovementRepository = stockMovementRepository;
        this.machineInventoryRepository = machineInventoryRepository;
        this.binInventoryRepository = binInventoryRepository;
        this.shelfInventoryRepository = shelfInventoryRepository;
        this.machineRepository = machineRepository;
        this.binRepository = binRepository;
        this.shelfRepository = shelfRepository;
        this.stockMovementMapper = stockMovementMapper;
    }

    /*
    * Adjust inventory quantity (restock, sale, damage, etc)
    * Creates a single stock movement record
    * */
    public void adjustInventory(LocationType locationType, UUID inventoryId, AdjustStockRequestDTO request) {
        // 1. Load the appropriate inventory record
        Object inventory = loadInventory(locationType, inventoryId);
        // 2. Get current quantity
        int currentQuantity = getInventoryQuantity(inventory);
        // 3. Calculate new quantity
        int newQuantity = currentQuantity + request.getQuantityChange();
        // 4. Valida: can't go below 0
        if (newQuantity < 0) {
            throw new InsufficientInventoryException(
                    String.format("Cannot reduce quantity by %d. Current quantity: %d",
                            Math.abs(request.getQuantityChange()), currentQuantity)
            );
        }
        // 5. Update inventory quantity
        setInventoryQuantity(inventory, newQuantity);
        saveInventory(locationType, inventory);
        // 6.Create stock movement record
        Map<String, Object> metadata = new HashMap<>();
        if (request.getNotes() != null) {
            metadata.put("notes", request.getNotes());
        }
        StockMovement movement = StockMovement.builder()
                .itemId(inventoryId)
                .locationType(locationType)
                .quantityChange(request.getQuantityChange())
                .reason(request.getReason())
                .actorId(request.getActorId())
                .at(OffsetDateTime.now())
                .metadata(metadata.isEmpty() ? null : metadata)
                .build();

        stockMovementRepository.save(movement);
    }

    /**
     * Transfer inventory between two locations
     * Creates TWO stock movement records (withdrawal + deposit)
     */
    public void transferInventory(TransferInventoryRequestDTO request) {
        // 1. Load source inventory
        Object sourceInventory = loadInventory(request.getSourceLocationType(), request.getSourceInventoryId());
        int sourceQuantity = getInventoryQuantity(sourceInventory);
        // 2. Validate sufficient quantity
        if (sourceQuantity < request.getQuantity()) {
            throw new InsufficientInventoryException(
                    String.format("Cannot transfer %d items. Source only has %d available.",
                            request.getQuantity(), sourceQuantity)
            );
        }
        // 3. Load or validate destination inventory exists
        Object destinationInventory = loadInventory(request.getDestinationLocationType(), getDestinationInventoryId(request));
        int destinationQuantity = getInventoryQuantity(destinationInventory);
        // 4. Update quantities
        setInventoryQuantity(sourceInventory, sourceQuantity - request.getQuantity());
        setInventoryQuantity(destinationInventory, destinationQuantity + request.getQuantity());
        saveInventory(request.getSourceLocationType(), sourceInventory);
        saveInventory(request.getDestinationLocationType(), destinationInventory);
        // 5. Create metadata
        Map<String, Object> metadata = new HashMap<>();
        if (request.getNotes() != null) {
            metadata.put("notes", request.getNotes());
        }
        metadata.put("transfer", true);
        // 6. Create two stock movements (withdrawal from source)
        UUID sourceLocationId = getLocationId(sourceInventory, request.getSourceLocationType());
        UUID destinationLocationId = getLocationId(destinationInventory, request.getDestinationLocationType());
        StockMovement withdrawal = StockMovement.builder()
                .itemId(request.getSourceInventoryId())
                .locationType(request.getSourceLocationType())
                .fromBoxId(sourceLocationId)
                .toBoxId(destinationLocationId)
                .quantityChange(-request.getQuantity())
                .reason(StockMovementReason.ADJUSTMENT)  // or create TRANSFER reason
                .actorId(request.getActorId())
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();
        // 7. Create deposit to destination
        StockMovement deposit = StockMovement.builder()
                .itemId(getDestinationInventoryId(request))
                .locationType(request.getDestinationLocationType())
                .fromBoxId(sourceLocationId)
                .toBoxId(destinationLocationId)
                .quantityChange(request.getQuantity())
                .reason(StockMovementReason.ADJUSTMENT)  // or create TRANSFER reason
                .actorId(request.getActorId())
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();

        stockMovementRepository.save(withdrawal);
        stockMovementRepository.save(deposit);
    }

    /**
     * Get movement history for an inventory item
     */
    public List<StockMovementResponseDTO> getMovementHistory(UUID itemId, Pageable pageable) {
        Page<StockMovement> movements = stockMovementRepository.findByItemIdOrderByAtDesc(itemId, pageable);
        return stockMovementMapper.toDTOList(movements.getContent());
    }

    // ========= Helper Methods =========

    // Load inventory record based on location type
    @NonNull
    private Object loadInventory(LocationType locationType, UUID inventoryId) {
        return switch (locationType) {
            case MACHINE -> machineInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new MachineInventoryNotFoundException(
                            "Machine inventory not found with id: " + inventoryId));
            case BIN -> binInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new BinInventoryNotFoundException(
                            "Bin inventory not found with id: " + inventoryId));
            case SHELF -> shelfInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new ShelfInventoryNotFoundException(
                            "Shelf inventory not found with id: " + inventoryId));
        };
    }

    // Get quantity from inventory object
    private int getInventoryQuantity(Object inventory) {
        if (inventory instanceof MachineInventory mi) {
            return mi.getQuantity();
        } else if (inventory instanceof BinInventory bi) {
            return bi.getQuantity();
        } else if (inventory instanceof ShelfInventory si) {
            return si.getQuantity();
        }
        throw new IllegalArgumentException("Unknown inventory type");
    }

    // Set quantity on inventory object
    private void setInventoryQuantity(Object inventory, int quantity) {
        if (inventory instanceof MachineInventory mi) {
            mi.setQuantity(quantity);
        } else if (inventory instanceof BinInventory bi) {
            bi.setQuantity(quantity);
        } else if (inventory instanceof ShelfInventory si) {
            si.setQuantity(quantity);
        } else {
            throw new IllegalArgumentException("Unknown inventory type");
        }
    }

    // Save inventory based on type
    private void saveInventory(LocationType locationType, Object inventory) {
        switch (locationType) {
            case MACHINE -> machineInventoryRepository.save((MachineInventory) inventory);
            case BIN -> binInventoryRepository.save((BinInventory) inventory);
            case SHELF -> shelfInventoryRepository.save((ShelfInventory) inventory);
        }
    }

    // Get the location ID (machine_id, bin_id, shelf_id) from inventory
    private UUID getLocationId(Object inventory, LocationType locationType) {
        return switch (locationType) {
            case MACHINE -> ((MachineInventory) inventory).getMachine().getId();
            case BIN -> ((BinInventory) inventory).getBin().getId();
            case SHELF -> ((ShelfInventory) inventory).getShelf().getId();
        };
    }

    // Extract destination inventory ID from transfer request
    private UUID getDestinationInventoryId(TransferInventoryRequestDTO request) {
        return request.getDestinationInventoryId();
    }

    /**
     * Resolve location UUID → code (for future Kafka/UI use)
     */
    public String resolveLocationCode(UUID locationId, LocationType locationType) {
        if (locationId == null) return null;

        return switch (locationType) {
            case MACHINE -> machineRepository.findById(locationId)
                    .map(machine -> machine.getMachineCode())
                    .orElse(null);
            case BIN -> binRepository.findById(locationId)
                    .map(bin -> bin.getBinCode())
                    .orElse(null);
            case SHELF -> shelfRepository.findById(locationId)
                    .map(shelf -> shelf.getShelfCode())
                    .orElse(null);
        };
    }
}
