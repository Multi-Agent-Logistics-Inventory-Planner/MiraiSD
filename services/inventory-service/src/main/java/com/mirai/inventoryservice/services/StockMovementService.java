package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.AdjustStockRequestDTO;
import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.dtos.requests.TransferInventoryRequestDTO;
import com.mirai.inventoryservice.exceptions.*;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.*;
import com.mirai.inventoryservice.models.storage.*;
import com.mirai.inventoryservice.repositories.*;
import static com.mirai.inventoryservice.repositories.StockMovementSpecifications.withFilters;
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
    private final BoxBinInventoryRepository boxBinInventoryRepository;
    private final SingleClawMachineInventoryRepository singleClawMachineInventoryRepository;
    private final DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository;
    private final KeychainMachineInventoryRepository keychainMachineInventoryRepository;
    private final CabinetInventoryRepository cabinetInventoryRepository;
    private final RackInventoryRepository rackInventoryRepository;
    private final BoxBinRepository boxBinRepository;
    private final SingleClawMachineRepository singleClawMachineRepository;
    private final DoubleClawMachineRepository doubleClawMachineRepository;
    private final KeychainMachineRepository keychainMachineRepository;
    private final CabinetRepository cabinetRepository;
    private final RackRepository rackRepository;
    private final EventOutboxService eventOutboxService;

    public StockMovementService(
            StockMovementRepository stockMovementRepository,
            BoxBinInventoryRepository boxBinInventoryRepository,
            SingleClawMachineInventoryRepository singleClawMachineInventoryRepository,
            DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository,
            KeychainMachineInventoryRepository keychainMachineInventoryRepository,
            CabinetInventoryRepository cabinetInventoryRepository,
            RackInventoryRepository rackInventoryRepository,
            BoxBinRepository boxBinRepository,
            SingleClawMachineRepository singleClawMachineRepository,
            DoubleClawMachineRepository doubleClawMachineRepository,
            KeychainMachineRepository keychainMachineRepository,
            CabinetRepository cabinetRepository,
            RackRepository rackRepository,
            EventOutboxService eventOutboxService) {
        this.stockMovementRepository = stockMovementRepository;
        this.boxBinInventoryRepository = boxBinInventoryRepository;
        this.singleClawMachineInventoryRepository = singleClawMachineInventoryRepository;
        this.doubleClawMachineInventoryRepository = doubleClawMachineInventoryRepository;
        this.keychainMachineInventoryRepository = keychainMachineInventoryRepository;
        this.cabinetInventoryRepository = cabinetInventoryRepository;
        this.rackInventoryRepository = rackInventoryRepository;
        this.boxBinRepository = boxBinRepository;
        this.singleClawMachineRepository = singleClawMachineRepository;
        this.doubleClawMachineRepository = doubleClawMachineRepository;
        this.keychainMachineRepository = keychainMachineRepository;
        this.cabinetRepository = cabinetRepository;
        this.rackRepository = rackRepository;
        this.eventOutboxService = eventOutboxService;
    }

    /**
     * Adjust inventory quantity (restock, sale, damage, etc.)
     * Creates a single stock movement record
     */
    public StockMovement adjustInventory(LocationType locationType, UUID inventoryId, AdjustStockRequestDTO request) {
        // Load inventory records
        Object inventory = loadInventory(locationType, inventoryId);
        
        int currentQuantity = getInventoryQuantity(inventory);
        
        int newQuantity = currentQuantity + request.getQuantityChange();
        
        // Validate
        if (newQuantity < 0) {
            throw new InsufficientInventoryException(
                    String.format("Cannot reduce quantity by %d. Current quantity: %d",
                            Math.abs(request.getQuantityChange()), currentQuantity)
            );
        }
        
        setInventoryQuantity(inventory, newQuantity);
        saveInventory(locationType, inventory);
        
        // Create stock movement record
        Map<String, Object> metadata = new HashMap<>();
        if (request.getNotes() != null) {
            metadata.put("notes", request.getNotes());
        }
        metadata.put("inventory_id", inventoryId.toString());

        StockMovement movement = StockMovement.builder()
                .item(getInventoryProduct(inventory))
                .locationType(locationType)
                .previousQuantity(currentQuantity)
                .currentQuantity(newQuantity)
                .quantityChange(request.getQuantityChange())
                .reason(request.getReason())
                .actorId(request.getActorId())
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();

        StockMovement savedMovement = stockMovementRepository.save(movement);
        
        // Create outbox event for Kafka
        eventOutboxService.createStockMovementEvent(savedMovement);
        
        return savedMovement;
    }

    /**
     * Transfer inventory between two locations
     * Creates TWO stock movement records (withdrawal + deposit)
     */
    public void transferInventory(TransferInventoryRequestDTO request) {
        // Load source inventory
        Object sourceInventory = loadInventory(request.getSourceLocationType(), request.getSourceInventoryId());
        int sourceQuantity = getInventoryQuantity(sourceInventory);
        
        // Validate sufficient quantity
        if (sourceQuantity < request.getQuantity()) {
            throw new InsufficientInventoryException(
                    String.format("Cannot transfer %d items. Source only has %d available.",
                            request.getQuantity(), sourceQuantity)
            );
        }
        
        Object destinationInventory = loadInventory(request.getDestinationLocationType(), request.getDestinationInventoryId());
        int destinationQuantity = getInventoryQuantity(destinationInventory);
        
        setInventoryQuantity(sourceInventory, sourceQuantity - request.getQuantity());
        setInventoryQuantity(destinationInventory, destinationQuantity + request.getQuantity());
        saveInventory(request.getSourceLocationType(), sourceInventory);
        saveInventory(request.getDestinationLocationType(), destinationInventory);
        
        Map<String, Object> withdrawalMetadata = new HashMap<>();
        Map<String, Object> depositMetadata = new HashMap<>();
        if (request.getNotes() != null) {
            withdrawalMetadata.put("notes", request.getNotes());
            depositMetadata.put("notes", request.getNotes());
        }
        withdrawalMetadata.put("transfer", true);
        withdrawalMetadata.put("inventory_id", request.getSourceInventoryId().toString());
        depositMetadata.put("transfer", true);
        depositMetadata.put("inventory_id", request.getDestinationInventoryId().toString());

        UUID sourceLocationId = getLocationId(sourceInventory, request.getSourceLocationType());
        UUID destinationLocationId = getLocationId(destinationInventory, request.getDestinationLocationType());

        StockMovement withdrawal = StockMovement.builder()
                .item(getInventoryProduct(sourceInventory))
                .locationType(request.getSourceLocationType())
                .fromLocationId(sourceLocationId)
                .toLocationId(destinationLocationId)
                .previousQuantity(sourceQuantity)
                .currentQuantity(sourceQuantity - request.getQuantity())
                .quantityChange(-request.getQuantity())
                .reason(StockMovementReason.TRANSFER)
                .actorId(request.getActorId())
                .at(OffsetDateTime.now())
                .metadata(withdrawalMetadata)
                .build();

        StockMovement deposit = StockMovement.builder()
                .item(getInventoryProduct(destinationInventory))
                .locationType(request.getDestinationLocationType())
                .fromLocationId(sourceLocationId)
                .toLocationId(destinationLocationId)
                .previousQuantity(destinationQuantity)
                .currentQuantity(destinationQuantity + request.getQuantity())
                .quantityChange(request.getQuantity())
                .reason(StockMovementReason.TRANSFER)
                .actorId(request.getActorId())
                .at(OffsetDateTime.now())
                .metadata(depositMetadata)
                .build();
        
        StockMovement savedWithdrawal = stockMovementRepository.save(withdrawal);
        StockMovement savedDeposit = stockMovementRepository.save(deposit);
        
        // Create outbox events for Kafka
        eventOutboxService.createStockMovementEvent(savedWithdrawal);
        eventOutboxService.createStockMovementEvent(savedDeposit);
    }

    /**
     * Get movement history for a product
     */
    public Page<StockMovement> getMovementHistory(UUID productId, Pageable pageable) {
        return stockMovementRepository.findByItem_IdOrderByAtDesc(productId, pageable);
    }

    public List<StockMovement> getMovementHistory(UUID productId) {
        return stockMovementRepository.findByItem_IdOrderByAtDesc(productId);
    }

    /**
     * Get audit log with optional filters
     */
    public Page<StockMovement> getAuditLog(AuditLogFilterDTO filters, Pageable pageable) {
        return stockMovementRepository.findAll(withFilters(filters), pageable);
    }

    // ========= Helper Methods =========

    @NonNull
    private Object loadInventory(LocationType locationType, UUID inventoryId) {
        return switch (locationType) {
            case BOX_BIN -> boxBinInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new BoxBinInventoryNotFoundException("BoxBin inventory not found: " + inventoryId));
            case SINGLE_CLAW_MACHINE -> singleClawMachineInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new SingleClawMachineInventoryNotFoundException("SingleClawMachine inventory not found: " + inventoryId));
            case DOUBLE_CLAW_MACHINE -> doubleClawMachineInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new DoubleClawMachineInventoryNotFoundException("DoubleClawMachine inventory not found: " + inventoryId));
            case KEYCHAIN_MACHINE -> keychainMachineInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new KeychainMachineInventoryNotFoundException("KeychainMachine inventory not found: " + inventoryId));
            case CABINET -> cabinetInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new CabinetInventoryNotFoundException("Cabinet inventory not found: " + inventoryId));
            case RACK -> rackInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new RackInventoryNotFoundException("Rack inventory not found: " + inventoryId));
            case NOT_ASSIGNED -> throw new IllegalArgumentException("Cannot load inventory for NOT_ASSIGNED location type");
        };
    }

    private int getInventoryQuantity(Object inventory) {
        return switch (inventory) {
            case BoxBinInventory bbi -> bbi.getQuantity();
            case SingleClawMachineInventory scmi -> scmi.getQuantity();
            case DoubleClawMachineInventory dcmi -> dcmi.getQuantity();
            case KeychainMachineInventory kmi -> kmi.getQuantity();
            case CabinetInventory ci -> ci.getQuantity();
            case RackInventory ri -> ri.getQuantity();
            default -> throw new IllegalArgumentException("Unknown inventory type");
        };
    }

    private void setInventoryQuantity(Object inventory, int quantity) {
        switch (inventory) {
            case BoxBinInventory bbi -> bbi.setQuantity(quantity);
            case SingleClawMachineInventory scmi -> scmi.setQuantity(quantity);
            case DoubleClawMachineInventory dcmi -> dcmi.setQuantity(quantity);
            case KeychainMachineInventory kmi -> kmi.setQuantity(quantity);
            case CabinetInventory ci -> ci.setQuantity(quantity);
            case RackInventory ri -> ri.setQuantity(quantity);
            default -> throw new IllegalArgumentException("Unknown inventory type");
        }
    }

    private void saveInventory(LocationType locationType, Object inventory) {
        switch (locationType) {
            case BOX_BIN -> boxBinInventoryRepository.save((BoxBinInventory) inventory);
            case SINGLE_CLAW_MACHINE -> singleClawMachineInventoryRepository.save((SingleClawMachineInventory) inventory);
            case DOUBLE_CLAW_MACHINE -> doubleClawMachineInventoryRepository.save((DoubleClawMachineInventory) inventory);
            case KEYCHAIN_MACHINE -> keychainMachineInventoryRepository.save((KeychainMachineInventory) inventory);
            case CABINET -> cabinetInventoryRepository.save((CabinetInventory) inventory);
            case RACK -> rackInventoryRepository.save((RackInventory) inventory);
            case NOT_ASSIGNED -> throw new IllegalArgumentException("Cannot save inventory for NOT_ASSIGNED location type");
        }
    }

    private UUID getLocationId(Object inventory, LocationType locationType) {
        return switch (locationType) {
            case BOX_BIN -> ((BoxBinInventory) inventory).getBoxBin().getId();
            case SINGLE_CLAW_MACHINE -> ((SingleClawMachineInventory) inventory).getSingleClawMachine().getId();
            case DOUBLE_CLAW_MACHINE -> ((DoubleClawMachineInventory) inventory).getDoubleClawMachine().getId();
            case KEYCHAIN_MACHINE -> ((KeychainMachineInventory) inventory).getKeychainMachine().getId();
            case CABINET -> ((CabinetInventory) inventory).getCabinet().getId();
            case RACK -> ((RackInventory) inventory).getRack().getId();
            case NOT_ASSIGNED -> throw new IllegalArgumentException("Cannot get location ID for NOT_ASSIGNED location type");
        };
    }

    private com.mirai.inventoryservice.models.Product getInventoryProduct(Object inventory) {
        return switch (inventory) {
            case BoxBinInventory bbi -> bbi.getItem();
            case SingleClawMachineInventory scmi -> scmi.getItem();
            case DoubleClawMachineInventory dcmi -> dcmi.getItem();
            case KeychainMachineInventory kmi -> kmi.getItem();
            case CabinetInventory ci -> ci.getItem();
            case RackInventory ri -> ri.getItem();
            default -> throw new IllegalArgumentException("Unknown inventory type");
        };
    }

    /**
     * Resolve location UUID → code (for Kafka/UI use)
     */
    public String resolveLocationCode(UUID locationId, LocationType locationType) {
        if (locationId == null) return null;
        
        return switch (locationType) {
            case BOX_BIN -> boxBinRepository.findById(locationId)
                    .map(BoxBin::getBoxBinCode).orElse(null);
            case SINGLE_CLAW_MACHINE -> singleClawMachineRepository.findById(locationId)
                    .map(SingleClawMachine::getSingleClawMachineCode).orElse(null);
            case DOUBLE_CLAW_MACHINE -> doubleClawMachineRepository.findById(locationId)
                    .map(DoubleClawMachine::getDoubleClawMachineCode).orElse(null);
            case KEYCHAIN_MACHINE -> keychainMachineRepository.findById(locationId)
                    .map(KeychainMachine::getKeychainMachineCode).orElse(null);
            case CABINET -> cabinetRepository.findById(locationId)
                    .map(Cabinet::getCabinetCode).orElse(null);
            case RACK -> rackRepository.findById(locationId)
                    .map(Rack::getRackCode).orElse(null);
            case NOT_ASSIGNED -> "Not Assigned";
        };
    }
}

