package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.AdjustStockRequestDTO;
import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.dtos.requests.BatchTransferInventoryRequestDTO;
import com.mirai.inventoryservice.dtos.requests.TransferInventoryRequestDTO;
import com.mirai.inventoryservice.exceptions.*;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.Site;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.models.storage.StorageLocation;
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

/**
 * Service for managing stock movements and inventory operations.
 *
 * Uses the unified location_inventory table which consolidates all inventory
 * from storage locations (box bins, racks, machines, etc.).
 */
@Service
public class StockMovementService {
    private final StockMovementRepository stockMovementRepository;
    private final AuditLogRepository auditLogRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final LocationInventoryRepository locationInventoryRepository;
    private final LocationRepository locationRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final SiteRepository siteRepository;
    private final EntityManager entityManager;
    private final SupabaseBroadcastService broadcastService;
    private final EventOutboxService eventOutboxService;

    // Default site code - will be used until multi-site support is implemented
    private static final String DEFAULT_SITE_CODE = "MAIN";

    public StockMovementService(
            StockMovementRepository stockMovementRepository,
            AuditLogRepository auditLogRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            LocationInventoryRepository locationInventoryRepository,
            LocationRepository locationRepository,
            StorageLocationRepository storageLocationRepository,
            SiteRepository siteRepository,
            EntityManager entityManager,
            SupabaseBroadcastService broadcastService,
            @org.springframework.context.annotation.Lazy EventOutboxService eventOutboxService) {
        this.stockMovementRepository = stockMovementRepository;
        this.auditLogRepository = auditLogRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.locationInventoryRepository = locationInventoryRepository;
        this.locationRepository = locationRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.siteRepository = siteRepository;
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
        LocationInventory inventory = locationInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found: " + inventoryId));

        int currentQuantity = inventory.getQuantity();
        int newQuantity = currentQuantity + request.getQuantityChange();

        if (newQuantity < 0) {
            throw new InsufficientInventoryException(
                    String.format("Cannot reduce quantity by %d. Current quantity: %d",
                            Math.abs(request.getQuantityChange()), currentQuantity)
            );
        }

        if (newQuantity == 0) {
            locationInventoryRepository.delete(inventory);
        } else {
            inventory.setQuantity(newQuantity);
            locationInventoryRepository.save(inventory);
        }

        UUID locationId = inventory.getLocation().getId();
        String locationCode = inventory.getLocation().getLocationCode();
        String storageLocationCode = inventory.getLocation().getStorageLocation().getCode();
        LocationType derivedLocationType = mapStorageLocationCodeToLocationType(storageLocationCode);

        AuditLog auditLog = createAuditLog(
                request.getActorId(),
                request.getReason(),
                null,
                null,
                locationId,
                locationCode,
                1,
                Math.abs(request.getQuantityChange()),
                inventory.getProduct().getName(),
                request.getNotes()
        );

        Map<String, Object> metadata = new HashMap<>();
        if (request.getNotes() != null) {
            metadata.put("notes", request.getNotes());
        }
        metadata.put("inventory_id", inventoryId.toString());

        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .item(inventory.getProduct())
                .locationType(derivedLocationType)
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

        boolean productChanged = updateProductActiveStatus(savedMovement.getItem());

        broadcastService.broadcastInventoryUpdated(storageLocationCode, savedMovement.getItem().getId().toString());
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
        LocationInventory sourceInventory = locationInventoryRepository.findById(request.getSourceInventoryId())
                .orElseThrow(() -> new InventoryNotFoundException("Source inventory not found: " + request.getSourceInventoryId()));

        int sourceQuantity = sourceInventory.getQuantity();
        UUID sourceLocationId = sourceInventory.getLocation().getId();
        String sourceLocationCode = sourceInventory.getLocation().getLocationCode();

        UUID destLocationId = resolveDestinationLocationId(request);
        String destLocationCode = resolveLocationCode(destLocationId);

        AuditLog auditLog = createAuditLog(
                request.getActorId(),
                StockMovementReason.TRANSFER,
                sourceLocationId,
                sourceLocationCode,
                destLocationId,
                destLocationCode,
                1,
                request.getQuantity(),
                sourceInventory.getProduct().getName(),
                request.getNotes()
        );

        executeTransfer(request, sourceInventory, sourceQuantity, auditLog);

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

        TransferInventoryRequestDTO first = transfers.get(0);
        LocationInventory firstSource = locationInventoryRepository.findById(first.getSourceInventoryId())
                .orElseThrow(() -> new InventoryNotFoundException("Source inventory not found: " + first.getSourceInventoryId()));

        UUID sourceLocationId = firstSource.getLocation().getId();
        String sourceLocationCode = firstSource.getLocation().getLocationCode();

        UUID destLocationId = resolveDestinationLocationId(first);
        String destLocationCode = resolveLocationCode(destLocationId);

        int totalQuantity = transfers.stream().mapToInt(TransferInventoryRequestDTO::getQuantity).sum();

        String productSummary = transfers.size() == 1
                ? firstSource.getProduct().getName()
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
            LocationInventory sourceInventory = locationInventoryRepository.findById(request.getSourceInventoryId())
                    .orElseThrow(() -> new InventoryNotFoundException("Source inventory not found: " + request.getSourceInventoryId()));
            int sourceQuantity = sourceInventory.getQuantity();
            executeTransfer(request, sourceInventory, sourceQuantity, auditLog);
        }

        broadcastService.broadcastInventoryUpdated();
        broadcastService.broadcastAuditLogCreated();
    }

    /**
     * Core transfer logic: validates, moves inventory, creates withdrawal + deposit StockMovements
     * linked to the provided AuditLog.
     */
    private void executeTransfer(TransferInventoryRequestDTO request,
                                  LocationInventory sourceInventory, int sourceQuantity,
                                  AuditLog auditLog) {
        if (sourceQuantity < request.getQuantity()) {
            throw new InsufficientInventoryException(
                    String.format("Cannot transfer %d items. Source only has %d available.",
                            request.getQuantity(), sourceQuantity)
            );
        }

        LocationInventory destinationInventory;
        int destinationQuantity;
        UUID destinationInventoryId = request.getDestinationInventoryId();

        if (destinationInventoryId != null) {
            destinationInventory = locationInventoryRepository.findById(destinationInventoryId)
                    .orElseThrow(() -> new InventoryNotFoundException("Destination inventory not found: " + destinationInventoryId));
            destinationQuantity = destinationInventory.getQuantity();
        } else {
            UUID destLocationId = request.getDestinationLocationId();
            if (destLocationId == null) {
                // For NOT_ASSIGNED, find or create the NA location
                destLocationId = getNotAssignedLocationId();
            }

            Location destLocation = locationRepository.findById(destLocationId)
                    .orElseThrow(() -> new LocationNotFoundException("Destination location not found: " + destLocationId));

            // Check if inventory already exists at this location for this product
            destinationInventory = locationInventoryRepository
                    .findByLocation_IdAndProduct_Id(destLocationId, sourceInventory.getProduct().getId())
                    .orElseGet(() -> {
                        LocationInventory newInv = LocationInventory.builder()
                                .location(destLocation)
                                .site(destLocation.getStorageLocation().getSite())
                                .product(sourceInventory.getProduct())
                                .quantity(0)
                                .build();
                        return locationInventoryRepository.save(newInv);
                    });
            destinationQuantity = destinationInventory.getQuantity();
            destinationInventoryId = destinationInventory.getId();
        }

        int newSourceQuantity = sourceQuantity - request.getQuantity();
        destinationInventory.setQuantity(destinationQuantity + request.getQuantity());

        if (newSourceQuantity == 0) {
            locationInventoryRepository.delete(sourceInventory);
        } else {
            sourceInventory.setQuantity(newSourceQuantity);
            locationInventoryRepository.save(sourceInventory);
        }
        locationInventoryRepository.save(destinationInventory);

        UUID sourceLocationId = sourceInventory.getLocation().getId();
        UUID destinationLocationId = destinationInventory.getLocation().getId();

        String sourceStorageCode = sourceInventory.getLocation().getStorageLocation().getCode();
        String destStorageCode = destinationInventory.getLocation().getStorageLocation().getCode();
        LocationType sourceLocationType = mapStorageLocationCodeToLocationType(sourceStorageCode);
        LocationType destLocationType = mapStorageLocationCodeToLocationType(destStorageCode);

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
                .item(sourceInventory.getProduct())
                .locationType(sourceLocationType)
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
                .item(destinationInventory.getProduct())
                .locationType(destLocationType)
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

        updateProductActiveStatus(sourceInventory.getProduct());
    }

    /**
     * Resolves the physical destination location UUID for a transfer request.
     */
    private UUID resolveDestinationLocationId(TransferInventoryRequestDTO request) {
        if (request.getDestinationLocationId() != null) {
            return request.getDestinationLocationId();
        }
        if (request.getDestinationInventoryId() != null) {
            LocationInventory destInventory = locationInventoryRepository.findById(request.getDestinationInventoryId())
                    .orElseThrow(() -> new InventoryNotFoundException("Destination inventory not found: " + request.getDestinationInventoryId()));
            return destInventory.getLocation().getId();
        }
        // For NOT_ASSIGNED destination
        return getNotAssignedLocationId();
    }

    /**
     * Get the NOT_ASSIGNED location ID for the default site
     */
    private UUID getNotAssignedLocationId() {
        return storageLocationRepository.findByCodeAndSite_Code("NOT_ASSIGNED", DEFAULT_SITE_CODE)
                .map(sl -> locationRepository.findByStorageLocationCodeAndSiteId("NOT_ASSIGNED", sl.getSite().getId())
                        .stream().findFirst()
                        .orElseThrow(() -> new LocationNotFoundException("NOT_ASSIGNED location not found"))
                        .getId())
                .orElseThrow(() -> new StorageLocationNotFoundException("NOT_ASSIGNED storage location not found"));
    }

    /**
     * Create new inventory at a location with tracking.
     */
    @Transactional
    public UUID createInventoryWithTracking(LocationType locationType, UUID locationId,
                                            Product product, int quantity,
                                            StockMovementReason reason, UUID actorId, String notes) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        Location location;
        if (locationId != null) {
            location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new LocationNotFoundException("Location not found: " + locationId));
        } else {
            // NOT_ASSIGNED case
            location = locationRepository.findByStorageLocationCodeAndSiteId("NOT_ASSIGNED", getDefaultSiteId())
                    .stream().findFirst()
                    .orElseThrow(() -> new LocationNotFoundException("NOT_ASSIGNED location not found"));
        }

        // Check if storage location allows inventory
        if (location.getStorageLocation().getIsDisplayOnly()) {
            throw new InvalidInventoryOperationException(
                    location.getStorageLocation().getName() + " is display-only and does not support inventory");
        }

        LocationInventory inventory = LocationInventory.builder()
                .location(location)
                .site(location.getStorageLocation().getSite())
                .product(product)
                .quantity(quantity)
                .build();
        inventory = locationInventoryRepository.save(inventory);

        UUID inventoryId = inventory.getId();
        String locationCode = location.getLocationCode();
        String storageLocationCode = location.getStorageLocation().getCode();
        LocationType derivedLocationType = mapStorageLocationCodeToLocationType(storageLocationCode);

        AuditLog auditLog = createAuditLog(
                actorId,
                reason,
                null,
                null,
                location.getId(),
                locationCode,
                1,
                quantity,
                product.getName(),
                notes
        );

        Map<String, Object> metadata = new HashMap<>();
        if (notes != null) {
            metadata.put("notes", notes);
        }
        metadata.put("inventory_id", inventoryId.toString());

        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .item(product)
                .locationType(derivedLocationType)
                .toLocationId(location.getId())
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

        boolean productChanged = updateProductActiveStatus(product);

        broadcastService.broadcastInventoryUpdated(storageLocationCode, product.getId().toString());
        broadcastService.broadcastAuditLogCreated(product.getId().toString());
        if (productChanged) {
            broadcastService.broadcastProductUpdated(List.of(product.getId().toString()));
        }

        return inventoryId;
    }

    /**
     * Remove inventory from a location with tracking.
     */
    @Transactional
    public void removeInventoryWithTracking(LocationType locationType, UUID inventoryId,
                                            StockMovementReason reason, UUID actorId, String notes) {
        LocationInventory inventory = locationInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found: " + inventoryId));

        int currentQuantity = inventory.getQuantity();
        Product product = inventory.getProduct();
        Location location = inventory.getLocation();
        UUID locationId = location.getId();
        String locationCode = location.getLocationCode();
        String storageLocationCode = location.getStorageLocation().getCode();
        LocationType derivedLocationType = mapStorageLocationCodeToLocationType(storageLocationCode);

        locationInventoryRepository.delete(inventory);

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

        Map<String, Object> metadata = new HashMap<>();
        if (notes != null) {
            metadata.put("notes", notes);
        }
        metadata.put("inventory_id", inventoryId.toString());

        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .item(product)
                .locationType(derivedLocationType)
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

        boolean productChanged = updateProductActiveStatus(product);

        broadcastService.broadcastInventoryUpdated(storageLocationCode, product.getId().toString());
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
     * Get the default site ID
     */
    private UUID getDefaultSiteId() {
        return siteRepository.findByCode(DEFAULT_SITE_CODE)
                .orElseThrow(() -> new SiteNotFoundException("Default site not found: " + DEFAULT_SITE_CODE))
                .getId();
    }

    /**
     * Maps storage location code to LocationType enum for backward compatibility.
     * This mapping is needed until LocationType is fully deprecated from StockMovement.
     */
    private LocationType mapStorageLocationCodeToLocationType(String storageLocationCode) {
        return switch (storageLocationCode) {
            case "BOX_BINS" -> LocationType.BOX_BIN;
            case "RACKS" -> LocationType.RACK;
            case "CABINETS" -> LocationType.CABINET;
            case "WINDOWS" -> LocationType.WINDOW;
            case "SINGLE_CLAW" -> LocationType.SINGLE_CLAW_MACHINE;
            case "DOUBLE_CLAW" -> LocationType.DOUBLE_CLAW_MACHINE;
            case "FOUR_CORNER" -> LocationType.FOUR_CORNER_MACHINE;
            case "PUSHER" -> LocationType.PUSHER_MACHINE;
            case "GACHAPON" -> LocationType.GACHAPON;
            case "KEYCHAIN" -> LocationType.KEYCHAIN_MACHINE;
            case "NOT_ASSIGNED" -> LocationType.NOT_ASSIGNED;
            default -> throw new IllegalArgumentException("Unknown storage location code: " + storageLocationCode);
        };
    }

    /**
     * Updates the product's denormalized quantity and active status based on total inventory.
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

    /**
     * Resolve location UUID → code
     */
    public String resolveLocationCode(UUID locationId) {
        if (locationId == null) {
            return null;
        }
        return locationRepository.findById(locationId)
                .map(Location::getLocationCode)
                .orElse(null);
    }

    /**
     * Resolve location UUID → code (backward compatible signature)
     */
    public String resolveLocationCode(UUID locationId, LocationType locationType) {
        if (locationId == null) {
            return locationType == LocationType.NOT_ASSIGNED ? "NA" : null;
        }
        return resolveLocationCode(locationId);
    }

    /**
     * Calculate total inventory for a product across all storage locations.
     */
    public int calculateTotalInventory(UUID productId) {
        entityManager.flush();
        Integer total = locationInventoryRepository.sumQuantityByProductId(productId);
        return total != null ? total : 0;
    }

    /**
     * Create an audit log entry for a stock movement action.
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
