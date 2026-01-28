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
    private final FourCornerMachineInventoryRepository fourCornerMachineInventoryRepository;
    private final PusherMachineInventoryRepository pusherMachineInventoryRepository;
    private final BoxBinRepository boxBinRepository;
    private final SingleClawMachineRepository singleClawMachineRepository;
    private final DoubleClawMachineRepository doubleClawMachineRepository;
    private final KeychainMachineRepository keychainMachineRepository;
    private final CabinetRepository cabinetRepository;
    private final RackRepository rackRepository;
    private final FourCornerMachineRepository fourCornerMachineRepository;
    private final PusherMachineRepository pusherMachineRepository;
    private final EventOutboxService eventOutboxService;

    public StockMovementService(
            StockMovementRepository stockMovementRepository,
            BoxBinInventoryRepository boxBinInventoryRepository,
            SingleClawMachineInventoryRepository singleClawMachineInventoryRepository,
            DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository,
            KeychainMachineInventoryRepository keychainMachineInventoryRepository,
            CabinetInventoryRepository cabinetInventoryRepository,
            RackInventoryRepository rackInventoryRepository,
            FourCornerMachineInventoryRepository fourCornerMachineInventoryRepository,
            PusherMachineInventoryRepository pusherMachineInventoryRepository,
            BoxBinRepository boxBinRepository,
            SingleClawMachineRepository singleClawMachineRepository,
            DoubleClawMachineRepository doubleClawMachineRepository,
            KeychainMachineRepository keychainMachineRepository,
            CabinetRepository cabinetRepository,
            RackRepository rackRepository,
            FourCornerMachineRepository fourCornerMachineRepository,
            PusherMachineRepository pusherMachineRepository,
            EventOutboxService eventOutboxService) {
        this.stockMovementRepository = stockMovementRepository;
        this.boxBinInventoryRepository = boxBinInventoryRepository;
        this.singleClawMachineInventoryRepository = singleClawMachineInventoryRepository;
        this.doubleClawMachineInventoryRepository = doubleClawMachineInventoryRepository;
        this.keychainMachineInventoryRepository = keychainMachineInventoryRepository;
        this.cabinetInventoryRepository = cabinetInventoryRepository;
        this.rackInventoryRepository = rackInventoryRepository;
        this.fourCornerMachineInventoryRepository = fourCornerMachineInventoryRepository;
        this.pusherMachineInventoryRepository = pusherMachineInventoryRepository;
        this.boxBinRepository = boxBinRepository;
        this.singleClawMachineRepository = singleClawMachineRepository;
        this.doubleClawMachineRepository = doubleClawMachineRepository;
        this.keychainMachineRepository = keychainMachineRepository;
        this.cabinetRepository = cabinetRepository;
        this.rackRepository = rackRepository;
        this.fourCornerMachineRepository = fourCornerMachineRepository;
        this.pusherMachineRepository = pusherMachineRepository;
        this.eventOutboxService = eventOutboxService;
    }

    /**
     * Adjust inventory quantity (restock, sale, damage, etc.)
     * Creates a single stock movement record
     */
    public StockMovement adjustInventory(LocationType locationType, UUID inventoryId, AdjustStockRequestDTO request) {
        // 1. Load the appropriate inventory record
        Object inventory = loadInventory(locationType, inventoryId);
        
        // 2. Get current quantity
        int currentQuantity = getInventoryQuantity(inventory);
        
        // 3. Calculate new quantity
        int newQuantity = currentQuantity + request.getQuantityChange();
        
        // 4. Validate: can't go below 0
        if (newQuantity < 0) {
            throw new InsufficientInventoryException(
                    String.format("Cannot reduce quantity by %d. Current quantity: %d",
                            Math.abs(request.getQuantityChange()), currentQuantity)
            );
        }
        
        // 5. Update inventory quantity
        setInventoryQuantity(inventory, newQuantity);
        saveInventory(locationType, inventory);
        
        // 6. Create stock movement record
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
     * If destination inventory doesn't exist, creates it automatically
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

        // 3. Load or create destination inventory
        Object destinationInventory;
        int destinationQuantity;
        UUID destinationInventoryId = request.getDestinationInventoryId();

        if (destinationInventoryId != null) {
            // Load existing destination inventory
            destinationInventory = loadInventory(request.getDestinationLocationType(), destinationInventoryId);
            destinationQuantity = getInventoryQuantity(destinationInventory);
        } else {
            // Create new inventory at destination
            if (request.getDestinationLocationId() == null) {
                throw new IllegalArgumentException("Either destinationInventoryId or destinationLocationId must be provided");
            }
            destinationInventory = createInventoryAtLocation(
                    request.getDestinationLocationType(),
                    request.getDestinationLocationId(),
                    getInventoryProduct(sourceInventory),
                    0
            );
            destinationQuantity = 0;
            destinationInventoryId = getInventoryId(destinationInventory);
        }
        
        // 4. Update quantities
        setInventoryQuantity(sourceInventory, sourceQuantity - request.getQuantity());
        setInventoryQuantity(destinationInventory, destinationQuantity + request.getQuantity());
        saveInventory(request.getSourceLocationType(), sourceInventory);
        saveInventory(request.getDestinationLocationType(), destinationInventory);
        
        // 5. Create metadata
        Map<String, Object> withdrawalMetadata = new HashMap<>();
        Map<String, Object> depositMetadata = new HashMap<>();
        if (request.getNotes() != null) {
            withdrawalMetadata.put("notes", request.getNotes());
            depositMetadata.put("notes", request.getNotes());
        }
        withdrawalMetadata.put("transfer", true);
        withdrawalMetadata.put("inventory_id", request.getSourceInventoryId().toString());
        depositMetadata.put("transfer", true);
        depositMetadata.put("inventory_id", destinationInventoryId.toString());

        // 6. Get location IDs
        UUID sourceLocationId = getLocationId(sourceInventory, request.getSourceLocationType());
        UUID destinationLocationId = getLocationId(destinationInventory, request.getDestinationLocationType());

        // 7. Create withdrawal movement
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

        // 8. Create deposit movement
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
            case FOUR_CORNER_MACHINE -> fourCornerMachineInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new FourCornerMachineInventoryNotFoundException("FourCornerMachine inventory not found: " + inventoryId));
            case PUSHER_MACHINE -> pusherMachineInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new PusherMachineInventoryNotFoundException("PusherMachine inventory not found: " + inventoryId));
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
            case FourCornerMachineInventory fcmi -> fcmi.getQuantity();
            case PusherMachineInventory pmi -> pmi.getQuantity();
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
            case FourCornerMachineInventory fcmi -> fcmi.setQuantity(quantity);
            case PusherMachineInventory pmi -> pmi.setQuantity(quantity);
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
            case FOUR_CORNER_MACHINE -> fourCornerMachineInventoryRepository.save((FourCornerMachineInventory) inventory);
            case PUSHER_MACHINE -> pusherMachineInventoryRepository.save((PusherMachineInventory) inventory);
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
            case FOUR_CORNER_MACHINE -> ((FourCornerMachineInventory) inventory).getFourCornerMachine().getId();
            case PUSHER_MACHINE -> ((PusherMachineInventory) inventory).getPusherMachine().getId();
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
            case FourCornerMachineInventory fcmi -> fcmi.getItem();
            case PusherMachineInventory pmi -> pmi.getItem();
            default -> throw new IllegalArgumentException("Unknown inventory type");
        };
    }

    /**
     * Create a new inventory record at the specified location
     */
    private Object createInventoryAtLocation(LocationType locationType, UUID locationId,
            com.mirai.inventoryservice.models.Product product, int quantity) {
        return switch (locationType) {
            case BOX_BIN -> {
                BoxBin boxBin = boxBinRepository.findById(locationId)
                        .orElseThrow(() -> new BoxBinNotFoundException("BoxBin not found: " + locationId));
                BoxBinInventory inv = new BoxBinInventory();
                inv.setBoxBin(boxBin);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield boxBinInventoryRepository.save(inv);
            }
            case SINGLE_CLAW_MACHINE -> {
                SingleClawMachine machine = singleClawMachineRepository.findById(locationId)
                        .orElseThrow(() -> new SingleClawMachineNotFoundException("SingleClawMachine not found: " + locationId));
                SingleClawMachineInventory inv = new SingleClawMachineInventory();
                inv.setSingleClawMachine(machine);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield singleClawMachineInventoryRepository.save(inv);
            }
            case DOUBLE_CLAW_MACHINE -> {
                DoubleClawMachine machine = doubleClawMachineRepository.findById(locationId)
                        .orElseThrow(() -> new DoubleClawMachineNotFoundException("DoubleClawMachine not found: " + locationId));
                DoubleClawMachineInventory inv = new DoubleClawMachineInventory();
                inv.setDoubleClawMachine(machine);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield doubleClawMachineInventoryRepository.save(inv);
            }
            case KEYCHAIN_MACHINE -> {
                KeychainMachine machine = keychainMachineRepository.findById(locationId)
                        .orElseThrow(() -> new KeychainMachineNotFoundException("KeychainMachine not found: " + locationId));
                KeychainMachineInventory inv = new KeychainMachineInventory();
                inv.setKeychainMachine(machine);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield keychainMachineInventoryRepository.save(inv);
            }
            case CABINET -> {
                Cabinet cabinet = cabinetRepository.findById(locationId)
                        .orElseThrow(() -> new CabinetNotFoundException("Cabinet not found: " + locationId));
                CabinetInventory inv = new CabinetInventory();
                inv.setCabinet(cabinet);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield cabinetInventoryRepository.save(inv);
            }
            case RACK -> {
                Rack rack = rackRepository.findById(locationId)
                        .orElseThrow(() -> new RackNotFoundException("Rack not found: " + locationId));
                RackInventory inv = new RackInventory();
                inv.setRack(rack);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield rackInventoryRepository.save(inv);
            }
            case FOUR_CORNER_MACHINE -> {
                FourCornerMachine machine = fourCornerMachineRepository.findById(locationId)
                        .orElseThrow(() -> new FourCornerMachineNotFoundException("FourCornerMachine not found: " + locationId));
                FourCornerMachineInventory inv = new FourCornerMachineInventory();
                inv.setFourCornerMachine(machine);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield fourCornerMachineInventoryRepository.save(inv);
            }
            case PUSHER_MACHINE -> {
                PusherMachine machine = pusherMachineRepository.findById(locationId)
                        .orElseThrow(() -> new PusherMachineNotFoundException("PusherMachine not found: " + locationId));
                PusherMachineInventory inv = new PusherMachineInventory();
                inv.setPusherMachine(machine);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield pusherMachineInventoryRepository.save(inv);
            }
        };
    }

    /**
     * Get the ID from an inventory object
     */
    private UUID getInventoryId(Object inventory) {
        return switch (inventory) {
            case BoxBinInventory bbi -> bbi.getId();
            case SingleClawMachineInventory scmi -> scmi.getId();
            case DoubleClawMachineInventory dcmi -> dcmi.getId();
            case KeychainMachineInventory kmi -> kmi.getId();
            case CabinetInventory ci -> ci.getId();
            case RackInventory ri -> ri.getId();
            case FourCornerMachineInventory fcmi -> fcmi.getId();
            case PusherMachineInventory pmi -> pmi.getId();
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
            case FOUR_CORNER_MACHINE -> fourCornerMachineRepository.findById(locationId)
                    .map(FourCornerMachine::getFourCornerMachineCode).orElse(null);
            case PUSHER_MACHINE -> pusherMachineRepository.findById(locationId)
                    .map(PusherMachine::getPusherMachineCode).orElse(null);
        };
    }
}

