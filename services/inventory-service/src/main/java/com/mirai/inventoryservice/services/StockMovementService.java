package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.AdjustStockRequestDTO;
import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.dtos.requests.BatchTransferInventoryRequestDTO;
import com.mirai.inventoryservice.dtos.requests.TransferInventoryRequestDTO;
import com.mirai.inventoryservice.exceptions.*;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.*;
import com.mirai.inventoryservice.models.storage.*;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.repositories.*;
import static com.mirai.inventoryservice.repositories.StockMovementSpecifications.withFilters;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class StockMovementService {
    private final StockMovementRepository stockMovementRepository;
    private final AuditLogRepository auditLogRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final BoxBinInventoryRepository boxBinInventoryRepository;
    private final SingleClawMachineInventoryRepository singleClawMachineInventoryRepository;
    private final DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository;
    private final CabinetInventoryRepository cabinetInventoryRepository;
    private final RackInventoryRepository rackInventoryRepository;
    private final FourCornerMachineInventoryRepository fourCornerMachineInventoryRepository;
    private final PusherMachineInventoryRepository pusherMachineInventoryRepository;
    private final WindowInventoryRepository windowInventoryRepository;
    private final NotAssignedInventoryRepository notAssignedInventoryRepository;
    private final BoxBinRepository boxBinRepository;
    private final SingleClawMachineRepository singleClawMachineRepository;
    private final DoubleClawMachineRepository doubleClawMachineRepository;
    private final KeychainMachineRepository keychainMachineRepository;
    private final CabinetRepository cabinetRepository;
    private final RackRepository rackRepository;
    private final FourCornerMachineRepository fourCornerMachineRepository;
    private final PusherMachineRepository pusherMachineRepository;
    private final WindowRepository windowRepository;
    private final EntityManager entityManager;
    private final SupabaseBroadcastService broadcastService;
    private final EventOutboxService eventOutboxService;

    public StockMovementService(
            StockMovementRepository stockMovementRepository,
            AuditLogRepository auditLogRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            BoxBinInventoryRepository boxBinInventoryRepository,
            SingleClawMachineInventoryRepository singleClawMachineInventoryRepository,
            DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository,
            CabinetInventoryRepository cabinetInventoryRepository,
            RackInventoryRepository rackInventoryRepository,
            FourCornerMachineInventoryRepository fourCornerMachineInventoryRepository,
            PusherMachineInventoryRepository pusherMachineInventoryRepository,
            WindowInventoryRepository windowInventoryRepository,
            NotAssignedInventoryRepository notAssignedInventoryRepository,
            BoxBinRepository boxBinRepository,
            SingleClawMachineRepository singleClawMachineRepository,
            DoubleClawMachineRepository doubleClawMachineRepository,
            KeychainMachineRepository keychainMachineRepository,
            CabinetRepository cabinetRepository,
            RackRepository rackRepository,
            FourCornerMachineRepository fourCornerMachineRepository,
            PusherMachineRepository pusherMachineRepository,
            WindowRepository windowRepository,
            EntityManager entityManager,
            SupabaseBroadcastService broadcastService,
            @org.springframework.context.annotation.Lazy EventOutboxService eventOutboxService) {
        this.stockMovementRepository = stockMovementRepository;
        this.auditLogRepository = auditLogRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.boxBinInventoryRepository = boxBinInventoryRepository;
        this.singleClawMachineInventoryRepository = singleClawMachineInventoryRepository;
        this.doubleClawMachineInventoryRepository = doubleClawMachineInventoryRepository;
        this.cabinetInventoryRepository = cabinetInventoryRepository;
        this.rackInventoryRepository = rackInventoryRepository;
        this.fourCornerMachineInventoryRepository = fourCornerMachineInventoryRepository;
        this.pusherMachineInventoryRepository = pusherMachineInventoryRepository;
        this.windowInventoryRepository = windowInventoryRepository;
        this.notAssignedInventoryRepository = notAssignedInventoryRepository;
        this.boxBinRepository = boxBinRepository;
        this.singleClawMachineRepository = singleClawMachineRepository;
        this.doubleClawMachineRepository = doubleClawMachineRepository;
        this.keychainMachineRepository = keychainMachineRepository;
        this.cabinetRepository = cabinetRepository;
        this.rackRepository = rackRepository;
        this.fourCornerMachineRepository = fourCornerMachineRepository;
        this.pusherMachineRepository = pusherMachineRepository;
        this.windowRepository = windowRepository;
        this.entityManager = entityManager;
        this.broadcastService = broadcastService;
        this.eventOutboxService = eventOutboxService;
    }

    /**
     * Adjust inventory quantity (restock, sale, damage, etc.)
     * Creates a single stock movement record linked to an audit log
     */
    @Transactional
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

        if (newQuantity == 0) {
            deleteInventory(locationType, inventory);
        } else {
            setInventoryQuantity(inventory, newQuantity);
            saveInventory(locationType, inventory);
        }

        UUID locationId = getLocationId(inventory, locationType);
        String locationCode = resolveLocationCode(locationId, locationType);

        // Create audit log entry
        AuditLog auditLog = createAuditLog(
                request.getActorId(),
                request.getReason(),
                null,
                null,
                locationId,
                locationCode,
                1,
                Math.abs(request.getQuantityChange()),
                getInventoryProduct(inventory).getName(),
                request.getNotes()
        );

        // Create stock movement record
        Map<String, Object> metadata = new HashMap<>();
        if (request.getNotes() != null) {
            metadata.put("notes", request.getNotes());
        }
        metadata.put("inventory_id", inventoryId.toString());

        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .item(getInventoryProduct(inventory))
                .locationType(locationType)
                .toLocationId(locationId)
                .previousQuantity(currentQuantity)
                .currentQuantity(newQuantity)
                .quantityChange(request.getQuantityChange())
                .reason(request.getReason())
                .actorId(request.getActorId())
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();

        StockMovement savedMovement = stockMovementRepository.save(movement);
        eventOutboxService.createStockMovementEvent(savedMovement);

        // Update product active status based on total inventory
        boolean productChanged = updateProductActiveStatus(savedMovement.getItem());

        // Broadcast real-time update to connected clients
        broadcastService.broadcastInventoryUpdated(locationType.name(), savedMovement.getItem().getId().toString());
        broadcastService.broadcastAuditLogCreated(savedMovement.getItem().getId().toString());
        if (productChanged) {
            broadcastService.broadcastProductUpdated(List.of(savedMovement.getItem().getId().toString()));
        }

        return savedMovement;
    }

    /**
     * Transfer inventory between two locations for a single item.
     * Creates TWO stock movement records (withdrawal + deposit) linked to a single audit log.
     * If destination inventory doesn't exist, creates it automatically.
     */
    @Transactional
    public void transferInventory(TransferInventoryRequestDTO request) {
        Object sourceInventory = loadInventory(request.getSourceLocationType(), request.getSourceInventoryId());
        int sourceQuantity = getInventoryQuantity(sourceInventory);

        UUID sourceLocationId = getLocationId(sourceInventory, request.getSourceLocationType());
        String sourceLocationCode = resolveLocationCode(sourceLocationId, request.getSourceLocationType());

        // Resolve destination location code before executing so the AuditLog captures it
        UUID destLocationId = resolveDestinationLocationId(request);
        String destLocationCode = resolveLocationCode(destLocationId, request.getDestinationLocationType());

        AuditLog auditLog = createAuditLog(
                request.getActorId(),
                StockMovementReason.TRANSFER,
                sourceLocationId,
                sourceLocationCode,
                destLocationId,
                destLocationCode,
                1,
                request.getQuantity(),
                getInventoryProduct(sourceInventory).getName(),
                request.getNotes()
        );

        executeTransfer(request, sourceInventory, sourceQuantity, auditLog);

        // Broadcast real-time update to connected clients
        broadcastService.broadcastInventoryUpdated();
        broadcastService.broadcastAuditLogCreated();
    }

    /**
     * Transfer multiple inventory items between two locations in a single batch.
     * Creates ONE audit log entry covering all items, with itemCount = number of distinct products.
     */
    @Transactional
    public void batchTransferInventory(BatchTransferInventoryRequestDTO batchRequest) {
        List<TransferInventoryRequestDTO> transfers = batchRequest.getTransfers();

        // Use first transfer's location info for the shared AuditLog
        TransferInventoryRequestDTO first = transfers.get(0);
        Object firstSource = loadInventory(first.getSourceLocationType(), first.getSourceInventoryId());
        UUID sourceLocationId = getLocationId(firstSource, first.getSourceLocationType());
        String sourceLocationCode = resolveLocationCode(sourceLocationId, first.getSourceLocationType());

        UUID destLocationId = resolveDestinationLocationId(first);
        String destLocationCode = resolveLocationCode(destLocationId, first.getDestinationLocationType());

        int totalQuantity = transfers.stream().mapToInt(TransferInventoryRequestDTO::getQuantity).sum();

        String productSummary = transfers.size() == 1
                ? getInventoryProduct(firstSource).getName()
                : transfers.size() + " products";

        AuditLog auditLog = createAuditLog(
                first.getActorId(),
                StockMovementReason.TRANSFER,
                sourceLocationId,
                sourceLocationCode,
                destLocationId,
                destLocationCode,
                transfers.size(),
                totalQuantity,
                productSummary,
                first.getNotes()
        );

        for (TransferInventoryRequestDTO request : transfers) {
            Object sourceInventory = loadInventory(request.getSourceLocationType(), request.getSourceInventoryId());
            int sourceQuantity = getInventoryQuantity(sourceInventory);
            executeTransfer(request, sourceInventory, sourceQuantity, auditLog);
        }

        // Broadcast real-time update to connected clients
        broadcastService.broadcastInventoryUpdated();
        broadcastService.broadcastAuditLogCreated();
    }

    /**
     * Core transfer logic: validates, moves inventory, creates withdrawal + deposit StockMovements
     * linked to the provided AuditLog. Used by both single and batch transfer paths.
     */
    private void executeTransfer(TransferInventoryRequestDTO request,
                                  Object sourceInventory, int sourceQuantity,
                                  AuditLog auditLog) {
        if (sourceQuantity < request.getQuantity()) {
            throw new InsufficientInventoryException(
                    String.format("Cannot transfer %d items. Source only has %d available.",
                            request.getQuantity(), sourceQuantity)
            );
        }

        Object destinationInventory;
        int destinationQuantity;
        UUID destinationInventoryId = request.getDestinationInventoryId();

        if (destinationInventoryId != null) {
            destinationInventory = loadInventory(request.getDestinationLocationType(), destinationInventoryId);
            destinationQuantity = getInventoryQuantity(destinationInventory);
        } else {
            if (request.getDestinationLocationId() == null
                    && request.getDestinationLocationType() != com.mirai.inventoryservice.models.enums.LocationType.NOT_ASSIGNED) {
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

        int newSourceQuantity = sourceQuantity - request.getQuantity();
        setInventoryQuantity(destinationInventory, destinationQuantity + request.getQuantity());
        if (newSourceQuantity == 0) {
            deleteInventory(request.getSourceLocationType(), sourceInventory);
        } else {
            setInventoryQuantity(sourceInventory, newSourceQuantity);
            saveInventory(request.getSourceLocationType(), sourceInventory);
        }
        saveInventory(request.getDestinationLocationType(), destinationInventory);

        UUID sourceLocationId = getLocationId(sourceInventory, request.getSourceLocationType());
        UUID destinationLocationId = getLocationId(destinationInventory, request.getDestinationLocationType());

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

        StockMovement withdrawal = StockMovement.builder()
                .auditLog(auditLog)
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
                .auditLog(auditLog)
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
        eventOutboxService.createStockMovementEvent(savedWithdrawal);
        eventOutboxService.createStockMovementEvent(savedDeposit);

        updateProductActiveStatus(getInventoryProduct(sourceInventory));
    }

    /**
     * Resolves the physical destination location UUID for a transfer request.
     * Returns null for NOT_ASSIGNED (no physical location).
     */
    private UUID resolveDestinationLocationId(TransferInventoryRequestDTO request) {
        if (request.getDestinationLocationId() != null) {
            return request.getDestinationLocationId();
        }
        if (request.getDestinationInventoryId() != null) {
            Object destInventory = loadInventory(request.getDestinationLocationType(), request.getDestinationInventoryId());
            return getLocationId(destInventory, request.getDestinationLocationType());
        }
        return null; // NOT_ASSIGNED
    }

    /**
     * Create new inventory at a location with tracking.
     * Creates the inventory record and a stock movement for audit.
     *
     * @param locationType The type of storage location
     * @param locationId The ID of the storage location (null for NOT_ASSIGNED)
     * @param product The product to add
     * @param quantity The initial quantity
     * @param reason The reason for adding (typically INITIAL_STOCK or RESTOCK)
     * @param actorId The user performing the action (optional)
     * @param notes Additional notes (optional)
     * @return The created inventory ID
     */
    @Transactional
    public UUID createInventoryWithTracking(LocationType locationType, UUID locationId,
                                            Product product, int quantity,
                                            StockMovementReason reason, UUID actorId, String notes) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        // Create the inventory record
        Object inventory = createInventoryAtLocation(locationType, locationId, product, quantity);
        UUID inventoryId = getInventoryId(inventory);
        String locationCode = resolveLocationCode(locationId, locationType);

        // Create audit log entry
        AuditLog auditLog = createAuditLog(
                actorId,
                reason,
                null,
                null,
                locationId,
                locationCode,
                1,
                quantity,
                product.getName(),
                notes
        );

        // Create stock movement record
        Map<String, Object> metadata = new HashMap<>();
        if (notes != null) {
            metadata.put("notes", notes);
        }
        metadata.put("inventory_id", inventoryId.toString());

        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .item(product)
                .locationType(locationType)
                .toLocationId(locationId)
                .previousQuantity(0)
                .currentQuantity(quantity)
                .quantityChange(quantity)
                .reason(reason)
                .actorId(actorId)
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();

        StockMovement savedMovement = stockMovementRepository.save(movement);
        eventOutboxService.createStockMovementEvent(savedMovement);

        // Update product active status
        boolean productChanged = updateProductActiveStatus(product);

        // Broadcast real-time update to connected clients
        broadcastService.broadcastInventoryUpdated(locationType.name(), product.getId().toString());
        broadcastService.broadcastAuditLogCreated(product.getId().toString());
        if (productChanged) {
            broadcastService.broadcastProductUpdated(List.of(product.getId().toString()));
        }

        return inventoryId;
    }

    /**
     * Remove inventory from a location with tracking.
     * Deletes the inventory record and creates a stock movement for audit.
     *
     * @param locationType The type of storage location
     * @param inventoryId The ID of the inventory record to remove
     * @param reason The reason for removal (typically REMOVED, DAMAGE, or SALE)
     * @param actorId The user performing the action (optional)
     * @param notes Additional notes (optional)
     */
    @Transactional
    public void removeInventoryWithTracking(LocationType locationType, UUID inventoryId,
                                            StockMovementReason reason, UUID actorId, String notes) {
        // Load the inventory to get current state
        Object inventory = loadInventory(locationType, inventoryId);
        int currentQuantity = getInventoryQuantity(inventory);
        Product product = getInventoryProduct(inventory);
        UUID locationId = getLocationId(inventory, locationType);
        String locationCode = resolveLocationCode(locationId, locationType);

        // Delete the inventory record
        deleteInventory(locationType, inventory);

        // Create audit log entry
        AuditLog auditLog = createAuditLog(
                actorId,
                reason,
                locationId,
                locationCode,
                null,
                null,
                1,
                currentQuantity,
                product.getName(),
                notes
        );

        // Create stock movement record
        Map<String, Object> metadata = new HashMap<>();
        if (notes != null) {
            metadata.put("notes", notes);
        }
        metadata.put("inventory_id", inventoryId.toString());

        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .item(product)
                .locationType(locationType)
                .fromLocationId(locationId)
                .previousQuantity(currentQuantity)
                .currentQuantity(0)
                .quantityChange(-currentQuantity)
                .reason(reason)
                .actorId(actorId)
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();

        StockMovement savedMovement = stockMovementRepository.save(movement);
        eventOutboxService.createStockMovementEvent(savedMovement);

        // Update product active status
        boolean productChanged = updateProductActiveStatus(product);

        // Broadcast real-time update to connected clients
        broadcastService.broadcastInventoryUpdated(locationType.name(), product.getId().toString());
        broadcastService.broadcastAuditLogCreated(product.getId().toString());
        if (productChanged) {
            broadcastService.broadcastProductUpdated(List.of(product.getId().toString()));
        }
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

    /**
     * Updates the product's denormalized quantity and active status based on total inventory.
     * Always persists so the products table stays in sync without a 9-table union query on reads.
     */
    private boolean updateProductActiveStatus(Product product) {
        int totalInventory = calculateTotalInventory(product.getId());
        boolean shouldBeActive = totalInventory > 0;

        boolean changed = !Objects.equals(product.getQuantity(), totalInventory)
                || !Objects.equals(product.getIsActive(), shouldBeActive);

        if (!changed) {
            return false;
        }

        product.setQuantity(totalInventory);
        product.setIsActive(shouldBeActive);
        productRepository.save(product);
        return true;
    }

    /**
     * Sync denormalized product totals (quantity/isActive) and broadcast changes.
     * Useful for flows that create StockMovements without using StockMovementService (e.g. shipments).
     */
    @Transactional
    public void syncProductTotals(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }

        List<Product> products = productRepository.findAllById(productIds);
        List<String> changedIds = new ArrayList<>();

        for (Product p : products) {
            if (updateProductActiveStatus(p)) {
                changedIds.add(p.getId().toString());
            }
        }

        if (!changedIds.isEmpty()) {
            broadcastService.broadcastProductUpdated(changedIds);
        }
    }

    @NonNull
    private Object loadInventory(LocationType locationType, UUID inventoryId) {
        return switch (locationType) {
            case BOX_BIN -> boxBinInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new BoxBinInventoryNotFoundException("BoxBin inventory not found: " + inventoryId));
            case CABINET -> cabinetInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new CabinetInventoryNotFoundException("Cabinet inventory not found: " + inventoryId));
            case DOUBLE_CLAW_MACHINE -> doubleClawMachineInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new DoubleClawMachineInventoryNotFoundException("DoubleClawMachine inventory not found: " + inventoryId));
            case FOUR_CORNER_MACHINE -> fourCornerMachineInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new FourCornerMachineInventoryNotFoundException("FourCornerMachine inventory not found: " + inventoryId));
            case GACHAPON -> throw new InvalidInventoryOperationException("Gachapon is display-only and does not support inventory");
            case KEYCHAIN_MACHINE -> throw new InvalidInventoryOperationException("Keychain Machine is display-only and does not support inventory");
            case PUSHER_MACHINE -> pusherMachineInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new PusherMachineInventoryNotFoundException("PusherMachine inventory not found: " + inventoryId));
            case RACK -> rackInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new RackInventoryNotFoundException("Rack inventory not found: " + inventoryId));
            case SINGLE_CLAW_MACHINE -> singleClawMachineInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new SingleClawMachineInventoryNotFoundException("SingleClawMachine inventory not found: " + inventoryId));
            case WINDOW -> windowInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new WindowInventoryNotFoundException("Window inventory not found: " + inventoryId));
            case NOT_ASSIGNED -> notAssignedInventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new NotAssignedInventoryNotFoundException("NotAssigned inventory not found: " + inventoryId));
        };
    }

    private int getInventoryQuantity(Object inventory) {
        return switch (inventory) {
            case BoxBinInventory bbi -> bbi.getQuantity();
            case SingleClawMachineInventory scmi -> scmi.getQuantity();
            case DoubleClawMachineInventory dcmi -> dcmi.getQuantity();
            case CabinetInventory ci -> ci.getQuantity();
            case RackInventory ri -> ri.getQuantity();
            case FourCornerMachineInventory fcmi -> fcmi.getQuantity();
            case PusherMachineInventory pmi -> pmi.getQuantity();
            case WindowInventory wi -> wi.getQuantity();
            case NotAssignedInventory nai -> nai.getQuantity();
            default -> throw new IllegalArgumentException("Unknown inventory type");
        };
    }

    private void setInventoryQuantity(Object inventory, int quantity) {
        switch (inventory) {
            case BoxBinInventory bbi -> bbi.setQuantity(quantity);
            case SingleClawMachineInventory scmi -> scmi.setQuantity(quantity);
            case DoubleClawMachineInventory dcmi -> dcmi.setQuantity(quantity);
            case CabinetInventory ci -> ci.setQuantity(quantity);
            case RackInventory ri -> ri.setQuantity(quantity);
            case FourCornerMachineInventory fcmi -> fcmi.setQuantity(quantity);
            case PusherMachineInventory pmi -> pmi.setQuantity(quantity);
            case WindowInventory wi -> wi.setQuantity(quantity);
            case NotAssignedInventory nai -> nai.setQuantity(quantity);
            default -> throw new IllegalArgumentException("Unknown inventory type");
        }
    }

    private void saveInventory(LocationType locationType, Object inventory) {
        switch (locationType) {
            case BOX_BIN -> boxBinInventoryRepository.save((BoxBinInventory) inventory);
            case CABINET -> cabinetInventoryRepository.save((CabinetInventory) inventory);
            case DOUBLE_CLAW_MACHINE -> doubleClawMachineInventoryRepository.save((DoubleClawMachineInventory) inventory);
            case FOUR_CORNER_MACHINE -> fourCornerMachineInventoryRepository.save((FourCornerMachineInventory) inventory);
            case GACHAPON -> throw new InvalidInventoryOperationException("Gachapon is display-only and does not support inventory");
            case KEYCHAIN_MACHINE -> throw new InvalidInventoryOperationException("Keychain Machine is display-only and does not support inventory");
            case PUSHER_MACHINE -> pusherMachineInventoryRepository.save((PusherMachineInventory) inventory);
            case RACK -> rackInventoryRepository.save((RackInventory) inventory);
            case SINGLE_CLAW_MACHINE -> singleClawMachineInventoryRepository.save((SingleClawMachineInventory) inventory);
            case WINDOW -> windowInventoryRepository.save((WindowInventory) inventory);
            case NOT_ASSIGNED -> notAssignedInventoryRepository.save((NotAssignedInventory) inventory);
        }
    }

    private void deleteInventory(LocationType locationType, Object inventory) {
        switch (locationType) {
            case BOX_BIN -> boxBinInventoryRepository.delete((BoxBinInventory) inventory);
            case CABINET -> cabinetInventoryRepository.delete((CabinetInventory) inventory);
            case DOUBLE_CLAW_MACHINE -> doubleClawMachineInventoryRepository.delete((DoubleClawMachineInventory) inventory);
            case FOUR_CORNER_MACHINE -> fourCornerMachineInventoryRepository.delete((FourCornerMachineInventory) inventory);
            case GACHAPON -> throw new InvalidInventoryOperationException("Gachapon is display-only and does not support inventory");
            case KEYCHAIN_MACHINE -> throw new InvalidInventoryOperationException("Keychain Machine is display-only and does not support inventory");
            case PUSHER_MACHINE -> pusherMachineInventoryRepository.delete((PusherMachineInventory) inventory);
            case RACK -> rackInventoryRepository.delete((RackInventory) inventory);
            case SINGLE_CLAW_MACHINE -> singleClawMachineInventoryRepository.delete((SingleClawMachineInventory) inventory);
            case WINDOW -> windowInventoryRepository.delete((WindowInventory) inventory);
            case NOT_ASSIGNED -> notAssignedInventoryRepository.delete((NotAssignedInventory) inventory);
        }
    }

    private UUID getLocationId(Object inventory, LocationType locationType) {
        return switch (locationType) {
            case BOX_BIN -> ((BoxBinInventory) inventory).getBoxBin().getId();
            case CABINET -> ((CabinetInventory) inventory).getCabinet().getId();
            case DOUBLE_CLAW_MACHINE -> ((DoubleClawMachineInventory) inventory).getDoubleClawMachine().getId();
            case FOUR_CORNER_MACHINE -> ((FourCornerMachineInventory) inventory).getFourCornerMachine().getId();
            case GACHAPON -> throw new InvalidInventoryOperationException("Gachapon is display-only and does not support inventory");
            case KEYCHAIN_MACHINE -> throw new InvalidInventoryOperationException("Keychain Machine is display-only and does not support inventory");
            case PUSHER_MACHINE -> ((PusherMachineInventory) inventory).getPusherMachine().getId();
            case RACK -> ((RackInventory) inventory).getRack().getId();
            case SINGLE_CLAW_MACHINE -> ((SingleClawMachineInventory) inventory).getSingleClawMachine().getId();
            case WINDOW -> ((WindowInventory) inventory).getWindow().getId();
            case NOT_ASSIGNED -> null;  // No location for NOT_ASSIGNED
        };
    }

    private com.mirai.inventoryservice.models.Product getInventoryProduct(Object inventory) {
        return switch (inventory) {
            case BoxBinInventory bbi -> bbi.getItem();
            case SingleClawMachineInventory scmi -> scmi.getItem();
            case DoubleClawMachineInventory dcmi -> dcmi.getItem();
            case CabinetInventory ci -> ci.getItem();
            case RackInventory ri -> ri.getItem();
            case FourCornerMachineInventory fcmi -> fcmi.getItem();
            case PusherMachineInventory pmi -> pmi.getItem();
            case WindowInventory wi -> wi.getItem();
            case NotAssignedInventory nai -> nai.getItem();
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
            case CABINET -> {
                Cabinet cabinet = cabinetRepository.findById(locationId)
                        .orElseThrow(() -> new CabinetNotFoundException("Cabinet not found: " + locationId));
                CabinetInventory inv = new CabinetInventory();
                inv.setCabinet(cabinet);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield cabinetInventoryRepository.save(inv);
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
            case FOUR_CORNER_MACHINE -> {
                FourCornerMachine machine = fourCornerMachineRepository.findById(locationId)
                        .orElseThrow(() -> new FourCornerMachineNotFoundException("FourCornerMachine not found: " + locationId));
                FourCornerMachineInventory inv = new FourCornerMachineInventory();
                inv.setFourCornerMachine(machine);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield fourCornerMachineInventoryRepository.save(inv);
            }
            case GACHAPON -> throw new InvalidInventoryOperationException("Gachapon is display-only and does not support inventory");
            case KEYCHAIN_MACHINE -> throw new InvalidInventoryOperationException("Keychain Machine is display-only and does not support inventory");
            case PUSHER_MACHINE -> {
                PusherMachine machine = pusherMachineRepository.findById(locationId)
                        .orElseThrow(() -> new PusherMachineNotFoundException("PusherMachine not found: " + locationId));
                PusherMachineInventory inv = new PusherMachineInventory();
                inv.setPusherMachine(machine);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield pusherMachineInventoryRepository.save(inv);
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
            case SINGLE_CLAW_MACHINE -> {
                SingleClawMachine machine = singleClawMachineRepository.findById(locationId)
                        .orElseThrow(() -> new SingleClawMachineNotFoundException("SingleClawMachine not found: " + locationId));
                SingleClawMachineInventory inv = new SingleClawMachineInventory();
                inv.setSingleClawMachine(machine);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield singleClawMachineInventoryRepository.save(inv);
            }
            case WINDOW -> {
                Window window = windowRepository.findById(locationId)
                        .orElseThrow(() -> new WindowNotFoundException("Window not found: " + locationId));
                WindowInventory inv = new WindowInventory();
                inv.setWindow(window);
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield windowInventoryRepository.save(inv);
            }
            case NOT_ASSIGNED -> {
                NotAssignedInventory inv = new NotAssignedInventory();
                inv.setItem(product);
                inv.setQuantity(quantity);
                yield notAssignedInventoryRepository.save(inv);
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
            case CabinetInventory ci -> ci.getId();
            case RackInventory ri -> ri.getId();
            case FourCornerMachineInventory fcmi -> fcmi.getId();
            case PusherMachineInventory pmi -> pmi.getId();
            case WindowInventory wi -> wi.getId();
            case NotAssignedInventory nai -> nai.getId();
            default -> throw new IllegalArgumentException("Unknown inventory type");
        };
    }

    /**
     * Resolve location UUID → code (for Kafka/UI use)
     * Calls the database function resolve_location_code() for consistency.
     */
    public String resolveLocationCode(UUID locationId, LocationType locationType) {
        // NOT_ASSIGNED locations have no locationId, but should return "NA"
        if (locationId == null) {
            return locationType == LocationType.NOT_ASSIGNED ? "NA" : null;
        }

        Object result = entityManager.createNativeQuery("SELECT resolve_location_code(:locationId, :locationType)")
                .setParameter("locationId", locationId)
                .setParameter("locationType", locationType.name())
                .getSingleResult();

        return result != null ? result.toString() : null;
    }

    /**
     * Calculate total inventory for a product across all storage locations.
     * Calls the database function calculate_total_inventory() for consistency.
     */
    public int calculateTotalInventory(UUID productId) {
        entityManager.flush();
        Object result = entityManager.createNativeQuery("SELECT calculate_total_inventory(:productId)")
                .setParameter("productId", productId)
                .getSingleResult();

        return ((Number) result).intValue();
    }

    /**
     * Create an audit log entry for a stock movement action.
     * productSummary is denormalized at write-time so the list view never needs to
     * lazy-load movements just to display the product name.
     */
    private AuditLog createAuditLog(
            UUID actorId,
            StockMovementReason reason,
            UUID fromLocationId,
            String fromLocationCode,
            UUID toLocationId,
            String toLocationCode,
            int itemCount,
            int totalQuantityMoved,
            String productSummary,
            String notes
    ) {
        com.mirai.inventoryservice.models.audit.User user = null;
        String actorName = null;
        if (actorId != null) {
            user = userRepository.findById(actorId).orElse(null);
            actorName = user != null ? user.getFullName() : null;
        }

        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .actorName(actorName)
                .reason(reason)
                .primaryFromLocationId(fromLocationId)
                .primaryFromLocationCode(fromLocationCode)
                .primaryToLocationId(toLocationId)
                .primaryToLocationCode(toLocationCode)
                .itemCount(itemCount)
                .totalQuantityMoved(totalQuantityMoved)
                .productSummary(productSummary)
                .notes(notes)
                .build();

        return auditLogRepository.save(auditLog);
    }
}
