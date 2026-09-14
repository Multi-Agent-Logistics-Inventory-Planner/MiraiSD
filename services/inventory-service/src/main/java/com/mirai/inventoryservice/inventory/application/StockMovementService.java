package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.inventory.api.BatchAdjustLineDTO;
import com.mirai.inventoryservice.inventory.api.BatchAdjustStockRequestDTO;
import com.mirai.inventoryservice.inventory.api.BatchTransferInventoryRequestDTO;
import com.mirai.inventoryservice.inventory.api.TransferInventoryRequestDTO;
import com.mirai.inventoryservice.inventory.domain.InsufficientInventoryException;
import com.mirai.inventoryservice.inventory.domain.InvalidInventoryOperationException;
import com.mirai.inventoryservice.inventory.domain.InventoryNotFoundException;
import com.mirai.inventoryservice.sites.domain.LocationNotFoundException;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.application.CatalogQueries;
import com.mirai.inventoryservice.catalog.application.ProductRef;
import com.mirai.inventoryservice.catalog.application.ProductStockStateWriter;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.sites.application.LocationService;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.repositories.*;
import com.mirai.inventoryservice.services.EventOutboxService;
import com.mirai.inventoryservice.services.SupabaseBroadcastService;
import static com.mirai.inventoryservice.inventory.infrastructure.StockMovementSpecifications.withFilters;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
    private final CatalogQueries catalogQueries;
    private final ProductStockStateWriter productStockStateWriter;
    private final UserRepository userRepository;
    private final LocationInventoryRepository locationInventoryRepository;
    private final LocationRepository locationRepository;
    private final LocationService locationService;
    private final EntityManager entityManager;
    private final SupabaseBroadcastService broadcastService;
    private final EventOutboxService eventOutboxService;

    public StockMovementService(
            StockMovementRepository stockMovementRepository,
            AuditLogRepository auditLogRepository,
            CatalogQueries catalogQueries,
            ProductStockStateWriter productStockStateWriter,
            UserRepository userRepository,
            LocationInventoryRepository locationInventoryRepository,
            LocationRepository locationRepository,
            LocationService locationService,
            EntityManager entityManager,
            SupabaseBroadcastService broadcastService,
            @org.springframework.context.annotation.Lazy EventOutboxService eventOutboxService) {
        this.stockMovementRepository = stockMovementRepository;
        this.auditLogRepository = auditLogRepository;
        this.catalogQueries = catalogQueries;
        this.productStockStateWriter = productStockStateWriter;
        this.userRepository = userRepository;
        this.locationInventoryRepository = locationInventoryRepository;
        this.locationRepository = locationRepository;
        this.locationService = locationService;
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
                && product.getKujiType() == com.mirai.inventoryservice.catalog.domain.KujiType.CUSTOM) {
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
                && parent.getKujiType() != com.mirai.inventoryservice.catalog.domain.KujiType.CUSTOM) {
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
    /**
     * Site-scoped counterpart to {@link #batchAdjustInventory(BatchAdjustStockRequestDTO)}
     * (.specs/phase-6-inventory 6c, T-6c-12, AC-3): validates the request's {@code locationId}
     * belongs to {@code siteId} (404 for an unknown/foreign-site location, via
     * {@code LocationService.getLocationById}) before any write, then delegates to the existing
     * un-scoped method -- since every adjustment line is already required to belong to the same
     * {@code locationId} (see the ownership check above), validating the location once transitively
     * confines the whole batch to one site.
     * <p>
     * {@code actorId} MUST be the authenticated principal's backend user id (from
     * {@code AuthorizedSiteContext.backendUserId()}), never a client-supplied value -- the v1
     * mutation route is the caller and always passes this. Any {@code actorId} present on
     * {@code request} is a legacy compatibility field for the un-scoped overload only; it is
     * overwritten here, not trusted, per docs/specs/authentication-and-authorization.md#4.
     */
    @Transactional
    public List<StockMovement> batchAdjustInventory(UUID siteId, UUID actorId, BatchAdjustStockRequestDTO request) {
        locationService.getLocationById(siteId, request.getLocationId());
        request.setActorId(actorId);
        return batchAdjustInventory(request);
    }

    @Transactional
    public List<StockMovement> batchAdjustInventory(BatchAdjustStockRequestDTO request) {
        List<BatchAdjustLineDTO> lines = request.getAdjustments();

        validateBatchAdjustSigns(lines);

        List<UUID> requestedInventoryIds = lines.stream()
                .map(BatchAdjustLineDTO::getInventoryId).collect(Collectors.toList());
        lockInventoryRowsForUpdate(requestedInventoryIds);
        Map<UUID, LocationInventory> inventoryById = preloadInventories(requestedInventoryIds);

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
                    .site(inv.getSite())
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
     * Acquires PESSIMISTIC_WRITE locks on the given inventory ids, before any of them is read
     * elsewhere in this transaction (.specs/phase-6-inventory 6c, T-6c-6, F-6c-5; review-driven fix
     * round 3: adjustment/transfer lock-order conflict). Used by {@link #batchAdjustInventory} --
     * every id it locks is a caller-supplied reference to a row that already exists, with no
     * create-if-absent path.
     * <p>
     * Resolves each id to its {@link LocationProductKey} (unlocked scalar read -- both columns are
     * immutable once a row exists) and locks strictly in {@link #LOCATION_PRODUCT_KEY_ORDER}, via
     * the exact same {@link #ensureAndLockInventoryRow} routine the transfer paths use. This is
     * deliberate, not incidental: an id-ordered lock (this method's original T-6c-6 shape) and the
     * transfer paths' (location, product)-ordered lock are two different, independently-consistent
     * orderings that can still disagree with EACH OTHER -- for two products A and B at one location,
     * whichever of A/B's row ids sorts first can easily be the opposite of which sorts first by
     * (location, product), so a batch-adjust locking [A, B] by id and a concurrent transfer locking
     * [B, A] by (location, product) can each hold one and wait on the other (reproduced as a real
     * Postgres `deadlock detected` using the production lock methods). There must be exactly one
     * ordering domain shared by every writer that can lock more than one row in the same
     * transaction -- (location, product) is it, since it is the only domain that also works for a
     * not-yet-created transfer destination. Ids not resolving to an existing row are silently
     * skipped here; the existing "Inventory not found" check after this call (via
     * {@link #preloadInventories}) still catches that case with its established message, unchanged.
     * <p>
     * Must run before the first read of any of these ids in the same transaction: Hibernate will
     * not overwrite an already-managed entity's scalar state from a later query, so locking after
     * an earlier unlocked read would silently keep the stale, unlocked value rather than actually
     * serializing the write.
     */
    private void lockInventoryRowsForUpdate(Collection<UUID> ids) {
        Set<LocationProductKey> sortedKeys = new TreeSet<>(LOCATION_PRODUCT_KEY_ORDER);
        for (UUID id : new HashSet<>(ids)) {
            if (id == null) {
                continue;
            }
            locationInventoryRepository.findLocationAndProductIdById(id)
                    .ifPresent(k -> sortedKeys.add(new LocationProductKey(k.locationId(), k.productId())));
        }
        for (LocationProductKey key : sortedKeys) {
            ensureAndLockInventoryRow(key.locationId(), key.productId());
        }
    }

    /**
     * A location_inventory row's natural key. Used as the single sort/dedup/lock domain for every
     * row a transfer or batch-transfer touches -- source and destination alike, whether the row
     * already exists or must be created (.specs/phase-6-inventory 6c, review-driven fix round 2:
     * T-6c-6 P1 findings). Row id cannot serve this role: a not-yet-created destination has no id
     * until after it is created, and mixing "sort by id" for existing rows with "sort by
     * (location, product)" for new ones is exactly the two-domain inconsistency that let two
     * batches deadlock via concurrent speculative inserts in opposite orders.
     */
    private record LocationProductKey(UUID locationId, UUID productId) {
    }

    /** Total, deterministic order over {@link LocationProductKey}: by location id, then product id. */
    private static final Comparator<LocationProductKey> LOCATION_PRODUCT_KEY_ORDER =
            Comparator.comparing(LocationProductKey::locationId).thenComparing(LocationProductKey::productId);

    /**
     * One transfer request's resolved identity: its source and destination keys, computed once
     * during planning and reused both to build the global lock set and to look up the destination's
     * (already-locked, guaranteed-existing) id when {@code executeTransfer} runs.
     */
    private record TransferPlan(TransferInventoryRequestDTO request, LocationProductKey sourceKey,
                                 LocationProductKey destinationKey) {
    }

    /**
     * Resolves every transfer's source and destination to a {@link LocationProductKey}, failing
     * fast with the same not-found exceptions callers have always seen if a referenced row doesn't
     * exist -- no lock is taken here, and none is needed yet: a location_inventory row's own
     * (location, product) key is immutable once the row exists, so reading it unlocked is safe.
     * <p>
     * An explicit {@code destinationInventoryId} is resolved to its real (location, product) key
     * the same way as any other row, rather than special-cased out of the scheme -- its absence
     * surfaces the same {@link InventoryNotFoundException} it always has, just earlier (during
     * planning, not inside the old {@code executeTransfer} fallback). An implicit destination
     * (resolved by location) is keyed by (destLocationId, sourceProductId) -- it may not exist yet;
     * {@link #ensureAndLockInventoryRow} creates it if needed once locking actually runs.
     */
    private List<TransferPlan> planTransfers(List<TransferInventoryRequestDTO> transfers) {
        List<TransferPlan> plans = new ArrayList<>(transfers.size());
        for (TransferInventoryRequestDTO request : transfers) {
            UUID sourceId = request.getSourceInventoryId();
            LocationProductKey sourceKey = requireLocationProductKey(
                    sourceId, "Source inventory not found: " + sourceId);

            LocationProductKey destinationKey;
            UUID explicitDestinationId = request.getDestinationInventoryId();
            if (explicitDestinationId != null) {
                destinationKey = requireLocationProductKey(
                        explicitDestinationId, "Destination inventory not found: " + explicitDestinationId);
            } else {
                UUID destLocationId = request.getDestinationLocationId() != null
                        ? request.getDestinationLocationId() : getNotAssignedLocationId();
                destinationKey = new LocationProductKey(destLocationId, sourceKey.productId());
            }
            plans.add(new TransferPlan(request, sourceKey, destinationKey));
        }
        return plans;
    }

    /**
     * Scalar-only lookup of an existing row's (location, product) key, for planning -- unlocked and
     * safe because both columns are immutable once a row exists. Used for both a transfer's source
     * (which must already exist) and an explicit destination id (which the caller is claiming
     * already exists).
     */
    private LocationProductKey requireLocationProductKey(UUID inventoryId, String notFoundMessage) {
        return locationInventoryRepository.findLocationAndProductIdById(inventoryId)
                .map(ids -> new LocationProductKey(ids.locationId(), ids.productId()))
                .orElseThrow(() -> new InventoryNotFoundException(notFoundMessage));
    }

    /**
     * Locks every row a batch of transfers will touch -- every plan's source key and destination
     * key, deduplicated, in one strict, global {@link #LOCATION_PRODUCT_KEY_ORDER} pass -- and
     * returns the resulting real, locked id for each key. Processing the sorted set strictly in
     * order, one key at a time (never in parallel, never batched into one query), is what makes the
     * global-ordering property hold across concurrent transactions and batches: two overlapping
     * writers that need overlapping keys always request them in the same relative order, so neither
     * can hold what the other waits for -- closing the deadlock this record's reviewer reproduced
     * from two batches processing the same two new destinations in opposite request order.
     */
    private Map<LocationProductKey, UUID> lockPlannedRows(List<TransferPlan> plans) {
        Set<LocationProductKey> sortedKeys = new TreeSet<>(LOCATION_PRODUCT_KEY_ORDER);
        for (TransferPlan plan : plans) {
            sortedKeys.add(plan.sourceKey());
            sortedKeys.add(plan.destinationKey());
        }
        Map<LocationProductKey, UUID> lockedIds = new HashMap<>();
        for (LocationProductKey key : sortedKeys) {
            lockedIds.put(key, ensureAndLockInventoryRow(key.locationId(), key.productId()));
        }
        return lockedIds;
    }

    /**
     * Atomically finds-or-creates and locks the one location_inventory row at (locationId,
     * productId), closing both P1 findings the reviewer reproduced against the prior design:
     * <p>
     * (1) An existing row can no longer disappear between "found" and "locked" -- finding IS
     * locking here. {@code findIdByLocation_IdAndProduct_IdForUpdate} is a scalar,
     * {@code PESSIMISTIC_WRITE} read; there is no separate unlocked read step for a concurrent
     * delete (or delete-and-recreate) to race against. A miss means "no row exists right now," not
     * "a row exists but we haven't looked yet."
     * <p>
     * (2) Called only from {@link #lockPlannedRows}, strictly in {@link #LOCATION_PRODUCT_KEY_ORDER}
     * order across every row -- source and destination, existing and new -- in one global domain.
     * Two batches that both need to create the same brand-new keys in opposite request order still
     * process them in the same relative order once sorted, so Postgres's {@code ON CONFLICT}
     * speculative-insertion wait (below) is always one-directional between any two overlapping
     * writers -- it cannot form a cycle.
     * <p>
     * The loop always terminates, in practice within 1-2 iterations: the first pass either finds an
     * existing row (fast path, done in one query) or finds nothing and issues
     * {@code INSERT ... ON CONFLICT DO NOTHING}. That insert either commits our own new row --
     * visible to our own transaction immediately via read-your-own-writes, so the next iteration's
     * locked find succeeds -- or no-ops because a concurrent transaction's insert at the same key is
     * in flight. In the no-op case, Postgres's speculative-insertion protocol makes our {@code
     * INSERT} itself wait for that concurrent inserter to commit or abort before returning, so by
     * the time we loop back, that transaction has already resolved: either it committed (our next
     * locked find sees its row) or it aborted (our next iteration's own insert succeeds). There is
     * no interleaving in which this method spins indefinitely or returns an id for a row that then
     * turns out not to exist.
     */
    private UUID ensureAndLockInventoryRow(UUID locationId, UUID productId) {
        while (true) {
            Optional<UUID> lockedId = locationInventoryRepository
                    .findIdByLocation_IdAndProduct_IdForUpdate(locationId, productId);
            if (lockedId.isPresent()) {
                return lockedId.get();
            }
            UUID siteId = locationRepository.findSiteIdById(locationId)
                    .orElseThrow(() -> new LocationNotFoundException("Destination location not found: " + locationId));
            locationInventoryRepository.insertLocationInventoryIfAbsent(UUID.randomUUID(), locationId, siteId, productId);
        }
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
        Map<UUID, ProductStockStateWriter.StockState> stateByProductId = new HashMap<>();
        for (UUID productId : productIds) {
            int total = totals.getOrDefault(productId, 0);
            stateByProductId.put(productId, new ProductStockStateWriter.StockState(total, total > 0));
        }
        return productStockStateWriter.applyStockStateBatch(stateByProductId);
    }

    /**
     * Transfer inventory between two locations for a single item.
     * Creates TWO stock movement records (withdrawal + deposit) linked to a single audit log.
     * If destination inventory doesn't exist, creates it automatically.
     */
    /**
     * Site-scoped counterpart to {@link #transferInventory(TransferInventoryRequestDTO)}
     * (.specs/phase-6-inventory 6c, T-6c-12, AC-3): confirms the source inventory row belongs to
     * {@code siteId} (404 via {@link LocationInventoryRepository#findByIdAndSite_Id}) before any
     * write. {@link #requireSameSite} already forces the destination to match the source's site,
     * so checking the source alone is sufficient to confine the whole transfer to one site.
     * <p>
     * {@code actorId} MUST be the authenticated principal's backend user id, never a
     * client-supplied value -- see {@link #batchAdjustInventory(UUID, UUID, BatchAdjustStockRequestDTO)}.
     */
    @Transactional
    public void transferInventory(UUID siteId, UUID actorId, TransferInventoryRequestDTO request) {
        requireInventoryBelongsToSite(siteId, request.getSourceInventoryId());
        request.setActorId(actorId);
        transferInventory(request);
    }

    @Transactional
    public void transferInventory(TransferInventoryRequestDTO request) {
        List<TransferPlan> plans = planTransfers(List.of(request));
        Map<LocationProductKey, UUID> lockedIds = lockPlannedRows(plans);
        TransferPlan plan = plans.get(0);

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
        executeTransfer(request, sourceInventory, sourceQuantity, lockedIds.get(plan.destinationKey()), auditLog, true, codes);

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
    /**
     * Site-scoped counterpart to {@link #batchTransferInventory(BatchTransferInventoryRequestDTO)}
     * (.specs/phase-6-inventory 6c, T-6c-12, AC-3): every transfer's source must belong to
     * {@code siteId} before any write, same reasoning as the single-transfer overload.
     * <p>
     * {@code actorId} MUST be the authenticated principal's backend user id, never a
     * client-supplied value -- see {@link #batchAdjustInventory(UUID, UUID, BatchAdjustStockRequestDTO)}.
     */
    @Transactional
    public void batchTransferInventory(UUID siteId, UUID actorId, BatchTransferInventoryRequestDTO batchRequest) {
        for (TransferInventoryRequestDTO transfer : batchRequest.getTransfers()) {
            requireInventoryBelongsToSite(siteId, transfer.getSourceInventoryId());
            transfer.setActorId(actorId);
        }
        batchTransferInventory(batchRequest);
    }

    /** Throws {@link InventoryNotFoundException} (-> 404) if {@code inventoryId} isn't at {@code siteId}. */
    private void requireInventoryBelongsToSite(UUID siteId, UUID inventoryId) {
        locationInventoryRepository.findByIdAndSite_Id(inventoryId, siteId)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found: " + inventoryId));
    }

    @Transactional
    public void batchTransferInventory(BatchTransferInventoryRequestDTO batchRequest) {
        List<TransferInventoryRequestDTO> transfers = batchRequest.getTransfers();

        List<TransferPlan> plans = planTransfers(transfers);
        Map<LocationProductKey, UUID> lockedIds = lockPlannedRows(plans);

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

        // plans is index-aligned with transfers -- planTransfers built it in the same order.
        for (int i = 0; i < transfers.size(); i++) {
            TransferInventoryRequestDTO request = transfers.get(i);
            TransferPlan plan = plans.get(i);
            LocationInventory sourceInventory = sourceById.get(request.getSourceInventoryId());
            if (sourceInventory == null) {
                throw new InventoryNotFoundException("Source inventory not found: " + request.getSourceInventoryId());
            }
            int sourceQuantity = sourceInventory.getQuantity();
            executeTransfer(request, sourceInventory, sourceQuantity,
                    lockedIds.get(plan.destinationKey()), auditLog, false, codes);
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
     * <p>
     * {@code destinationInventoryId} is resolved and locked entirely upstream, by {@link
     * #planTransfers} and {@link #lockPlannedRows}, before this method ever runs -- there is no
     * more explicit-id-vs-implicit-location branching here, and no find-or-create logic (.specs
     * /phase-6-inventory 6c, review-driven fix round 2: T-6c-6 P1 findings). The {@code findById}
     * below can only ever hit a row this transaction already holds {@code PESSIMISTIC_WRITE} on; an
     * absence here would mean the locking invariant established upstream was violated, which is
     * exactly the class of bug this makes loud (an {@link IllegalStateException}) instead of silent.
     */
    private void executeTransfer(TransferInventoryRequestDTO request,
                                  LocationInventory sourceInventory, int sourceQuantity,
                                  UUID destinationInventoryId,
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

        LocationInventory destinationInventory = locationInventoryRepository.findById(destinationInventoryId)
                .orElseThrow(() -> new IllegalStateException(
                        "Destination inventory " + destinationInventoryId
                                + " should already exist and be locked at this point"));
        requireSameSite(sourceInventory.getSite(), destinationInventory.getSite());
        int destinationQuantity = destinationInventory.getQuantity();

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
                .site(sourceInventory.getSite())
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
                .site(destinationInventory.getSite())
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
     * Same-site-only transfer precondition (.specs/phase-6-inventory 6c, T-6c-4,
     * multi-site-data-and-api.md:67 "cross-site joins prohibited except ... transfer
     * workflows"). Audited inter-site transfers are Phase 7's; until then, a source/destination
     * pair whose sites differ must be rejected explicitly rather than silently written.
     */
    private void requireSameSite(Site sourceSite, Site destinationSite) {
        if (!Objects.equals(sourceSite.getId(), destinationSite.getId())) {
            throw new InvalidInventoryOperationException(
                    "Cannot transfer inventory across sites: source site "
                            + sourceSite.getCode() + " does not match destination site "
                            + destinationSite.getCode());
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
     * Get the NOT_ASSIGNED location ID for the default site.
     * Delegates to {@link LocationService#getNotAssignedLocation()} (R-8,
     * .specs/phase-6-inventory/log.md T-4) rather than duplicating the lookup.
     */
    private UUID getNotAssignedLocationId() {
        return locationService.getNotAssignedLocation().getId();
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
            location = locationRepository.findByStorageLocationCodeAndSiteId("NOT_ASSIGNED", locationService.getDefaultSiteId())
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
                .site(location.getStorageLocation().getSite())
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
                .site(inventory.getSite())
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
        return productStockStateWriter.applyStockState(product.getId(), totalInventory, shouldBeActive);
    }

    /**
     * Sync denormalized product totals (quantity/isActive) and broadcast changes.
     */
    @Transactional
    public void syncProductTotals(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }

        // Silently skip ids that no longer exist (a product deleted concurrently), matching the
        // previous productRepository.findAllById(...) behavior exactly.
        Set<UUID> existingIds = catalogQueries.findAllByIds(productIds).stream()
                .map(ProductRef::id)
                .collect(Collectors.toSet());
        List<String> changedIds = new ArrayList<>();

        for (UUID productId : productIds) {
            if (!existingIds.contains(productId)) {
                continue;
            }
            int totalInventory = calculateTotalInventory(productId);
            boolean shouldBeActive = totalInventory > 0;
            if (productStockStateWriter.applyStockState(productId, totalInventory, shouldBeActive)) {
                changedIds.add(productId.toString());
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
        com.mirai.inventoryservice.identity.domain.User user = null;
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
