package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.dtos.requests.BatchAdjustLineDTO;
import com.mirai.inventoryservice.dtos.requests.BatchAdjustStockRequestDTO;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final KujiBoxTierRepository kujiBoxTierRepository;
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
            KujiBoxTierRepository kujiBoxTierRepository,
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
        this.kujiBoxTierRepository = kujiBoxTierRepository;
        this.entityManager = entityManager;
        this.broadcastService = broadcastService;
        this.eventOutboxService = eventOutboxService;
    }

    /**
     * No-op: kuji prize counts are no longer stored in LocationInventory. Slip counts
     * (active/inactive) live on KujiBoxTier and are decoupled from regular inventory
     * movements, so there's nothing to lock against here. Kept as a stable signature
     * for callers that still invoke it during adjust/transfer flows.
     */
    public void validateKujiAllocation(UUID locationId, UUID productId, int newQuantity) {
        // intentionally empty
    }

    /**
     * Refuses inventory operations against CUSTOM kuji parent products. Their runtime is
     * the open KujiBox; they don't track LocationInventory directly.
     */
    public void rejectIfCustomKujiParent(Product product) {
        if (product != null
                && product.getKujiType() == com.mirai.inventoryservice.models.enums.KujiType.CUSTOM) {
            throw new InvalidInventoryOperationException(
                    "Custom kuji parent products do not track location inventory. "
                            + "Open a kuji box to manage prize stock instead.");
        }
    }

    /**
     * Refuses inventory operations against kuji prize children whose parent is non-CUSTOM
     * (i.e., PREMADE or untagged -- the PREMADE column tag is operationally unused in this
     * codebase, so vendor-shipped kuji parents typically have kuji_type=NULL). Their
     * per-prize counts live on shipment_items.received_quantity; they do not have
     * location_inventory rows. CUSTOM-parented children are exempt because KujiBoxService
     * writes transient location_inventory rows for them during close-box round-trip flows.
     */
    public void rejectIfKujiPrizeChild(Product product) {
        if (product == null) return;
        Product parent = product.getParent();
        if (parent != null
                && parent.getKujiType() != com.mirai.inventoryservice.models.enums.KujiType.CUSTOM) {
            throw new InvalidInventoryOperationException(
                    "Kuji prize children do not track location inventory. "
                            + "Edit the shipment item to correct received counts.");
        }
    }

    /**
     * Atomically adjust inventory for one or more products at a single location.
     * Creates one StockMovement row per line, all linked to a single AuditLog
     * with itemCount = adjustments.size() and totalQuantity = sum(|quantityChange|).
     *
     * Replaces the prior single-line adjustInventory; single adjusts are now a batch of 1.
     */
    @Transactional
    public List<StockMovement> batchAdjustInventory(BatchAdjustStockRequestDTO request) {
        List<BatchAdjustLineDTO> lines = request.getAdjustments();

        validateBatchAdjustSigns(lines);

        Map<UUID, LocationInventory> inventoryById = preloadInventories(
                lines.stream().map(BatchAdjustLineDTO::getInventoryId).collect(Collectors.toList())
        );

        // Validate ownership: every inventoryId must live at the supplied location.
        for (BatchAdjustLineDTO line : lines) {
            LocationInventory inv = inventoryById.get(line.getInventoryId());
            if (inv == null) {
                throw new InventoryNotFoundException("Inventory not found: " + line.getInventoryId());
            }
            if (!Objects.equals(inv.getLocation().getId(), request.getLocationId())) {
                throw new InvalidInventoryOperationException(
                        "Inventory " + line.getInventoryId() + " does not belong to location " + request.getLocationId());
            }
            rejectIfCustomKujiParent(inv.getProduct());
            rejectIfKujiPrizeChild(inv.getProduct());
        }

        // Validate quantities (subtract cannot exceed on-hand).
        for (BatchAdjustLineDTO line : lines) {
            LocationInventory inv = inventoryById.get(line.getInventoryId());
            int current = inv.getQuantity();
            int next = current + line.getQuantityChange();
            if (next < 0) {
                throw new InsufficientInventoryException(
                        String.format(
                                "Cannot reduce inventory %s by %d. Current quantity: %d",
                                line.getInventoryId(), Math.abs(line.getQuantityChange()), current));
            }
            validateKujiAllocation(inv.getLocation().getId(), inv.getProduct().getId(), next);
        }

        int totalQuantity = lines.stream().mapToInt(l -> Math.abs(l.getQuantityChange())).sum();
        LocationInventory first = inventoryById.get(lines.get(0).getInventoryId());
        UUID locationId = first.getLocation().getId();
        String locationCode = first.getLocation().getLocationCode();
        String storageLocationCode = first.getLocation().getStorageLocation().getCode();
        LocationType derivedLocationType = mapStorageLocationCodeToLocationType(storageLocationCode);

        String productSummary = lines.size() == 1
                ? first.getProduct().getName()
                : lines.size() + " products";

        AuditLog auditLog = createAuditLog(
                request.getActorId(),
                request.getReason(),
                null,
                null,
                locationId,
                locationCode,
                lines.size(),
                totalQuantity,
                productSummary,
                request.getNotes()
        );

        OffsetDateTime now = OffsetDateTime.now();
        List<LocationInventory> toSave = new ArrayList<>();
        List<LocationInventory> toDelete = new ArrayList<>();
        List<StockMovement> movements = new ArrayList<>(lines.size());
        Set<UUID> affectedProductIds = new HashSet<>();

        for (BatchAdjustLineDTO line : lines) {
            LocationInventory inv = inventoryById.get(line.getInventoryId());
            int currentQuantity = inv.getQuantity();
            int newQuantity = currentQuantity + line.getQuantityChange();

            if (newQuantity == 0) {
                toDelete.add(inv);
            } else {
                inv.setQuantity(newQuantity);
                toSave.add(inv);
            }

            Map<String, Object> metadata = new HashMap<>();
            if (request.getNotes() != null) {
                metadata.put("notes", request.getNotes());
            }
            metadata.put("inventory_id", line.getInventoryId().toString());
            if ("box".equalsIgnoreCase(line.getIntakeUnit())
                    && line.getIntakeQty() != null && line.getIntakeQty() > 0) {
                metadata.put("intake_unit", "box");
                metadata.put("intake_qty", line.getIntakeQty());
            }

            movements.add(StockMovement.builder()
                    .auditLog(auditLog)
                    .item(inv.getProduct())
                    .locationType(derivedLocationType)
                    .toLocationId(locationId)
                    .previousQuantity(currentQuantity)
                    .currentQuantity(newQuantity)
                    .quantityChange(line.getQuantityChange())
                    .reason(request.getReason())
                    .actorId(request.getActorId())
                    .at(now)
                    .metadata(metadata)
                    .build());

            affectedProductIds.add(inv.getProduct().getId());
        }

        if (!toSave.isEmpty()) {
            locationInventoryRepository.saveAll(toSave);
        }
        if (!toDelete.isEmpty()) {
            locationInventoryRepository.deleteAll(toDelete);
        }

        List<StockMovement> saved = stockMovementRepository.saveAll(movements);

        // Compute current totals once for every affected product (single GROUP BY),
        // then pass them into the outbox loop alongside the already-known location code.
        // Avoids 1× sumQuantityByProductId + 1× entityManager.flush per outbox event.
        entityManager.flush();
        Map<UUID, Integer> currentTotals = sumCurrentTotalsByProductIds(affectedProductIds);
        EventOutboxService.StockEventContext outboxCtx = new EventOutboxService.StockEventContext(
                Map.of(locationId, locationCode),
                currentTotals
        );
        for (StockMovement m : saved) {
            eventOutboxService.createStockMovementEvent(m, outboxCtx);
        }

        List<UUID> changedProductIds = applyProductActiveStatusFromTotals(affectedProductIds, currentTotals);

        broadcastService.broadcastInventoryUpdated(storageLocationCode, null);
        broadcastService.broadcastAuditLogCreated(null);
        if (!changedProductIds.isEmpty()) {
            broadcastService.broadcastProductUpdated(
                    changedProductIds.stream().map(UUID::toString).collect(Collectors.toList()));
        }

        return saved;
    }

    /**
     * Every line in a batch must share the same sign (all add or all subtract).
     * Mixing signs in a single submission is rejected at the service layer.
     */
    private void validateBatchAdjustSigns(List<BatchAdjustLineDTO> lines) {
        Integer signum = null;
        for (BatchAdjustLineDTO line : lines) {
            int q = line.getQuantityChange();
            if (q == 0) {
                throw new InvalidInventoryOperationException(
                        "quantityChange must be non-zero for inventory " + line.getInventoryId());
            }
            int s = Integer.signum(q);
            if (signum == null) {
                signum = s;
            } else if (!signum.equals(s)) {
                throw new InvalidInventoryOperationException(
                        "All adjustments in a batch must share the same sign (all add or all subtract)");
            }
        }
    }

    /**
     * One-shot fetch of LocationInventory rows by id with location, storage location,
     * product, and product.parent eager-loaded via JOIN FETCH. Replaces both the
     * per-line findById loops and the lazy-fetch N+1 that followed.
     */
    private Map<UUID, LocationInventory> preloadInventories(Collection<UUID> inventoryIds) {
        Map<UUID, LocationInventory> map = new LinkedHashMap<>();
        for (LocationInventory inv : locationInventoryRepository.findAllByIdWithGraph(inventoryIds)) {
            map.put(inv.getId(), inv);
        }
        return map;
    }

    /**
     * Single GROUP BY query returning current on-hand totals per product id.
     * Products with zero stock are present in the map with value 0 so callers
     * can mark them inactive without an additional query.
     */
    private Map<UUID, Integer> sumCurrentTotalsByProductIds(Set<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> totals = new HashMap<>();
        for (UUID id : productIds) {
            totals.put(id, 0);
        }
        for (Object[] row : locationInventoryRepository.sumQuantitiesByProductIds(productIds)) {
            UUID productId = (UUID) row[0];
            Long sum = (Long) row[1];
            totals.put(productId, sum == null ? 0 : sum.intValue());
        }
        return totals;
    }

    /**
     * Apply (quantity, isActive) updates to every affected product from the
     * pre-computed totals map, in a single {@code findAllById} fetch and a batched
     * write. Skips redundant sumQuantityByProductId calls that the legacy
     * per-product helper would have run.
     */
    private List<UUID> applyProductActiveStatusFromTotals(Set<UUID> productIds, Map<UUID, Integer> totals) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<UUID> changed = new ArrayList<>();
        List<Product> toSave = new ArrayList<>();
        for (Product p : productRepository.findAllById(productIds)) {
            int total = totals.getOrDefault(p.getId(), 0);
            boolean shouldBeActive = total > 0;
            boolean hasChange = !Objects.equals(p.getQuantity(), total)
                    || !Objects.equals(p.getIsActive(), shouldBeActive);
            if (hasChange) {
                p.setQuantity(total);
                p.setIsActive(shouldBeActive);
                toSave.add(p);
                changed.add(p.getId());
            }
        }
        if (!toSave.isEmpty()) {
            productRepository.saveAll(toSave);
        }
        return changed;
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

        Map<UUID, String> codes = new HashMap<>();
        codes.put(sourceLocationId, sourceLocationCode);
        if (destLocationId != null && destLocationCode != null) {
            codes.put(destLocationId, destLocationCode);
        }
        executeTransfer(request, sourceInventory, sourceQuantity, auditLog, true, codes);

        broadcastService.broadcastInventoryUpdated();
        broadcastService.broadcastAuditLogCreated();
    }

    /**
     * Transfer multiple inventory items between two locations in a single batch.
     * Creates ONE audit log entry covering all items, with itemCount = number of distinct products.
     *
     * Pre-loads all source LocationInventory rows in a single query and defers
     * product active-status updates to a single sweep after the loop.
     */
    @Transactional
    public void batchTransferInventory(BatchTransferInventoryRequestDTO batchRequest) {
        List<TransferInventoryRequestDTO> transfers = batchRequest.getTransfers();

        Map<UUID, LocationInventory> sourceById = preloadInventories(
                transfers.stream().map(TransferInventoryRequestDTO::getSourceInventoryId).collect(Collectors.toList())
        );

        TransferInventoryRequestDTO first = transfers.get(0);
        LocationInventory firstSource = sourceById.get(first.getSourceInventoryId());
        if (firstSource == null) {
            throw new InventoryNotFoundException("Source inventory not found: " + first.getSourceInventoryId());
        }

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

        Set<UUID> affectedProductIds = new HashSet<>();
        Map<UUID, String> codes = new HashMap<>();
        codes.put(sourceLocationId, sourceLocationCode);
        if (destLocationId != null && destLocationCode != null) {
            codes.put(destLocationId, destLocationCode);
        }

        for (TransferInventoryRequestDTO request : transfers) {
            LocationInventory sourceInventory = sourceById.get(request.getSourceInventoryId());
            if (sourceInventory == null) {
                throw new InventoryNotFoundException("Source inventory not found: " + request.getSourceInventoryId());
            }
            int sourceQuantity = sourceInventory.getQuantity();
            executeTransfer(request, sourceInventory, sourceQuantity, auditLog, false, codes);
            affectedProductIds.add(sourceInventory.getProduct().getId());
        }

        // Compute totals once for all affected products, then publish outbox-friendly
        // updates: in the transfer path, the per-row outbox events were already
        // created inside executeTransfer with the codes map but without precomputed
        // totals (they fall back to per-row sumQuantityByProductId). The status
        // update below still benefits from a single GROUP BY.
        entityManager.flush();
        Map<UUID, Integer> currentTotals = sumCurrentTotalsByProductIds(affectedProductIds);
        applyProductActiveStatusFromTotals(affectedProductIds, currentTotals);

        broadcastService.broadcastInventoryUpdated();
        broadcastService.broadcastAuditLogCreated();
    }

    /**
     * Core transfer logic: validates, moves inventory, creates withdrawal + deposit StockMovements
     * linked to the provided AuditLog.
     */
    private void executeTransfer(TransferInventoryRequestDTO request,
                                  LocationInventory sourceInventory, int sourceQuantity,
                                  AuditLog auditLog, boolean syncProductStatus,
                                  Map<UUID, String> locationCodesById) {
        if (sourceQuantity < request.getQuantity()) {
            throw new InsufficientInventoryException(
                    String.format("Cannot transfer %d items. Source only has %d available.",
                            request.getQuantity(), sourceQuantity)
            );
        }

        validateKujiAllocation(
                sourceInventory.getLocation().getId(),
                sourceInventory.getProduct().getId(),
                sourceQuantity - request.getQuantity());

        rejectIfKujiPrizeChild(sourceInventory.getProduct());

        LocationInventory destinationInventory;
        int destinationQuantity;
        UUID destinationInventoryId = request.getDestinationInventoryId();

        if (destinationInventoryId != null) {
            final UUID destInvId = destinationInventoryId;
            destinationInventory = locationInventoryRepository.findById(destinationInventoryId)
                    .orElseThrow(() -> new InventoryNotFoundException("Destination inventory not found: " + destInvId));
            destinationQuantity = destinationInventory.getQuantity();
        } else {
            UUID destLocationId = request.getDestinationLocationId();
            if (destLocationId == null) {
                // For NOT_ASSIGNED, find or create the NA location
                destLocationId = getNotAssignedLocationId();
            }

            final UUID destLocId = destLocationId;
            Location destLocation = locationRepository.findById(destLocationId)
                    .orElseThrow(() -> new LocationNotFoundException("Destination location not found: " + destLocId));

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

        EventOutboxService.StockEventContext outboxCtx =
                locationCodesById == null || locationCodesById.isEmpty()
                        ? EventOutboxService.StockEventContext.empty()
                        : new EventOutboxService.StockEventContext(locationCodesById, Map.of());
        eventOutboxService.createStockMovementEvent(savedWithdrawal, outboxCtx);
        eventOutboxService.createStockMovementEvent(savedDeposit, outboxCtx);

        if (syncProductStatus) {
            updateProductActiveStatus(sourceInventory.getProduct());
        }
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
     * Create new inventory at a location with tracking. Convenience overload — no intake metadata.
     */
    public UUID createInventoryWithTracking(LocationType locationType, UUID locationId,
                                            Product product, int quantity,
                                            StockMovementReason reason, UUID actorId, String notes) {
        return createInventoryWithTracking(locationType, locationId, product, quantity, reason, actorId, notes, null, null);
    }

    /**
     * Create new inventory at a location with tracking. Persists optional intake metadata
     * ({@code intakeUnit="box"}, {@code intakeQty}) so the audit log can render the
     * user's typed unit ("+2 boxes (72 packs)") instead of just the canonical pack count.
     */
    @Transactional
    public UUID createInventoryWithTracking(LocationType locationType, UUID locationId,
                                            Product product, int quantity,
                                            StockMovementReason reason, UUID actorId, String notes,
                                            String intakeUnit, Integer intakeQty) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        rejectIfCustomKujiParent(product);
        rejectIfKujiPrizeChild(product);

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
        if ("box".equalsIgnoreCase(intakeUnit) && intakeQty != null && intakeQty > 0) {
            metadata.put("intake_unit", "box");
            metadata.put("intake_qty", intakeQty);
        }

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

        validateKujiAllocation(locationId, product.getId(), 0);

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
            case "SHELVES" -> LocationType.SHELF;
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
