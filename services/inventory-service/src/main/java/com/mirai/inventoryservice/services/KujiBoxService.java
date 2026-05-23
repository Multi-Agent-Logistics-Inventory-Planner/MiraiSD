package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.kuji.AddSlipRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.CloseKujiBoxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.DeletePrizeRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.MoveSlipsRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.NewKujiBoxTierDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.OpenKujiBoxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.AddKujiTierRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.PatchKujiTierRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.RecordDrawRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.TransferInMoreRequestDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiAllocationByLocationDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiAllocationByProductDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiBoxResponseDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiBoxTierResponseDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiDailyPayoutsResponseDTO;
import com.mirai.inventoryservice.exceptions.InsufficientInventoryException;
import com.mirai.inventoryservice.exceptions.InventoryNotFoundException;
import com.mirai.inventoryservice.exceptions.LocationNotFoundException;
import com.mirai.inventoryservice.exceptions.ProductNotFoundException;
import com.mirai.inventoryservice.models.MachineDisplay;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.KujiBoxStatus;
import com.mirai.inventoryservice.models.enums.KujiType;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.NotificationSeverity;
import com.mirai.inventoryservice.models.enums.NotificationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import com.mirai.inventoryservice.models.kuji.KujiBox;
import com.mirai.inventoryservice.models.kuji.KujiBoxTier;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.repositories.KujiBoxRepository;
import com.mirai.inventoryservice.repositories.KujiBoxTierRepository;
import com.mirai.inventoryservice.repositories.LocationInventoryRepository;
import com.mirai.inventoryservice.repositories.LocationRepository;
import com.mirai.inventoryservice.repositories.MachineDisplayRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing custom kuji boxes — open / close / reopen lifecycle, draw recording,
 * undo, slip adjustments, transfer-in-more, and tier patches.
 *
 * Design notes:
 * - All inventory mutations route through this service's transfer-pair pattern (mirrors
 *   {@link StockMovementService#transferInventory}). One AuditLog wraps the StockMovements.
 * - Draws emit a parent AuditLog of reason KUJI_PRIZE_WON with one StockMovement per draw line.
 *   Linked tiers decrement the linked product's LocationInventory at the box's location.
 *   Free-text tiers emit a quantityChange=0 movement against the box parent product so the
 *   audit log still records the event.
 * - Notifications are emitted in try/catch so messaging-pipeline failures cannot roll back
 *   the inventory transaction (mirrors {@link MachineDisplayService} pattern).
 */
@Service
@Slf4j
public class KujiBoxService {

    /** Tier sort: highest price first, nulls (priceless tiers) last, label asc as tiebreak. */
    private static final Comparator<KujiBoxTier> TIER_ORDER = Comparator
            .comparing(KujiBoxTier::getPrice,
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(KujiBoxTier::getLabel,
                    Comparator.nullsLast(Comparator.naturalOrder()));

    private final KujiBoxRepository kujiBoxRepository;
    private final KujiBoxTierRepository kujiBoxTierRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final LocationInventoryRepository locationInventoryRepository;
    private final MachineDisplayRepository machineDisplayRepository;
    private final AuditLogRepository auditLogRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SupabaseBroadcastService broadcastService;
    private final EventOutboxService eventOutboxService;
    private final StockMovementService stockMovementService;
    private final EntityManager entityManager;

    private final ProductService productService;

    public KujiBoxService(
            KujiBoxRepository kujiBoxRepository,
            KujiBoxTierRepository kujiBoxTierRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            LocationInventoryRepository locationInventoryRepository,
            MachineDisplayRepository machineDisplayRepository,
            AuditLogRepository auditLogRepository,
            StockMovementRepository stockMovementRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            SupabaseBroadcastService broadcastService,
            EventOutboxService eventOutboxService,
            StockMovementService stockMovementService,
            EntityManager entityManager,
            ProductService productService
    ) {
        this.kujiBoxRepository = kujiBoxRepository;
        this.kujiBoxTierRepository = kujiBoxTierRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.locationInventoryRepository = locationInventoryRepository;
        this.machineDisplayRepository = machineDisplayRepository;
        this.auditLogRepository = auditLogRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.broadcastService = broadcastService;
        this.eventOutboxService = eventOutboxService;
        this.stockMovementService = stockMovementService;
        this.entityManager = entityManager;
        this.productService = productService;
    }

    // ===================== Open / Close / Reopen =====================

    @Transactional
    public KujiBoxResponseDTO openBox(OpenKujiBoxRequestDTO request) {
        Product product = productRepository.findByIdWithCategories(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + request.getProductId()));

        if (product.getKujiType() != KujiType.CUSTOM) {
            throw new IllegalArgumentException(
                    "Product is not a custom kuji (kujiType must be CUSTOM): " + product.getName());
        }

        kujiBoxRepository.findByProductIdAndStatus(product.getId(), KujiBoxStatus.OPEN)
                .ifPresent(b -> {
                    throw new IllegalStateException(
                            "An OPEN box already exists for this kuji: " + b.getId());
                });

        Location boxLocation = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new LocationNotFoundException(
                        "Location not found: " + request.getLocationId()));

        MachineDisplay machineDisplay = null;
        if (request.getMachineDisplayId() != null) {
            machineDisplay = machineDisplayRepository.findById(request.getMachineDisplayId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "MachineDisplay not found: " + request.getMachineDisplayId()));
            if (machineDisplay.getEndedAt() != null) {
                throw new IllegalArgumentException(
                        "MachineDisplay is no longer active: " + request.getMachineDisplayId());
            }
        }

        OffsetDateTime now = OffsetDateTime.now();

        // Auto-attach a machine display when opening at a machine without an explicit
        // displayId. Reuses an existing active display for the kuji parent if present.
        // Why: the location-card blue dot is driven by active machine_display rows; opening
        // a kuji at a machine should make it visible there without a separate "set on display" step.
        if (machineDisplay == null && isMachineLocation(boxLocation)) {
            LocationType locationType = mapLocationType(boxLocation);
            machineDisplay = machineDisplayRepository
                    .findActiveByLocationTypeAndMachineId(locationType, boxLocation.getId())
                    .stream()
                    .filter(d -> d.getProduct() != null
                            && d.getProduct().getId().equals(product.getId()))
                    .findFirst()
                    .orElseGet(() -> machineDisplayRepository.save(MachineDisplay.builder()
                            .location(boxLocation)
                            .locationType(locationType)
                            .machineId(boxLocation.getId())
                            .product(product)
                            .startedAt(now)
                            .actorId(request.getActorId())
                            .build()));
        }

        KujiBox box = KujiBox.builder()
                .product(product)
                .location(boxLocation)
                .machineDisplay(machineDisplay)
                .status(KujiBoxStatus.OPEN)
                .label(request.getLabel())
                .notes(request.getNotes())
                .openedAt(now)
                .openedBy(request.getActorId())
                .tiers(new ArrayList<>())
                .build();
        box = kujiBoxRepository.save(box);

        // Persist tiers (cascade on box.tiers). Auto-created prize products are
        // birthed under the kuji parent here so the tier can reference them.
        List<KujiBoxTier> tierEntities = new ArrayList<>();
        for (int idx = 0; idx < request.getTiers().size(); idx++) {
            NewKujiBoxTierDTO tierDto = request.getTiers().get(idx);
            boolean autoCreate = Boolean.TRUE.equals(tierDto.getAutoCreate());

            // Mutual exclusion: linkedProductId vs autoCreate.
            if (autoCreate && tierDto.getLinkedProductId() != null) {
                throw new IllegalArgumentException(
                        "Tier '" + tierDto.getLabel() + "': cannot set both linkedProductId and autoCreate=true.");
            }
            if (autoCreate && (tierDto.getProductName() == null || tierDto.getProductName().isBlank())) {
                throw new IllegalArgumentException(
                        "Tier '" + tierDto.getLabel() + "': productName is required when autoCreate=true.");
            }

            Product linkedProduct = null;
            if (tierDto.getLinkedProductId() != null) {
                linkedProduct = productRepository.findById(tierDto.getLinkedProductId())
                        .orElseThrow(() -> new ProductNotFoundException(
                                "Linked product not found: " + tierDto.getLinkedProductId()));
            } else if (autoCreate) {
                // Birth a child product under the kuji parent. Quantity stays 0 on the
                // Product entity — the prize stock lives only on the tier counters
                // (activeCount + inactiveCount). No LocationInventory row is created.
                linkedProduct = productService.createProduct(
                        null,                                       // sku
                        null,                                       // categoryId — inherits from parent
                        product.getId(),                            // parentId
                        tierDto.getLetter(),                        // letter
                        null,                                       // templateQuantity
                        tierDto.getProductName().trim(),            // name
                        null,                                       // description
                        0,                                          // reorderPoint
                        0,                                          // targetStockLevel
                        14,                                         // leadTimeDays
                        null,                                       // unitCost
                        tierDto.getProductMsrp(),                   // msrp
                        tierDto.getProductImageUrl(),               // imageUrl
                        null,                                       // notes
                        null,                                       // initialStock — kuji counters own this
                        null,                                       // kujiType
                        null,                                       // kujiSlackWebhookUrl
                        null                                        // packsPerBox
                );
                linkedProduct.setIsActive(true);
                linkedProduct = productRepository.save(linkedProduct);
            }

            KujiBoxTier tier = KujiBoxTier.builder()
                    .box(box)
                    .label(tierDto.getLabel())
                    .letter(tierDto.getLetter())
                    .linkedProduct(linkedProduct)
                    .activeCount(tierDto.getActiveCount() != null ? tierDto.getActiveCount() : 0)
                    .inactiveCount(tierDto.getInactiveCount() != null ? tierDto.getInactiveCount() : 0)
                    .price(tierDto.getPrice())
                    .autoCreatedProduct(autoCreate)
                    .build();
            tierEntities.add(tier);
        }
        box.getTiers().addAll(tierEntities);
        kujiBoxTierRepository.saveAll(tierEntities);
        // Flush so tiers receive ids before transfer metadata references them
        entityManager.flush();

        // For each linked tier, materialize (slip count + held-back quantity) units of
        // inventory at the box's location. Pre-existing products transfer from a source
        // location; auto-created prize children are birthed directly at the box location
        // (no source — they didn't exist anywhere before).
        Set<UUID> affectedProductIds = new HashSet<>();
        for (int i = 0; i < request.getTiers().size(); i++) {
            NewKujiBoxTierDTO dto = request.getTiers().get(i);
            KujiBoxTier tier = box.getTiers().get(i);

            if (tier.getLinkedProduct() == null) {
                continue;
            }
            int slipCount = tier.getActiveCount() == null ? 0 : tier.getActiveCount();
            int heldBack = dto.getInactiveCount() == null ? 0 : dto.getInactiveCount();
            int totalQuantity = slipCount + heldBack;
            if (totalQuantity <= 0) {
                continue;
            }

            if (Boolean.TRUE.equals(tier.getAutoCreatedProduct())) {
                // Auto-created kuji prize children don't get a LocationInventory row.
                // Their quantity lives in the tier counters (activeCount + inactiveCount).
                // Keep an INITIAL_STOCK audit row with quantityChange=0 for traceability.
                Product newChild = tier.getLinkedProduct();
                Map<String, Object> metadata = buildTierMetadata(box, tier, "open_box_auto_create");
                metadata.put("activeCount", slipCount);
                metadata.put("inactiveCount", heldBack);

                StockMovement birth = StockMovement.builder()
                        .item(newChild)
                        .locationType(LocationType.NOT_ASSIGNED)
                        .fromLocationId(null)
                        .toLocationId(null)
                        .previousQuantity(0)
                        .currentQuantity(0)
                        .quantityChange(0)
                        .reason(StockMovementReason.INITIAL_STOCK)
                        .actorId(request.getActorId())
                        .at(now)
                        .metadata(metadata)
                        .build();
                stockMovementRepository.save(birth);
                continue;
            }

            if (dto.getSourceLocationId() == null) {
                throw new IllegalArgumentException(
                        "Tier '" + tier.getLabel() + "' has a linked product but no sourceLocationId");
            }

            Map<String, Object> metadata = buildTierMetadata(box, tier, "open_box_source_removal");
            metadata.put("activeCount", slipCount);
            metadata.put("inactiveCount", heldBack);

            // Pull prizes from the source location into the kuji's internal counters.
            // No deposit at the machine — kuji prize counts live only on the tier.
            executeKujiSourceRemoval(
                    dto.getSourceLocationId(),
                    tier.getLinkedProduct(),
                    totalQuantity,
                    request.getActorId(),
                    metadata,
                    box.getProduct().getId()
            );
            affectedProductIds.add(tier.getLinkedProduct().getId());
        }

        // Sync product totals + broadcasts
        if (!affectedProductIds.isEmpty()) {
            stockMovementService.syncProductTotals(new ArrayList<>(affectedProductIds));
            broadcastService.broadcastInventoryUpdated();
            broadcastService.broadcastAuditLogCreated();
        }

        entityManager.flush();
        return toResponseDTO(box);
    }

    @Transactional
    public KujiBoxResponseDTO closeBox(UUID boxId, CloseKujiBoxRequestDTO request) {
        KujiBox box = kujiBoxRepository.findByIdWithTiers(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));

        if (box.getStatus() != KujiBoxStatus.OPEN) {
            throw new IllegalStateException("Box is not OPEN: " + boxId);
        }

        Map<UUID, UUID> destinationByTierId = new HashMap<>();
        if (request.getTransferOutTargets() != null) {
            for (CloseKujiBoxRequestDTO.TierTransferDestination t : request.getTransferOutTargets()) {
                destinationByTierId.put(t.getTierId(), t.getDestinationLocationId());
            }
        }

        Set<UUID> affectedProductIds = new HashSet<>();
        for (KujiBoxTier tier : box.getTiers()) {
            int active = tier.getActiveCount() != null ? tier.getActiveCount() : 0;
            int inactive = tier.getInactiveCount() != null ? tier.getInactiveCount() : 0;
            int leftover = active + inactive;

            if (Boolean.TRUE.equals(tier.getAutoCreatedProduct())
                    && tier.getLinkedProduct() != null) {
                // Auto-created prize products are scoped to this one box. The kuji counters
                // are zeroed and the product soft-deleted. No LocationInventory write —
                // these prizes never lived in location_inventory in the decoupled model.
                Product child = tier.getLinkedProduct();
                if (leftover > 0) {
                    Map<String, Object> metadata = buildTierMetadata(box, tier, "close_box_auto_remove");
                    metadata.put("activeCountAtClose", active);
                    metadata.put("inactiveCountAtClose", inactive);
                    StockMovement removal = StockMovement.builder()
                            .item(child)
                            .locationType(LocationType.NOT_ASSIGNED)
                            .fromLocationId(null)
                            .toLocationId(null)
                            .previousQuantity(0)
                            .currentQuantity(0)
                            .quantityChange(0)
                            .reason(StockMovementReason.REMOVED)
                            .actorId(request.getActorId())
                            .at(OffsetDateTime.now())
                            .metadata(metadata)
                            .build();
                    stockMovementRepository.save(removal);
                }
                tier.setActiveCount(0);
                tier.setInactiveCount(0);
                kujiBoxTierRepository.save(tier);
                child.setIsActive(false);
                productRepository.save(child);
                affectedProductIds.add(child.getId());
                continue;
            }

            if (tier.getLinkedProduct() == null) {
                // Free-text tier — no product to transfer; just zero the counters.
                tier.setActiveCount(0);
                tier.setInactiveCount(0);
                kujiBoxTierRepository.save(tier);
                continue;
            }

            if (leftover <= 0) {
                continue;
            }
            UUID destinationLocationId = destinationByTierId.get(tier.getId());
            if (destinationLocationId == null) {
                throw new IllegalArgumentException(
                        "Tier " + tier.getLabel() + ": provide a destination for "
                                + leftover + " units of " + tier.getLinkedProduct().getName());
            }

            Location destinationLocation = locationRepository.findById(destinationLocationId)
                    .orElseThrow(() -> new LocationNotFoundException(
                            "Destination location not found: " + destinationLocationId));

            // Transfer the kuji's internal counter back into regular inventory. Source is
            // synthetic (the kuji), so only the destination side gets a real LocationInventory
            // write; the audit row carries the active/inactive split for reopen.
            Map<String, Object> metadata = buildTierMetadata(box, tier, "close_box_return_to_inventory");
            metadata.put("activeCountAtClose", active);
            metadata.put("inactiveCountAtClose", inactive);
            executeKujiCounterReturn(
                    destinationLocation,
                    tier.getLinkedProduct(),
                    leftover,
                    request.getActorId(),
                    metadata,
                    box.getProduct().getId()
            );
            tier.setActiveCount(0);
            tier.setInactiveCount(0);
            kujiBoxTierRepository.save(tier);
            affectedProductIds.add(tier.getLinkedProduct().getId());
        }

        // Mirror openBox's auto-attach: end the machine display tied to this box so
        // closing at a machine drops the location-card blue dot without a separate
        // "remove from display" step.
        boolean endedDisplay = false;
        MachineDisplay attachedDisplay = box.getMachineDisplay();
        if (attachedDisplay != null && attachedDisplay.getEndedAt() == null) {
            attachedDisplay.setEndedAt(OffsetDateTime.now());
            machineDisplayRepository.save(attachedDisplay);
            endedDisplay = true;
        }

        box.setStatus(KujiBoxStatus.CLOSED);
        box.setClosedAt(OffsetDateTime.now());
        box.setClosedBy(request.getActorId());
        box = kujiBoxRepository.save(box);

        if (!affectedProductIds.isEmpty()) {
            stockMovementService.syncProductTotals(new ArrayList<>(affectedProductIds));
        }
        if (!affectedProductIds.isEmpty() || endedDisplay) {
            broadcastService.broadcastInventoryUpdated();
            broadcastService.broadcastAuditLogCreated();
        }

        entityManager.flush();
        return toResponseDTO(box);
    }

    @Transactional
    public KujiBoxResponseDTO reopenBox(UUID boxId, UUID actorId) {
        KujiBox box = kujiBoxRepository.findByIdWithTiers(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));

        if (box.getStatus() != KujiBoxStatus.CLOSED) {
            throw new IllegalStateException("Box is not CLOSED: " + boxId);
        }

        kujiBoxRepository.findByProductIdAndStatus(box.getProduct().getId(), KujiBoxStatus.OPEN)
                .ifPresent(other -> {
                    throw new IllegalStateException(
                            "A newer OPEN box already exists for this kuji: " + other.getId());
                });

        // Reverse close-box movements that went OUT to a destination.
        //
        // New model: close emits a TRANSFER with fromLocationId=null, toLocationId=destination,
        // metadata.activeCountAtClose / inactiveCountAtClose. Reopen decrements the destination
        // and restores those counts onto the tier.
        //
        // Legacy model (pre-decoupling): close emitted a withdrawal half with from=box.location,
        // to=destination. Reverse those by transferring back from the destination to the box's
        // LocationInventory. Both shapes are handled.
        OffsetDateTime closedAt = box.getClosedAt();
        Set<UUID> affectedProductIds = new HashSet<>();
        if (closedAt != null) {
            List<StockMovement> closeMovements = findKujiBoxMovementsAtOrAfter(boxId, closedAt);

            for (StockMovement mv : closeMovements) {
                if (mv.getReason() != StockMovementReason.TRANSFER) {
                    continue;
                }
                if (mv.getQuantityChange() == null) {
                    continue;
                }

                Map<String, Object> mvMeta = mv.getMetadata();
                boolean isCounterReturn = mvMeta != null
                        && Boolean.TRUE.equals(mvMeta.get("kuji_counter_return"));

                if (isCounterReturn && mv.getQuantityChange() > 0
                        && mv.getFromLocationId() == null
                        && mv.getToLocationId() != null) {
                    // New-model close: reverse by decrementing destination and restoring counters.
                    int qty = mv.getQuantityChange();
                    Location destination = locationRepository.findById(mv.getToLocationId())
                            .orElseThrow(() -> new LocationNotFoundException(
                                    "Reopen destination location not found: " + mv.getToLocationId()));
                    LocationInventory inv = locationInventoryRepository
                            .findByLocation_IdAndProduct_Id(destination.getId(), mv.getItem().getId())
                            .orElseThrow(() -> new InventoryNotFoundException(
                                    "Cannot reopen: destination inventory missing for "
                                            + mv.getItem().getName()));
                    if (inv.getQuantity() < qty) {
                        throw new IllegalStateException(
                                "Cannot reopen: destination has " + inv.getQuantity()
                                        + " of " + mv.getItem().getName() + ", need " + qty);
                    }
                    int newQty = inv.getQuantity() - qty;
                    if (newQty == 0) {
                        locationInventoryRepository.delete(inv);
                    } else {
                        inv.setQuantity(newQty);
                        locationInventoryRepository.save(inv);
                    }

                    // Restore tier counters from metadata.
                    UUID tierId = extractTierIdFromMetadata(mvMeta);
                    if (tierId != null) {
                        KujiBoxTier tier = kujiBoxTierRepository.findById(tierId).orElse(null);
                        if (tier != null) {
                            int restoreActive = readQuantityFromMetadata(mvMeta, "activeCountAtClose", 0);
                            int restoreInactive = readQuantityFromMetadata(mvMeta, "inactiveCountAtClose", 0);
                            if (restoreActive == 0 && restoreInactive == 0) {
                                // Older counter-return without split — put everything in active.
                                restoreActive = qty;
                            }
                            tier.setActiveCount(
                                    (tier.getActiveCount() != null ? tier.getActiveCount() : 0) + restoreActive);
                            tier.setInactiveCount(
                                    (tier.getInactiveCount() != null ? tier.getInactiveCount() : 0) + restoreInactive);
                            kujiBoxTierRepository.save(tier);
                        }
                    }

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("kuji_box_id", boxId.toString());
                    metadata.put("action", "reopen_box_reverse_counter_return");
                    metadata.put("reverses_movement_id", String.valueOf(mv.getId()));
                    StockMovement reverse = StockMovement.builder()
                            .item(mv.getItem())
                            .locationType(mapLocationType(destination))
                            .fromLocationId(destination.getId())
                            .toLocationId(null)
                            .previousQuantity(inv.getQuantity() + qty)
                            .currentQuantity(newQty)
                            .quantityChange(-qty)
                            .reason(StockMovementReason.TRANSFER)
                            .actorId(actorId)
                            .at(OffsetDateTime.now())
                            .metadata(metadata)
                            .build();
                    StockMovement saved = stockMovementRepository.save(reverse);
                    eventOutboxService.createStockMovementEvent(saved);
                    affectedProductIds.add(mv.getItem().getId());
                    continue;
                }

                // Legacy close — only the withdrawal half (qty < 0, from=box.location) needs reversal.
                if (mv.getQuantityChange() >= 0
                        || mv.getFromLocationId() == null
                        || !mv.getFromLocationId().equals(box.getLocation().getId())
                        || mv.getToLocationId() == null) {
                    continue;
                }

                int qty = Math.abs(mv.getQuantityChange());
                Location source = locationRepository.findById(mv.getToLocationId())
                        .orElseThrow(() -> new LocationNotFoundException(
                                "Reopen source location not found: " + mv.getToLocationId()));

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("kuji_box_id", boxId.toString());
                metadata.put("action", "reopen_box_reverse_close");
                metadata.put("reverses_movement_id", String.valueOf(mv.getId()));

                executeKujiTransfer(
                        source.getId(),
                        box.getLocation(),
                        mv.getItem(),
                        qty,
                        actorId,
                        StockMovementReason.TRANSFER,
                        metadata,
                        box.getProduct().getId()
                );
                affectedProductIds.add(mv.getItem().getId());
            }
        }

        // Reactivate auto-created prize products that were soft-deleted at close.
        for (KujiBoxTier tier : box.getTiers()) {
            if (Boolean.TRUE.equals(tier.getAutoCreatedProduct())
                    && tier.getLinkedProduct() != null
                    && Boolean.FALSE.equals(tier.getLinkedProduct().getIsActive())) {
                Product child = tier.getLinkedProduct();
                child.setIsActive(true);
                productRepository.save(child);
                affectedProductIds.add(child.getId());
            }
        }

        box.setStatus(KujiBoxStatus.OPEN);
        box.setClosedAt(null);
        box.setClosedBy(null);
        box = kujiBoxRepository.save(box);

        if (!affectedProductIds.isEmpty()) {
            stockMovementService.syncProductTotals(new ArrayList<>(affectedProductIds));
            broadcastService.broadcastInventoryUpdated();
            broadcastService.broadcastAuditLogCreated();
        }

        entityManager.flush();
        return toResponseDTO(box);
    }

    // ===================== Draws =====================

    @Transactional
    public KujiBoxResponseDTO recordDraw(UUID boxId, RecordDrawRequestDTO request) {
        KujiBox box = kujiBoxRepository.findByIdWithTiers(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));
        if (box.getStatus() != KujiBoxStatus.OPEN) {
            throw new IllegalStateException("Box is not OPEN: " + boxId);
        }

        // Lock all draw tiers for update, in deterministic order
        List<UUID> tierIds = request.getDraws().stream()
                .map(RecordDrawRequestDTO.DrawLine::getTierId)
                .sorted(UUID::compareTo)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, KujiBoxTier> tiersById = new HashMap<>();
        for (UUID tid : tierIds) {
            KujiBoxTier locked = kujiBoxTierRepository.findByIdForUpdate(tid)
                    .orElseThrow(() -> new IllegalArgumentException("Tier not found: " + tid));
            if (locked.getBox() == null || !boxId.equals(locked.getBox().getId())) {
                throw new IllegalArgumentException(
                        "Tier " + tid + " does not belong to box " + boxId);
            }
            tiersById.put(tid, locked);
        }

        // Validate sufficient slip count and (for linked tiers) sufficient inventory at box location
        int totalQuantity = 0;
        for (RecordDrawRequestDTO.DrawLine draw : request.getDraws()) {
            KujiBoxTier tier = tiersById.get(draw.getTierId());
            if (tier.getActiveCount() == null || tier.getActiveCount() < draw.getQuantity()) {
                throw new IllegalArgumentException(
                        "Tier '" + tier.getLabel() + "' has only "
                                + (tier.getActiveCount() == null ? 0 : tier.getActiveCount())
                                + " slips remaining; cannot draw " + draw.getQuantity());
            }
            // Active count is the only check needed — kuji prize stock lives on the tier,
            // not in LocationInventory at the machine.
            totalQuantity += draw.getQuantity();
        }

        // Decrement counts (in-memory; saved when tier persists below)
        for (RecordDrawRequestDTO.DrawLine draw : request.getDraws()) {
            KujiBoxTier tier = tiersById.get(draw.getTierId());
            tier.setActiveCount(tier.getActiveCount() - draw.getQuantity());
            int currentDrawn = tier.getDrawnCount() != null ? tier.getDrawnCount() : 0;
            tier.setDrawnCount(currentDrawn + draw.getQuantity());
        }

        // Create one parent AuditLog of reason KUJI_PRIZE_WON. The summary lists the tiers
        // and prizes so the activity feed shows what was drawn, not just the box name.
        Product parentProduct = box.getProduct();
        String drawSummary = formatDrawSummary(request.getDraws(), tiersById);
        AuditLog parentLog = createAuditLog(
                request.getActorId(),
                StockMovementReason.KUJI_PRIZE_WON,
                box.getLocation().getId(),
                box.getLocation().getLocationCode(),
                null,
                null,
                request.getDraws().size(),
                totalQuantity,
                drawSummary,
                request.getNotes(),
                parentProduct.getId()
        );

        OffsetDateTime now = OffsetDateTime.now();
        List<Map<String, Object>> tierSummaries = new ArrayList<>();

        for (RecordDrawRequestDTO.DrawLine draw : request.getDraws()) {
            KujiBoxTier tier = tiersById.get(draw.getTierId());
            int qty = draw.getQuantity();

            Map<String, Object> metadata = baseDrawMetadata(box, tier, parentLog.getId());
            metadata.put("tier_label", tier.getLabel());
            if (tier.getLetter() != null) {
                metadata.put("tier_letter", tier.getLetter());
            }
            // Always store slip_quantity so undo can reliably restore tier counts.
            metadata.put("slip_quantity", qty);

            // Linked and unlinked draws emit the same structural movement: a zero-quantity
            // KUJI_PRIZE_WON audit row with prize details in metadata. Kuji prize stock
            // lives on the tier (activeCount), not in LocationInventory.
            Product linked = tier.getLinkedProduct();
            if (linked != null) {
                metadata.put("linked_product_id", linked.getId().toString());
                metadata.put("linked_product_name", linked.getName());
            }
            // Snapshot the per-slip price so the daily-payout chart and activity log
            // stay fixed if a tier's price or its linked product's msrp is edited later.
            metadata.put("unit_value", resolveUnitValue(tier, linked));
            StockMovement movement = StockMovement.builder()
                    .auditLog(parentLog)
                    .item(linked != null ? linked : parentProduct)
                    .locationType(LocationType.NOT_ASSIGNED)
                    .fromLocationId(null)
                    .toLocationId(null)
                    .previousQuantity(0)
                    .currentQuantity(0)
                    .quantityChange(0)
                    .reason(StockMovementReason.KUJI_PRIZE_WON)
                    .actorId(request.getActorId())
                    .at(now)
                    .metadata(metadata)
                    .build();

            StockMovement saved = stockMovementRepository.save(movement);
            eventOutboxService.createStockMovementEvent(saved);

            // Persist the decremented tier count
            kujiBoxTierRepository.save(tier);

            // Notification tier line
            Map<String, Object> line = new HashMap<>();
            line.put("tier_id", tier.getId().toString());
            line.put("label", tier.getLabel());
            line.put("letter", tier.getLetter());
            line.put("linked_product_name",
                    tier.getLinkedProduct() != null ? tier.getLinkedProduct().getName() : null);
            line.put("price", tier.getPrice());
            line.put("quantity", qty);
            line.put("count_after", tier.getActiveCount());
            tierSummaries.add(line);
        }

        // Notification — wrap in try/catch
        emitDrawNotification(
                NotificationType.KUJI_PRIZE_DRAWN,
                "Kuji prize drawn",
                box,
                request.getActorId(),
                request.getNotes(),
                tierSummaries,
                now
        );

        broadcastService.broadcastInventoryUpdated();
        broadcastService.broadcastAuditLogCreated(parentProduct.getId().toString());

        entityManager.flush();
        return toResponseDTO(box);
    }

    @Transactional
    public KujiBoxResponseDTO undoDraw(UUID boxId, UUID auditLogId, UUID actorId) {
        KujiBox box = kujiBoxRepository.findByIdWithTiers(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));

        AuditLog target = auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> new IllegalArgumentException("AuditLog not found: " + auditLogId));
        if (target.getReason() != StockMovementReason.KUJI_PRIZE_WON) {
            throw new IllegalArgumentException(
                    "AuditLog " + auditLogId + " is not a KUJI_PRIZE_WON event");
        }

        // Already undone?
        List<StockMovement> reversals = findKujiReversalsForAuditLog(auditLogId);
        if (reversals != null && !reversals.isEmpty()) {
            throw new IllegalStateException("Draw already undone");
        }

        List<StockMovement> originals = stockMovementRepository.findByAuditLogIdWithItem(auditLogId);
        if (originals == null || originals.isEmpty()) {
            throw new IllegalArgumentException(
                    "No stock movements found for audit log: " + auditLogId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        int totalRestored = 0;
        Set<UUID> affectedProductIds = new HashSet<>();

        // We sum first to know totalQuantityMoved before creating the parent log.
        // Prefer the canonical slip_quantity from metadata so free-text tiers (quantityChange=0)
        // contribute their actual draw count too.
        for (StockMovement mv : originals) {
            int slips = readQuantityFromMetadata(mv.getMetadata(), 0);
            if (slips == 0 && mv.getQuantityChange() != null) {
                slips = Math.abs(mv.getQuantityChange());
            }
            totalRestored += slips;
        }

        Product parentProduct = box.getProduct();
        String undoSummary = formatUndoSummary(originals);
        AuditLog reverseLog = createAuditLog(
                actorId,
                StockMovementReason.KUJI_DRAW_REVERSED,
                null,
                null,
                box.getLocation().getId(),
                box.getLocation().getLocationCode(),
                originals.size(),
                totalRestored,
                undoSummary,
                "Undid kuji draw",
                parentProduct.getId()
        );

        // Mark the original draw as reversed so the UI can hide it from the undo-draw picker.
        target.setReversedAt(now);
        target.setReversedByLogId(reverseLog.getId());
        auditLogRepository.save(target);

        // Tier id → KujiBoxTier (locked)
        Map<UUID, KujiBoxTier> lockedTiers = new HashMap<>();

        List<Map<String, Object>> tierSummaries = new ArrayList<>();

        for (StockMovement mv : originals) {
            UUID tierId = extractTierIdFromMetadata(mv.getMetadata());
            KujiBoxTier tier = null;
            if (tierId != null) {
                tier = lockedTiers.get(tierId);
                if (tier == null) {
                    tier = kujiBoxTierRepository.findByIdForUpdate(tierId).orElse(null);
                    if (tier != null) {
                        lockedTiers.put(tierId, tier);
                    }
                }
            }

            // Slip count to restore. Linked tiers store it in quantityChange (and metadata);
            // free-text tiers only have it in metadata.slip_quantity. We prefer metadata and
            // fall back to abs(quantityChange).
            int slipCount = readQuantityFromMetadata(mv.getMetadata(), 0);
            if (slipCount == 0 && mv.getQuantityChange() != null) {
                slipCount = Math.abs(mv.getQuantityChange());
            }
            int qty = slipCount;

            // Legacy draws (pre-decoupling) may have decremented LocationInventory at the
            // machine. Restore that side too so undo is correct for historical data. New
            // draws emit quantityChange=0 and skip this path.
            if (mv.getQuantityChange() != null && mv.getQuantityChange() < 0
                    && mv.getItem() != null
                    && mv.getFromLocationId() != null
                    && mv.getFromLocationId().equals(box.getLocation().getId())) {
                Product linked = mv.getItem();
                LocationInventory inv = locationInventoryRepository
                        .findByLocation_IdAndProduct_Id(box.getLocation().getId(), linked.getId())
                        .orElse(null);

                int prev = inv != null ? inv.getQuantity() : 0;
                int next = prev + qty;
                if (inv == null) {
                    inv = LocationInventory.builder()
                            .location(box.getLocation())
                            .site(box.getLocation().getStorageLocation().getSite())
                            .product(linked)
                            .quantity(next)
                            .build();
                } else {
                    inv.setQuantity(next);
                }
                locationInventoryRepository.save(inv);
                affectedProductIds.add(linked.getId());
            }

            // Always write a counter-only reversal audit row. Inventory restore above
            // only fires for legacy data.
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("kuji_box_id", boxId.toString());
            metadata.put("reverses_audit_log_id", auditLogId.toString());
            metadata.put("reverses_movement_id", String.valueOf(mv.getId()));
            if (tier != null) {
                metadata.put("kuji_box_tier_id", tier.getId().toString());
            }
            // Mirror the original draw's snapshotted unit value so the daily-payout
            // aggregation subtracts the same amount it originally added — even if the
            // tier's live price was edited between draw and undo.
            BigDecimal snappedUnitValue = readUnitValueFromMetadata(mv.getMetadata());
            if (snappedUnitValue != null) {
                metadata.put("unit_value", snappedUnitValue);
            }
            StockMovement reverse = StockMovement.builder()
                    .auditLog(reverseLog)
                    .item(mv.getItem() != null ? mv.getItem() : parentProduct)
                    .locationType(LocationType.NOT_ASSIGNED)
                    .fromLocationId(null)
                    .toLocationId(null)
                    .previousQuantity(0)
                    .currentQuantity(0)
                    .quantityChange(0)
                    .reason(StockMovementReason.KUJI_DRAW_REVERSED)
                    .actorId(actorId)
                    .at(now)
                    .metadata(metadata)
                    .build();
            StockMovement saved = stockMovementRepository.save(reverse);
            eventOutboxService.createStockMovementEvent(saved);

            // Restore tier slip count
            if (tier != null) {
                Integer current = tier.getActiveCount() != null ? tier.getActiveCount() : 0;
                tier.setActiveCount(current + qty);
                int currentDrawn = tier.getDrawnCount() != null ? tier.getDrawnCount() : 0;
                tier.setDrawnCount(Math.max(0, currentDrawn - qty));
                kujiBoxTierRepository.save(tier);

                Map<String, Object> line = new HashMap<>();
                line.put("tier_id", tier.getId().toString());
                line.put("label", tier.getLabel());
                line.put("letter", tier.getLetter());
                line.put("linked_product_name",
                        tier.getLinkedProduct() != null ? tier.getLinkedProduct().getName() : null);
                line.put("price", tier.getPrice());
                line.put("quantity", qty);
                line.put("count_after", tier.getActiveCount());
                tierSummaries.add(line);
            }
        }

        if (!affectedProductIds.isEmpty()) {
            stockMovementService.syncProductTotals(new ArrayList<>(affectedProductIds));
        }

        emitDrawNotification(
                NotificationType.KUJI_PRIZE_DRAW_UNDONE,
                "Kuji prize draw undone",
                box,
                actorId,
                "Undid kuji draw",
                tierSummaries,
                now
        );

        broadcastService.broadcastInventoryUpdated();
        broadcastService.broadcastAuditLogCreated(parentProduct.getId().toString());

        entityManager.flush();
        return toResponseDTO(box);
    }

    // ===================== Slip / Transfer-In More / Patch =====================

    @Transactional
    public KujiBoxResponseDTO addSlip(UUID boxId, UUID tierId, AddSlipRequestDTO request) {
        KujiBox box = kujiBoxRepository.findByIdWithTiers(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));
        if (box.getStatus() != KujiBoxStatus.OPEN) {
            throw new IllegalStateException("Box is not OPEN: " + boxId);
        }

        KujiBoxTier tier = kujiBoxTierRepository.findByIdForUpdate(tierId)
                .orElseThrow(() -> new IllegalArgumentException("Tier not found: " + tierId));
        if (tier.getBox() == null || !boxId.equals(tier.getBox().getId())) {
            throw new IllegalArgumentException("Tier does not belong to box");
        }

        int quantity = request.getQuantity();
        boolean inactive = Boolean.TRUE.equals(request.getInactive());
        if (inactive) {
            tier.setInactiveCount((tier.getInactiveCount() == null ? 0 : tier.getInactiveCount()) + quantity);
        } else {
            tier.setActiveCount((tier.getActiveCount() == null ? 0 : tier.getActiveCount()) + quantity);
        }
        kujiBoxTierRepository.save(tier);

        Product parentProduct = box.getProduct();
        // Slip adjustments don't change inventory — only the tier's slip count. The
        // AuditLog drives the kuji session activity log; no StockMovement or outbox
        // event is needed (and none is consumed downstream for KUJI_SLIP_ADJUSTMENT).
        String bucketLabel = inactive ? " (inactive)" : "";
        createAuditLog(
                request.getActorId(),
                StockMovementReason.KUJI_SLIP_ADJUSTMENT,
                box.getLocation().getId(),
                box.getLocation().getLocationCode(),
                null,
                null,
                1,
                quantity,
                parentProduct.getName(),
                "Kuji slip added" + bucketLabel + ": " + tierAuditHeader(tier),
                parentProduct.getId()
        );

        broadcastService.broadcastAuditLogCreated(parentProduct.getId().toString());

        entityManager.flush();
        return toResponseDTO(box);
    }

    /**
     * Move slips between the active and inactive buckets within a single tier.
     * Pure counter operation — no StockMovement, no LocationInventory side-effect.
     */
    @Transactional
    public KujiBoxResponseDTO moveSlips(UUID boxId, UUID tierId, MoveSlipsRequestDTO request) {
        KujiBox box = kujiBoxRepository.findByIdWithTiers(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));
        if (box.getStatus() != KujiBoxStatus.OPEN) {
            throw new IllegalStateException("Box is not OPEN: " + boxId);
        }

        KujiBoxTier tier = kujiBoxTierRepository.findByIdForUpdate(tierId)
                .orElseThrow(() -> new IllegalArgumentException("Tier not found: " + tierId));
        if (tier.getBox() == null || !boxId.equals(tier.getBox().getId())) {
            throw new IllegalArgumentException("Tier does not belong to box");
        }

        int quantity = request.getQuantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }

        int active = tier.getActiveCount() != null ? tier.getActiveCount() : 0;
        int inactive = tier.getInactiveCount() != null ? tier.getInactiveCount() : 0;
        MoveSlipsRequestDTO.Direction direction = request.getDirection();

        if (direction == MoveSlipsRequestDTO.Direction.DEACTIVATE) {
            if (active < quantity) {
                throw new IllegalArgumentException(
                        "Tier '" + tier.getLabel() + "' has only " + active
                                + " active slip(s); cannot deactivate " + quantity);
            }
            tier.setActiveCount(active - quantity);
            tier.setInactiveCount(inactive + quantity);
        } else { // ACTIVATE
            if (inactive < quantity) {
                throw new IllegalArgumentException(
                        "Tier '" + tier.getLabel() + "' has only " + inactive
                                + " inactive slip(s); cannot activate " + quantity);
            }
            tier.setActiveCount(active + quantity);
            tier.setInactiveCount(inactive - quantity);
        }
        kujiBoxTierRepository.save(tier);

        Product parentProduct = box.getProduct();
        String action = direction == MoveSlipsRequestDTO.Direction.ACTIVATE ? "activate" : "deactivate";
        createAuditLog(
                request.getActorId(),
                StockMovementReason.KUJI_SLIP_ADJUSTMENT,
                box.getLocation().getId(),
                box.getLocation().getLocationCode(),
                null,
                null,
                1,
                quantity,
                parentProduct.getName(),
                "Kuji slips " + action + "d: " + tierAuditHeader(tier) + " (" + quantity + ")",
                parentProduct.getId()
        );

        broadcastService.broadcastAuditLogCreated(parentProduct.getId().toString());

        entityManager.flush();
        return toResponseDTO(box);
    }

    /**
     * Delete a tier entirely — zeros both active and inactive counters and removes
     * the tier row from the box. Pure counter operation: no StockMovement, no
     * LocationInventory side-effect. If the tier had remaining slips of a linked
     * product that should be returned to regular inventory, do that via Edit Tier
     * (clear/switch linked product) before calling this.
     */
    @Transactional
    public KujiBoxResponseDTO deletePrize(UUID boxId, UUID tierId, DeletePrizeRequestDTO request) {
        KujiBox box = kujiBoxRepository.findByIdWithTiers(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));
        if (box.getStatus() != KujiBoxStatus.OPEN) {
            throw new IllegalStateException("Box is not OPEN: " + boxId);
        }

        KujiBoxTier tier = kujiBoxTierRepository.findByIdForUpdate(tierId)
                .orElseThrow(() -> new IllegalArgumentException("Tier not found: " + tierId));
        if (tier.getBox() == null || !boxId.equals(tier.getBox().getId())) {
            throw new IllegalArgumentException("Tier does not belong to box");
        }

        int active = tier.getActiveCount() != null ? tier.getActiveCount() : 0;
        int inactive = tier.getInactiveCount() != null ? tier.getInactiveCount() : 0;
        int total = active + inactive;
        String tierHeader = tierAuditHeader(tier);

        box.getTiers().remove(tier);
        kujiBoxTierRepository.delete(tier);

        Product parentProduct = box.getProduct();
        String summary = "Kuji prize tier deleted: " + tierHeader
                + " (active " + active + ", inactive " + inactive + ")";
        createAuditLog(
                request.getActorId(),
                StockMovementReason.KUJI_SLIP_ADJUSTMENT,
                box.getLocation().getId(),
                box.getLocation().getLocationCode(),
                null,
                null,
                1,
                total,
                parentProduct.getName(),
                summary,
                parentProduct.getId()
        );

        broadcastService.broadcastAuditLogCreated(parentProduct.getId().toString());

        entityManager.flush();
        return toResponseDTO(box);
    }

    @Transactional
    public KujiBoxResponseDTO transferInMore(UUID boxId, UUID tierId, TransferInMoreRequestDTO request) {
        KujiBox box = kujiBoxRepository.findByIdWithTiers(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));
        if (box.getStatus() != KujiBoxStatus.OPEN) {
            throw new IllegalStateException("Box is not OPEN: " + boxId);
        }

        KujiBoxTier tier = kujiBoxTierRepository.findByIdForUpdate(tierId)
                .orElseThrow(() -> new IllegalArgumentException("Tier not found: " + tierId));
        if (tier.getBox() == null || !boxId.equals(tier.getBox().getId())) {
            throw new IllegalArgumentException("Tier does not belong to box");
        }
        if (tier.getLinkedProduct() == null) {
            throw new IllegalArgumentException(
                    "Tier '" + tier.getLabel() + "' has no linked product; cannot transfer-in");
        }

        boolean autoCreateMint = request.getSourceLocationId() == null;
        if (!autoCreateMint) {
            // Pull from real inventory at the source — no deposit at the machine.
            Map<String, Object> metadata = baseDrawMetadata(box, tier, null);
            metadata.put("action", "transfer_in_more");
            applyIntakeMetadata(metadata, request.getIntakeUnit(), request.getIntakeQty());
            executeKujiSourceRemoval(
                    request.getSourceLocationId(),
                    tier.getLinkedProduct(),
                    request.getQuantity(),
                    request.getActorId(),
                    metadata,
                    box.getProduct().getId()
            );
        }
        // Either way, the tier's active counter grows.
        tier.setActiveCount((tier.getActiveCount() == null ? 0 : tier.getActiveCount()) + request.getQuantity());
        kujiBoxTierRepository.save(tier);

        if (!autoCreateMint) {
            stockMovementService.syncProductTotals(List.of(tier.getLinkedProduct().getId()));
        }
        broadcastService.broadcastInventoryUpdated();
        broadcastService.broadcastAuditLogCreated();

        entityManager.flush();
        return toResponseDTO(box);
    }

    @Transactional
    public KujiBoxResponseDTO transferInInventoryOnly(UUID boxId, UUID tierId, TransferInMoreRequestDTO request) {
        KujiBox box = kujiBoxRepository.findByIdWithTiers(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));
        if (box.getStatus() != KujiBoxStatus.OPEN) {
            throw new IllegalStateException("Box is not OPEN: " + boxId);
        }

        KujiBoxTier tier = kujiBoxTierRepository.findByIdForUpdate(tierId)
                .orElseThrow(() -> new IllegalArgumentException("Tier not found: " + tierId));
        if (tier.getBox() == null || !boxId.equals(tier.getBox().getId())) {
            throw new IllegalArgumentException("Tier does not belong to box");
        }
        if (tier.getLinkedProduct() == null) {
            throw new IllegalArgumentException(
                    "Tier '" + tier.getLabel() + "' has no linked product; cannot transfer-in");
        }

        boolean autoCreateMint = request.getSourceLocationId() == null;
        if (!autoCreateMint) {
            Map<String, Object> metadata = baseDrawMetadata(box, tier, null);
            metadata.put("action", "transfer_in_inventory_only");
            applyIntakeMetadata(metadata, request.getIntakeUnit(), request.getIntakeQty());
            executeKujiSourceRemoval(
                    request.getSourceLocationId(),
                    tier.getLinkedProduct(),
                    request.getQuantity(),
                    request.getActorId(),
                    metadata,
                    box.getProduct().getId()
            );
        }
        // Inventory-only transfer-in lands in the inactive bucket (held back, not on a slip).
        tier.setInactiveCount((tier.getInactiveCount() == null ? 0 : tier.getInactiveCount()) + request.getQuantity());
        kujiBoxTierRepository.save(tier);

        if (!autoCreateMint) {
            stockMovementService.syncProductTotals(List.of(tier.getLinkedProduct().getId()));
        }
        broadcastService.broadcastInventoryUpdated();
        broadcastService.broadcastAuditLogCreated();

        entityManager.flush();
        return toResponseDTO(box);
    }

    /**
     * Mint additional inventory of an auto-created kuji prize at the box location.
     * No source — these products only ever exist at the box. Creates a single
     * AuditLog + StockMovement(ADJUSTMENT) and updates the LocationInventory row.
     */
    private void mintAutoCreatedAtBox(
            KujiBox box,
            KujiBoxTier tier,
            int quantity,
            UUID actorId,
            String action
    ) {
        if (!Boolean.TRUE.equals(tier.getAutoCreatedProduct())) {
            throw new IllegalArgumentException(
                    "Tier '" + tier.getLabel()
                            + "' is linked to an existing product; sourceLocationId is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        Product child = tier.getLinkedProduct();
        Location boxLocation = box.getLocation();

        LocationInventory inv = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(boxLocation.getId(), child.getId())
                .orElseGet(() -> LocationInventory.builder()
                        .location(boxLocation)
                        .site(boxLocation.getStorageLocation().getSite())
                        .product(child)
                        .quantity(0)
                        .build());

        int prev = inv.getQuantity();
        inv.setQuantity(prev + quantity);
        locationInventoryRepository.save(inv);

        // Auto-create children only ever live at this one location, so the denormalized
        // Product.quantity equals LocationInventory.quantity. Update directly to avoid
        // a syncProductTotals SUM query downstream.
        child.setQuantity(prev + quantity);
        if (!Boolean.TRUE.equals(child.getIsActive())) {
            child.setIsActive(true);
        }
        productRepository.save(child);

        AuditLog auditLog = createAuditLog(
                actorId,
                StockMovementReason.ADJUSTMENT,
                null,
                null,
                boxLocation.getId(),
                boxLocation.getLocationCode(),
                1,
                quantity,
                child.getName(),
                null,
                box.getProduct().getId()
        );

        Map<String, Object> metadata = buildTierMetadata(box, tier, action);
        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .item(child)
                .locationType(mapLocationType(boxLocation))
                .fromLocationId(null)
                .toLocationId(boxLocation.getId())
                .previousQuantity(prev)
                .currentQuantity(prev + quantity)
                .quantityChange(quantity)
                .reason(StockMovementReason.ADJUSTMENT)
                .actorId(actorId)
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();
        StockMovement saved = stockMovementRepository.save(movement);
        eventOutboxService.createStockMovementEvent(saved);
    }

    @Transactional
    public KujiBoxResponseDTO addTier(UUID boxId, AddKujiTierRequestDTO request) {
        KujiBox box = kujiBoxRepository.findByIdWithTiers(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));
        if (box.getStatus() != KujiBoxStatus.OPEN) {
            throw new IllegalStateException("Box is not OPEN: " + boxId);
        }

        boolean autoCreate = Boolean.TRUE.equals(request.getAutoCreate());
        if (autoCreate && request.getLinkedProductId() != null) {
            throw new IllegalArgumentException(
                    "Tier '" + request.getLabel() + "': cannot set both linkedProductId and autoCreate=true.");
        }
        if (autoCreate && (request.getProductName() == null || request.getProductName().isBlank())) {
            throw new IllegalArgumentException(
                    "Tier '" + request.getLabel() + "': productName is required when autoCreate=true.");
        }

        Product linkedProduct = null;
        if (request.getLinkedProductId() != null) {
            linkedProduct = productRepository.findById(request.getLinkedProductId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "Linked product not found: " + request.getLinkedProductId()));
        } else if (autoCreate) {
            // Auto-created prize: product entity only. Quantity lives in tier counters.
            linkedProduct = productService.createProduct(
                    null,                                       // sku
                    null,                                       // categoryId — inherits from parent
                    box.getProduct().getId(),                   // parentId
                    request.getLetter(),                        // letter
                    null,                                       // templateQuantity
                    request.getProductName().trim(),            // name
                    null,                                       // description
                    0,                                          // reorderPoint
                    0,                                          // targetStockLevel
                    14,                                         // leadTimeDays
                    null,                                       // unitCost
                    request.getProductMsrp(),                   // msrp
                    request.getProductImageUrl(),               // imageUrl
                    null,                                       // notes
                    null,                                       // initialStock — kuji counters own this
                    null,                                       // kujiType
                    null,                                       // kujiSlackWebhookUrl
                    null                                        // packsPerBox
            );
            linkedProduct.setIsActive(true);
            linkedProduct = productRepository.save(linkedProduct);
        }

        KujiBoxTier tier = KujiBoxTier.builder()
                .box(box)
                .label(request.getLabel())
                .letter(request.getLetter())
                .linkedProduct(linkedProduct)
                .activeCount(request.getActiveCount() != null ? request.getActiveCount() : 0)
                .inactiveCount(request.getInactiveCount() != null ? request.getInactiveCount() : 0)
                .price(request.getPrice())
                .autoCreatedProduct(autoCreate)
                .build();
        tier = kujiBoxTierRepository.save(tier);
        if (box.getTiers() != null) {
            box.getTiers().add(tier);
        }
        entityManager.flush();

        // Materialize inventory: birth at box for auto-create, transfer-in otherwise.
        int slipCount = tier.getActiveCount() == null ? 0 : tier.getActiveCount();
        int heldBack = request.getInactiveCount() == null ? 0 : request.getInactiveCount();
        int totalQuantity = slipCount + heldBack;

        Set<UUID> affectedProductIds = new HashSet<>();
        if (linkedProduct != null && totalQuantity > 0) {
            if (autoCreate) {
                Map<String, Object> metadata = buildTierMetadata(box, tier, "add_tier_auto_create");
                metadata.put("activeCount", slipCount);
                metadata.put("inactiveCount", heldBack);
                StockMovement birth = StockMovement.builder()
                        .item(linkedProduct)
                        .locationType(LocationType.NOT_ASSIGNED)
                        .fromLocationId(null)
                        .toLocationId(null)
                        .previousQuantity(0)
                        .currentQuantity(0)
                        .quantityChange(0)
                        .reason(StockMovementReason.INITIAL_STOCK)
                        .actorId(request.getActorId())
                        .at(OffsetDateTime.now())
                        .metadata(metadata)
                        .build();
                stockMovementRepository.save(birth);
            } else {
                if (request.getSourceLocationId() == null) {
                    throw new IllegalArgumentException(
                            "Tier '" + tier.getLabel() + "' has a linked product but no sourceLocationId");
                }
                Map<String, Object> metadata = buildTierMetadata(box, tier, "add_tier_source_removal");
                metadata.put("activeCount", slipCount);
                metadata.put("inactiveCount", heldBack);
                executeKujiSourceRemoval(
                        request.getSourceLocationId(),
                        linkedProduct,
                        totalQuantity,
                        request.getActorId(),
                        metadata,
                        box.getProduct().getId()
                );
                affectedProductIds.add(linkedProduct.getId());
            }
        }

        if (!affectedProductIds.isEmpty()) {
            stockMovementService.syncProductTotals(new ArrayList<>(affectedProductIds));
            broadcastService.broadcastInventoryUpdated();
            broadcastService.broadcastAuditLogCreated();
        }

        Product parentProduct = box.getProduct();
        String summary = "Kuji prize tier added: " + tierAuditHeader(tier)
                + " (active " + slipCount + ", inactive " + heldBack + ")";
        createAuditLog(
                request.getActorId(),
                StockMovementReason.KUJI_SLIP_ADJUSTMENT,
                box.getLocation().getId(),
                box.getLocation().getLocationCode(),
                null,
                null,
                1,
                totalQuantity,
                parentProduct.getName(),
                summary,
                parentProduct.getId()
        );
        broadcastService.broadcastAuditLogCreated(parentProduct.getId().toString());

        entityManager.flush();
        return toResponseDTO(box);
    }

    @Transactional
    public KujiBoxResponseDTO patchTier(UUID boxId, UUID tierId, PatchKujiTierRequestDTO request) {
        KujiBox box = kujiBoxRepository.findByIdWithTiers(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));
        if (box.getStatus() != KujiBoxStatus.OPEN) {
            throw new IllegalStateException("Box is not OPEN: " + boxId);
        }

        KujiBoxTier tier = kujiBoxTierRepository.findByIdForUpdate(tierId)
                .orElseThrow(() -> new IllegalArgumentException("Tier not found: " + tierId));
        if (tier.getBox() == null || !boxId.equals(tier.getBox().getId())) {
            throw new IllegalArgumentException("Tier does not belong to box");
        }

        List<String> changes = new ArrayList<>();
        Set<UUID> affectedProductIds = new HashSet<>();

        // Linked product change handling
        boolean clearLinked = Boolean.TRUE.equals(request.getClearLinkedProduct());
        boolean changeLinked = clearLinked
                || (request.getLinkedProductId() != null
                && !Objects.equals(
                        request.getLinkedProductId(),
                        tier.getLinkedProduct() != null ? tier.getLinkedProduct().getId() : null));

        if (changeLinked) {
            Product oldLinked = tier.getLinkedProduct();
            int leftoverQty = (tier.getActiveCount() != null ? tier.getActiveCount() : 0)
                    + (tier.getInactiveCount() != null ? tier.getInactiveCount() : 0);
            if (oldLinked != null && leftoverQty > 0) {
                if (request.getLinkedProductDestinationLocationId() == null) {
                    throw new IllegalArgumentException(
                            "Tier " + tier.getLabel() + ": provide linkedProductDestinationLocationId for "
                                    + leftoverQty + " units of " + oldLinked.getName());
                }
                Location destination = locationRepository
                        .findById(request.getLinkedProductDestinationLocationId())
                        .orElseThrow(() -> new LocationNotFoundException(
                                "Destination location not found: "
                                        + request.getLinkedProductDestinationLocationId()));

                // Counter-return: kuji's active+inactive slips become regular inventory.
                Map<String, Object> metadata = baseDrawMetadata(box, tier, null);
                metadata.put("action", "patch_tier_old_product_out");
                executeKujiCounterReturn(
                        destination,
                        oldLinked,
                        leftoverQty,
                        request.getActorId(),
                        metadata,
                        box.getProduct().getId()
                );
                tier.setActiveCount(0);
                tier.setInactiveCount(0);
                affectedProductIds.add(oldLinked.getId());
            }

            if (clearLinked) {
                tier.setLinkedProduct(null);
                changes.add("cleared linked product");
            } else {
                Product newLinked = productRepository.findById(request.getLinkedProductId())
                        .orElseThrow(() -> new ProductNotFoundException(
                                "Linked product not found: " + request.getLinkedProductId()));
                tier.setLinkedProduct(newLinked);
                changes.add("changed linked product to " + newLinked.getName());

                // Optional: bring in initial counts of the new product in the same operation.
                int newActiveIn = request.getNewProductActiveCount() != null
                        ? request.getNewProductActiveCount() : 0;
                int newInactiveIn = request.getNewProductInactiveCount() != null
                        ? request.getNewProductInactiveCount() : 0;
                int newTotalIn = newActiveIn + newInactiveIn;
                if (newTotalIn > 0) {
                    if (request.getNewProductSourceLocationId() == null) {
                        throw new IllegalArgumentException(
                                "Tier " + tier.getLabel()
                                        + ": newProductSourceLocationId is required when bringing in "
                                        + newTotalIn + " units of " + newLinked.getName());
                    }
                    Map<String, Object> metadata = baseDrawMetadata(box, tier, null);
                    metadata.put("action", "patch_tier_new_product_in");
                    metadata.put("activeCount", newActiveIn);
                    metadata.put("inactiveCount", newInactiveIn);
                    executeKujiSourceRemoval(
                            request.getNewProductSourceLocationId(),
                            newLinked,
                            newTotalIn,
                            request.getActorId(),
                            metadata,
                            box.getProduct().getId()
                    );
                    tier.setActiveCount(
                            (tier.getActiveCount() != null ? tier.getActiveCount() : 0) + newActiveIn);
                    tier.setInactiveCount(
                            (tier.getInactiveCount() != null ? tier.getInactiveCount() : 0) + newInactiveIn);
                    affectedProductIds.add(newLinked.getId());
                    if (newActiveIn > 0 && newInactiveIn > 0) {
                        changes.add("brought in " + newActiveIn + " active and "
                                + newInactiveIn + " inactive");
                    } else if (newActiveIn > 0) {
                        changes.add("brought in " + newActiveIn + " active");
                    } else {
                        changes.add("brought in " + newInactiveIn + " inactive");
                    }
                }
            }
        }

        // Other patches
        if (request.getLabel() != null && !request.getLabel().equals(tier.getLabel())) {
            String prev = tier.getLabel();
            tier.setLabel(request.getLabel());
            changes.add("label: \"" + (prev != null ? prev : "") + "\" → \"" + request.getLabel() + "\"");
        }
        // Letter is not user-editable in any kuji flow today, so changes here
        // are not surfaced in the activity log. The assignment is kept so the
        // API contract stays intact for future callers.
        if (Boolean.TRUE.equals(request.getClearLetter())) {
            tier.setLetter(null);
        } else if (request.getLetter() != null && !request.getLetter().equals(tier.getLetter())) {
            tier.setLetter(request.getLetter());
        }
        if (request.getActiveCount() != null && !request.getActiveCount().equals(tier.getActiveCount())) {
            int prev = tier.getActiveCount() != null ? tier.getActiveCount() : 0;
            tier.setActiveCount(request.getActiveCount());
            changes.add("active count (manual adjustment): " + prev + " → " + request.getActiveCount());
        }
        if (request.getInactiveCount() != null
                && !request.getInactiveCount().equals(tier.getInactiveCount())) {
            int prev = tier.getInactiveCount() != null ? tier.getInactiveCount() : 0;
            tier.setInactiveCount(request.getInactiveCount());
            changes.add("inactive count (manual adjustment): " + prev + " → " + request.getInactiveCount());
        }
        if (Boolean.TRUE.equals(request.getClearPrice())) {
            BigDecimal prev = tier.getPrice();
            tier.setPrice(null);
            changes.add(prev != null ? "cleared price (was " + prev.toPlainString() + ")" : "cleared price");
        } else if (request.getPrice() != null && !request.getPrice().equals(tier.getPrice())) {
            BigDecimal prev = tier.getPrice();
            tier.setPrice(request.getPrice());
            changes.add("price: " + (prev != null ? prev.toPlainString() : "—")
                    + " → " + request.getPrice().toPlainString());
        }

        kujiBoxTierRepository.save(tier);

        Product parentProduct = box.getProduct();
        if (!changes.isEmpty()) {
            // Note prefix "Kuji tier edited" lets the kuji activity log classify this
            // entry and split the change-list into the expandable detail body.
            String summary = "Kuji tier edited: " + tierAuditHeader(tier) + " — " + String.join(", ", changes);
            AuditLog auditLog = createAuditLog(
                    request.getActorId(),
                    StockMovementReason.KUJI_SLIP_ADJUSTMENT,
                    box.getLocation().getId(),
                    box.getLocation().getLocationCode(),
                    null,
                    null,
                    1,
                    0,
                    parentProduct.getName(),
                    summary,
                    parentProduct.getId()
            );

            Map<String, Object> metadata = baseDrawMetadata(box, tier, auditLog.getId());
            metadata.put("action", "patch_tier");
            metadata.put("changes", changes);

            StockMovement movement = StockMovement.builder()
                    .auditLog(auditLog)
                    .item(parentProduct)
                    .locationType(LocationType.NOT_ASSIGNED)
                    .fromLocationId(null)
                    .toLocationId(null)
                    .previousQuantity(0)
                    .currentQuantity(0)
                    .quantityChange(0)
                    .reason(StockMovementReason.KUJI_SLIP_ADJUSTMENT)
                    .actorId(request.getActorId())
                    .at(OffsetDateTime.now())
                    .metadata(metadata)
                    .build();
            StockMovement saved = stockMovementRepository.save(movement);
            eventOutboxService.createStockMovementEvent(saved);
        }

        if (!affectedProductIds.isEmpty()) {
            stockMovementService.syncProductTotals(new ArrayList<>(affectedProductIds));
            broadcastService.broadcastInventoryUpdated();
        }
        broadcastService.broadcastAuditLogCreated();

        entityManager.flush();
        return toResponseDTO(box);
    }

    // ===================== Read APIs =====================

    @Transactional(readOnly = true)
    public KujiBoxResponseDTO getActiveBoxByProduct(UUID productId) {
        return kujiBoxRepository
                .findByProductIdAndStatusWithTiers(productId, KujiBoxStatus.OPEN)
                .map(this::toResponseDTO)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public KujiBoxResponseDTO getBox(UUID boxId) {
        KujiBox box = kujiBoxRepository.findByIdWithTiers(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));
        return toResponseDTO(box);
    }

    /**
     * Per-day net payout rollup for a box. Slip counts and value totals are netted
     * across KUJI_PRIZE_WON and KUJI_DRAW_REVERSED movements bucketed by calendar day
     * in the requested timezone. The returned series is always dense over [from, to].
     */
    @Transactional(readOnly = true)
    public KujiDailyPayoutsResponseDTO getDailyPayouts(
            UUID boxId,
            java.time.LocalDate from,
            java.time.LocalDate to,
            String tz
    ) {
        java.time.ZoneId zone;
        try {
            zone = java.time.ZoneId.of(tz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timezone: " + tz);
        }

        KujiBox box = kujiBoxRepository.findById(boxId)
                .orElseThrow(() -> new IllegalArgumentException("Box not found: " + boxId));

        java.time.LocalDate resolvedFrom = from != null
                ? from
                : box.getOpenedAt().atZoneSameInstant(zone).toLocalDate();
        java.time.LocalDate resolvedTo = to != null
                ? to
                : java.time.LocalDate.now(zone);

        if (resolvedTo.isBefore(resolvedFrom)) {
            throw new IllegalArgumentException(
                    "to (" + resolvedTo + ") must be on or after from (" + resolvedFrom + ")");
        }

        List<Object[]> rows = stockMovementRepository.aggregateKujiDailyPayouts(
                boxId, resolvedFrom, resolvedTo, zone.getId());

        Map<java.time.LocalDate, KujiDailyPayoutsResponseDTO.DailyPoint> byDate = new HashMap<>();
        for (Object[] row : rows) {
            java.time.LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            int slips = row[1] == null ? 0 : ((Number) row[1]).intValue();
            BigDecimal value = row[2] == null
                    ? BigDecimal.ZERO
                    : ((BigDecimal) row[2]).setScale(2, java.math.RoundingMode.HALF_UP);
            byDate.put(date, new KujiDailyPayoutsResponseDTO.DailyPoint(date, value, slips));
        }

        List<KujiDailyPayoutsResponseDTO.DailyPoint> series = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        int totalSlips = 0;
        for (java.time.LocalDate d = resolvedFrom; !d.isAfter(resolvedTo); d = d.plusDays(1)) {
            KujiDailyPayoutsResponseDTO.DailyPoint p = byDate.get(d);
            if (p == null) {
                p = new KujiDailyPayoutsResponseDTO.DailyPoint(
                        d, BigDecimal.ZERO.setScale(2), 0);
            }
            series.add(p);
            totalValue = totalValue.add(p.valueWon());
            totalSlips += p.slipCount();
        }

        return new KujiDailyPayoutsResponseDTO(
                boxId,
                resolvedFrom,
                resolvedTo,
                zone.getId(),
                series,
                new KujiDailyPayoutsResponseDTO.Totals(totalValue, totalSlips));
    }

    @Transactional(readOnly = true)
    public List<KujiBoxResponseDTO> getBoxHistory(UUID productId) {
        List<KujiBox> boxes = kujiBoxRepository.findByProductIdOrderByOpenedAtDesc(productId);
        // Re-fetch each with tiers eagerly
        return boxes.stream()
                .map(b -> kujiBoxRepository.findByIdWithTiers(b.getId()).orElse(b))
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<KujiBoxTierResponseDTO> cloneTiersFromLastClosedBox(UUID productId) {
        List<KujiBox> boxes = kujiBoxRepository.findByProductIdOrderByOpenedAtDesc(productId);
        Optional<KujiBox> lastClosed = boxes.stream()
                .filter(b -> b.getStatus() == KujiBoxStatus.CLOSED)
                .findFirst();
        if (lastClosed.isEmpty()) {
            return Collections.emptyList();
        }

        KujiBox loaded = kujiBoxRepository.findByIdWithTiers(lastClosed.get().getId())
                .orElse(lastClosed.get());
        return loaded.getTiers().stream()
                .sorted(TIER_ORDER)
                .map(t -> {
                    // Auto-created prize products are one-shot per box. Clear the link
                    // on the cloned template so the user creates fresh ones — the
                    // previous box's products are soft-deleted at close anyway.
                    boolean autoCreated = Boolean.TRUE.equals(t.getAutoCreatedProduct());
                    return KujiBoxTierResponseDTO.builder()
                            .id(null) // strip id — caller uses these as a starting template
                            .label(t.getLabel())
                            .letter(t.getLetter())
                            .linkedProductId(
                                    autoCreated || t.getLinkedProduct() == null
                                            ? null
                                            : t.getLinkedProduct().getId())
                            .linkedProductName(
                                    autoCreated || t.getLinkedProduct() == null
                                            ? null
                                            : t.getLinkedProduct().getName())
                            .activeCount(t.getActiveCount())
                            .inactiveCount(t.getInactiveCount() != null ? t.getInactiveCount() : 0)
                            .totalCount(
                                    (t.getActiveCount() != null ? t.getActiveCount() : 0)
                                            + (t.getInactiveCount() != null ? t.getInactiveCount() : 0))
                            .price(t.getPrice())
                            .autoCreatedProduct(false)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ===================== Internal helpers =====================

    /**
     * Decrement source LocationInventory and emit a SALE StockMovement for the
     * audit trail. Used when kuji prizes are pulled out of regular inventory into
     * the kuji's internal counters — stock leaves real inventory permanently because
     * it's being sold (via the kuji), so the SALE reason rolls it into sales analytics.
     * There is no destination LocationInventory write because kuji counts live on
     * the tier (activeCount/inactiveCount), not in location_inventory at the machine.
     *
     * Returns the source product id so callers can include it in the
     * syncProductTotals batch.
     */
    private UUID executeKujiSourceRemoval(
            UUID sourceLocationId,
            Product product,
            int quantity,
            UUID actorId,
            Map<String, Object> baseMetadata,
            UUID kujiBoxProductId
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Removal quantity must be positive");
        }

        LocationInventory source = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(sourceLocationId, product.getId())
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Source LocationInventory not found for product " + product.getName()
                                + " at location " + sourceLocationId));

        if (source.getQuantity() < quantity) {
            throw new InsufficientInventoryException(
                    "Cannot remove " + quantity + " units. Source has " + source.getQuantity());
        }

        Location sourceLocation = source.getLocation();
        int sourcePrev = source.getQuantity();
        int newSourceQty = sourcePrev - quantity;

        AuditLog auditLog = createAuditLog(
                actorId,
                StockMovementReason.SALE,
                sourceLocation.getId(),
                sourceLocation.getLocationCode(),
                null,
                null,
                1,
                quantity,
                product.getName(),
                null,
                kujiBoxProductId
        );

        Map<String, Object> metadata = new HashMap<>(baseMetadata != null ? baseMetadata : new HashMap<>());
        metadata.put("kuji_source_removal", true);

        StockMovement removal = StockMovement.builder()
                .auditLog(auditLog)
                .item(product)
                .locationType(mapLocationType(sourceLocation))
                .fromLocationId(sourceLocation.getId())
                .toLocationId(null)
                .previousQuantity(sourcePrev)
                .currentQuantity(newSourceQty)
                .quantityChange(-quantity)
                .reason(StockMovementReason.SALE)
                .actorId(actorId)
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();

        if (newSourceQty == 0) {
            locationInventoryRepository.delete(source);
        } else {
            source.setQuantity(newSourceQty);
            locationInventoryRepository.save(source);
        }

        StockMovement saved = stockMovementRepository.save(removal);
        eventOutboxService.createStockMovementEvent(saved);

        return product.getId();
    }

    /**
     * Inverse of {@link #executeKujiSourceRemoval}: increment a destination LocationInventory
     * and emit a TRANSFER StockMovement (from=null) representing kuji counter → regular
     * inventory. Used at close-box when leftover slips are returned to a real location.
     */
    private void executeKujiCounterReturn(
            Location destinationLocation,
            Product product,
            int quantity,
            UUID actorId,
            Map<String, Object> baseMetadata,
            UUID kujiBoxProductId
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Return quantity must be positive");
        }

        LocationInventory destination = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(destinationLocation.getId(), product.getId())
                .orElseGet(() -> locationInventoryRepository.save(LocationInventory.builder()
                        .location(destinationLocation)
                        .site(destinationLocation.getStorageLocation().getSite())
                        .product(product)
                        .quantity(0)
                        .build()));

        int destPrev = destination.getQuantity();
        int destNext = destPrev + quantity;
        destination.setQuantity(destNext);
        locationInventoryRepository.save(destination);

        AuditLog auditLog = createAuditLog(
                actorId,
                StockMovementReason.TRANSFER,
                null,
                null,
                destinationLocation.getId(),
                destinationLocation.getLocationCode(),
                1,
                quantity,
                product.getName(),
                null,
                kujiBoxProductId
        );

        Map<String, Object> metadata = new HashMap<>(baseMetadata != null ? baseMetadata : new HashMap<>());
        metadata.put("kuji_counter_return", true);

        StockMovement deposit = StockMovement.builder()
                .auditLog(auditLog)
                .item(product)
                .locationType(mapLocationType(destinationLocation))
                .fromLocationId(null)
                .toLocationId(destinationLocation.getId())
                .previousQuantity(destPrev)
                .currentQuantity(destNext)
                .quantityChange(quantity)
                .reason(StockMovementReason.TRANSFER)
                .actorId(actorId)
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();
        StockMovement saved = stockMovementRepository.save(deposit);
        eventOutboxService.createStockMovementEvent(saved);
    }

    /**
     * Execute a transfer pair (one AuditLog with two StockMovements) from source location to
     * destination, creating destination LocationInventory if missing. Mirrors
     * {@link StockMovementService#executeTransfer}.
     */
    private void executeKujiTransfer(
            UUID sourceLocationId,
            Location destinationLocation,
            Product product,
            int quantity,
            UUID actorId,
            StockMovementReason reason,
            Map<String, Object> baseMetadata,
            UUID kujiBoxProductId
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Transfer quantity must be positive");
        }

        // Refuse no-op transfers where source equals destination — they would either zero out
        // the inventory (if quantity == row.quantity) or be a useless round-trip.
        if (sourceLocationId.equals(destinationLocation.getId())) {
            throw new IllegalArgumentException(
                    "Source and destination must be different locations (both are "
                            + destinationLocation.getLocationCode() + ")");
        }

        LocationInventory source = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(sourceLocationId, product.getId())
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Source LocationInventory not found for product " + product.getName()
                                + " at location " + sourceLocationId));

        if (source.getQuantity() < quantity) {
            throw new InsufficientInventoryException(
                    "Cannot transfer " + quantity + " units. Source has " + source.getQuantity());
        }

        Location sourceLocation = source.getLocation();

        LocationInventory destination = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(destinationLocation.getId(), product.getId())
                .orElseGet(() -> locationInventoryRepository.save(LocationInventory.builder()
                        .location(destinationLocation)
                        .site(destinationLocation.getStorageLocation().getSite())
                        .product(product)
                        .quantity(0)
                        .build()));

        int sourcePrev = source.getQuantity();
        int destPrev = destination.getQuantity();

        AuditLog auditLog = createAuditLog(
                actorId,
                reason,
                sourceLocation.getId(),
                sourceLocation.getLocationCode(),
                destinationLocation.getId(),
                destinationLocation.getLocationCode(),
                1,
                quantity,
                product.getName(),
                null,
                kujiBoxProductId
        );

        Map<String, Object> withdrawalMetadata = new HashMap<>(baseMetadata != null ? baseMetadata : new HashMap<>());
        withdrawalMetadata.put("transfer", true);

        Map<String, Object> depositMetadata = new HashMap<>(baseMetadata != null ? baseMetadata : new HashMap<>());
        depositMetadata.put("transfer", true);

        OffsetDateTime now = OffsetDateTime.now();

        LocationType sourceType = mapLocationType(sourceLocation);
        LocationType destType = mapLocationType(destinationLocation);

        StockMovement withdrawal = StockMovement.builder()
                .auditLog(auditLog)
                .item(product)
                .locationType(sourceType)
                .fromLocationId(sourceLocation.getId())
                .toLocationId(destinationLocation.getId())
                .previousQuantity(sourcePrev)
                .currentQuantity(sourcePrev - quantity)
                .quantityChange(-quantity)
                .reason(reason)
                .actorId(actorId)
                .at(now)
                .metadata(withdrawalMetadata)
                .build();

        StockMovement deposit = StockMovement.builder()
                .auditLog(auditLog)
                .item(product)
                .locationType(destType)
                .fromLocationId(sourceLocation.getId())
                .toLocationId(destinationLocation.getId())
                .previousQuantity(destPrev)
                .currentQuantity(destPrev + quantity)
                .quantityChange(quantity)
                .reason(reason)
                .actorId(actorId)
                .at(now)
                .metadata(depositMetadata)
                .build();

        // Update inventory rows
        int newSourceQty = sourcePrev - quantity;
        if (newSourceQty == 0) {
            locationInventoryRepository.delete(source);
        } else {
            source.setQuantity(newSourceQty);
            locationInventoryRepository.save(source);
        }
        destination.setQuantity(destPrev + quantity);
        locationInventoryRepository.save(destination);

        StockMovement savedW = stockMovementRepository.save(withdrawal);
        StockMovement savedD = stockMovementRepository.save(deposit);
        eventOutboxService.createStockMovementEvent(savedW);
        eventOutboxService.createStockMovementEvent(savedD);
    }

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
            String notes,
            UUID kujiBoxProductId
    ) {
        User user = null;
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
                .kujiBoxProductId(kujiBoxProductId)
                .build();
        return auditLogRepository.save(auditLog);
    }

    private Map<String, Object> buildTierMetadata(KujiBox box, KujiBoxTier tier, String action) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("kuji_box_id", box.getId().toString());
        if (tier != null && tier.getId() != null) {
            metadata.put("kuji_box_tier_id", tier.getId().toString());
        }
        if (tier != null && tier.getLinkedProduct() != null) {
            metadata.put("kuji_product_id", tier.getLinkedProduct().getId().toString());
        }
        if (action != null) {
            metadata.put("action", action);
        }
        return metadata;
    }

    private String formatDrawSummary(
            List<RecordDrawRequestDTO.DrawLine> draws,
            Map<UUID, KujiBoxTier> tiersById
    ) {
        List<String> parts = new ArrayList<>();
        for (RecordDrawRequestDTO.DrawLine draw : draws) {
            KujiBoxTier tier = tiersById.get(draw.getTierId());
            if (tier == null) continue;
            parts.add(formatTierLine(
                    tier.getLetter(),
                    tier.getLabel(),
                    tier.getLinkedProduct() != null ? tier.getLinkedProduct().getName() : null,
                    draw.getQuantity(),
                    resolveUnitValue(tier, tier.getLinkedProduct())
            ));
        }
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    private String formatUndoSummary(List<StockMovement> originals) {
        List<String> parts = new ArrayList<>();
        for (StockMovement mv : originals) {
            Map<String, Object> meta = mv.getMetadata();
            String letter = stringOrNull(meta, "tier_letter");
            String label = stringOrNull(meta, "tier_label");
            int qty = readQuantityFromMetadata(meta, 0);
            if (qty == 0 && mv.getQuantityChange() != null) {
                qty = Math.abs(mv.getQuantityChange());
            }
            // Linked tier: prize product is StockMovement.item. Free-text tier:
            // item is the box parent product, so don't repeat that as the prize name.
            String linkedName = null;
            if (mv.getQuantityChange() != null && mv.getQuantityChange() < 0
                    && mv.getItem() != null) {
                linkedName = mv.getItem().getName();
            }
            // Legacy rows (pre-snapshot feature) don't carry unit_value — passing null
            // suppresses the price suffix entirely so we don't show a misleading "$0".
            parts.add(formatTierLine(letter, label, linkedName, qty, readUnitValueFromMetadata(meta)));
        }
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    /**
     * Header for kuji audit notes that name a tier: "<prize name> · <label>" when a
     * linked product is set, else just the label. Used so activity-log readers can
     * see which prize an adjustment hit, not only its position label.
     */
    private String tierAuditHeader(KujiBoxTier tier) {
        String label = tier.getLabel();
        Product linked = tier.getLinkedProduct();
        if (linked == null) return label;
        return linked.getName() + " · " + label;
    }

    private String stringOrNull(Map<String, Object> meta, String key) {
        if (meta == null) return null;
        Object v = meta.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * Per-slip value used for the daily-payout chart and activity-log price suffix.
     * Falls back tier.price → linked product.msrp → 0, and is stamped into stock-movement
     * metadata at draw time so historical totals stay fixed if prices change later.
     */
    private static BigDecimal resolveUnitValue(KujiBoxTier tier, Product linked) {
        if (tier != null && tier.getPrice() != null) return tier.getPrice();
        if (linked != null && linked.getMsrp() != null) return linked.getMsrp();
        return BigDecimal.ZERO;
    }

    private static BigDecimal readUnitValueFromMetadata(Map<String, Object> meta) {
        if (meta == null) return null;
        Object raw = meta.get("unit_value");
        if (raw == null) return null;
        if (raw instanceof BigDecimal bd) return bd;
        if (raw instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(raw.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatTierLine(
            String letter,
            String label,
            String linkedProductName,
            int qty,
            BigDecimal unitValue
    ) {
        // Prize name (linked product) leads when present so the reader sees what was
        // awarded first, with the tier label as secondary context in parens. When no
        // linked product exists, fall back to the tier letter/label as the head.
        String tierTag;
        if (letter != null && !letter.isBlank()) {
            tierTag = letter;
        } else if (label != null && !label.isBlank()) {
            tierTag = label;
        } else {
            tierTag = "Prize";
        }
        StringBuilder sb = new StringBuilder();
        if (linkedProductName != null && !linkedProductName.isBlank()) {
            sb.append(linkedProductName).append(" (").append(tierTag).append(")");
        } else {
            sb.append(tierTag);
        }
        sb.append(" × ").append(qty);
        // Snapshot-based price suffix. Present-positive shows the line total; present-zero
        // surfaces an explicit "Not set" so admins notice missing tier prices; absent (legacy
        // rows from before the snapshot feature) appends nothing.
        if (unitValue != null) {
            if (unitValue.signum() > 0) {
                BigDecimal total = unitValue.multiply(BigDecimal.valueOf(qty));
                sb.append(" — ").append(String.format("$%,.2f", total));
            } else {
                sb.append(" — Not set");
            }
        }
        return sb.toString();
    }

    /**
     * Mirrors StockMovementService — stamps the canonical {@code intake_unit}/{@code intake_qty}
     * keys when the caller's request was typed in box units. Audit log readers use these
     * to render "+2 boxes (72 packs)" instead of just "+72".
     */
    private static void applyIntakeMetadata(Map<String, Object> metadata, String intakeUnit, Integer intakeQty) {
        if ("box".equalsIgnoreCase(intakeUnit) && intakeQty != null && intakeQty > 0) {
            metadata.put("intake_unit", "box");
            metadata.put("intake_qty", intakeQty);
        }
    }

    private Map<String, Object> baseDrawMetadata(KujiBox box, KujiBoxTier tier, UUID auditLogId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("kuji_box_id", box.getId().toString());
        if (tier != null && tier.getId() != null) {
            metadata.put("kuji_box_tier_id", tier.getId().toString());
        }
        if (tier != null && tier.getLinkedProduct() != null) {
            metadata.put("kuji_product_id", tier.getLinkedProduct().getId().toString());
        }
        if (auditLogId != null) {
            metadata.put("draw_audit_log_id", auditLogId.toString());
        }
        return metadata;
    }

    private void emitDrawNotification(
            NotificationType type,
            String message,
            KujiBox box,
            UUID actorId,
            String notes,
            List<Map<String, Object>> tierSummaries,
            OffsetDateTime occurredAt
    ) {
        try {
            Product parentProduct = box.getProduct();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("kuji_product_id", parentProduct.getId().toString());
            metadata.put("kuji_product_name", parentProduct.getName());
            if (parentProduct.getKujiSlackWebhookUrl() != null) {
                metadata.put("kuji_slack_webhook_url", parentProduct.getKujiSlackWebhookUrl());
            }
            metadata.put("box_id", box.getId().toString());
            metadata.put("box_label", box.getLabel());

            String locationName = box.getLocation().getLocationCode();
            try {
                if (box.getLocation().getStorageLocation() != null
                        && box.getLocation().getStorageLocation().getName() != null) {
                    locationName = box.getLocation().getLocationCode()
                            + " / " + box.getLocation().getStorageLocation().getName();
                }
            } catch (Exception ignored) {
                // fall back to locationCode
            }
            metadata.put("location_id", box.getLocation().getId().toString());
            metadata.put("location_name", locationName);
            metadata.put("actor_name", resolveActorName(actorId));
            metadata.put("occurred_at", occurredAt.toString());
            if (notes != null) {
                metadata.put("notes", notes);
            }
            metadata.put("tiers", tierSummaries);

            int totalAfter = box.getTiers() == null
                    ? 0
                    : box.getTiers().stream()
                            .mapToInt(t -> t.getActiveCount() != null ? t.getActiveCount() : 0)
                            .sum();
            metadata.put("total_count_after", totalAfter);

            Notification notif = Notification.builder()
                    .type(type)
                    .severity(NotificationSeverity.INFO)
                    .message(message)
                    .itemId(parentProduct.getId())
                    .metadata(metadata)
                    .via(List.of("slack"))
                    .build();
            notificationService.createNotification(notif);
        } catch (Exception e) {
            log.warn("Failed to enqueue kuji notification (type={}, boxId={}): {}",
                    type, box.getId(), e.getMessage());
        }
    }

    private String resolveActorName(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return userRepository.findById(actorId)
                .map(User::getFullName)
                .orElse(null);
    }

    private LocationType mapLocationType(Location location) {
        if (location == null || location.getStorageLocation() == null) {
            return LocationType.NOT_ASSIGNED;
        }
        String code = location.getStorageLocation().getCode();
        return switch (code) {
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
            default -> LocationType.NOT_ASSIGNED;
        };
    }

    private boolean isMachineLocation(Location location) {
        return switch (mapLocationType(location)) {
            case SINGLE_CLAW_MACHINE, DOUBLE_CLAW_MACHINE, FOUR_CORNER_MACHINE,
                 PUSHER_MACHINE, GACHAPON, KEYCHAIN_MACHINE -> true;
            default -> false;
        };
    }

    /**
     * Native query to find StockMovements whose metadata.kuji_box_id matches boxId
     * and whose `at` is at or after the given timestamp. Used for reopen, which needs to
     * locate the close-time transfer-out movements without modifying the StockMovementRepository.
     */
    @SuppressWarnings("unchecked")
    private List<StockMovement> findKujiBoxMovementsAtOrAfter(UUID boxId, OffsetDateTime since) {
        return entityManager.createNativeQuery(
                "SELECT * FROM stock_movements WHERE metadata->>'kuji_box_id' = :boxId AND at >= :since",
                StockMovement.class)
                .setParameter("boxId", boxId.toString())
                .setParameter("since", since)
                .getResultList();
    }

    /**
     * Native query: find any KUJI_DRAW_REVERSED movement that already references the given
     * audit log via metadata.reverses_audit_log_id. Used for the "draw already undone" guard.
     */
    @SuppressWarnings("unchecked")
    private List<StockMovement> findKujiReversalsForAuditLog(UUID auditLogId) {
        return entityManager.createNativeQuery(
                "SELECT * FROM stock_movements WHERE reason = 'KUJI_DRAW_REVERSED' "
                        + "AND metadata->>'reverses_audit_log_id' = :auditLogId",
                StockMovement.class)
                .setParameter("auditLogId", auditLogId.toString())
                .getResultList();
    }

    private UUID extractTierIdFromMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object v = metadata.get("kuji_box_tier_id");
        if (v == null) {
            return null;
        }
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int readQuantityFromMetadata(Map<String, Object> metadata, int fallback) {
        return readQuantityFromMetadata(metadata, "slip_quantity", fallback);
    }

    private int readQuantityFromMetadata(Map<String, Object> metadata, String key, int fallback) {
        if (metadata == null) {
            return fallback;
        }
        Object v = metadata.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    // ===================== DTO mapping =====================

    private KujiBoxResponseDTO toResponseDTO(KujiBox box) {
        Product product = box.getProduct();
        Location location = box.getLocation();

        List<KujiBoxTierResponseDTO> tiers = box.getTiers() == null
                ? Collections.emptyList()
                : box.getTiers().stream()
                .sorted(TIER_ORDER)
                .map(t -> KujiBoxTierResponseDTO.builder()
                        .id(t.getId())
                        .label(t.getLabel())
                        .letter(t.getLetter())
                        .linkedProductId(
                                t.getLinkedProduct() != null ? t.getLinkedProduct().getId() : null)
                        .linkedProductName(
                                t.getLinkedProduct() != null ? t.getLinkedProduct().getName() : null)
                        .linkedProductImageUrl(
                                t.getLinkedProduct() != null ? t.getLinkedProduct().getImageUrl() : null)
                        .linkedProductPacksPerBox(
                                t.getLinkedProduct() != null
                                        ? t.getLinkedProduct().getPacksPerBox()
                                        : null)
                        .activeCount(t.getActiveCount())
                        .inactiveCount(t.getInactiveCount() != null ? t.getInactiveCount() : 0)
                        .drawnCount(t.getDrawnCount() != null ? t.getDrawnCount() : 0)
                        .totalCount(
                                (t.getActiveCount() != null ? t.getActiveCount() : 0)
                                        + (t.getInactiveCount() != null ? t.getInactiveCount() : 0))
                        .price(t.getPrice())
                        .linkedProductPrice(
                                t.getLinkedProduct() != null ? t.getLinkedProduct().getMsrp() : null)
                        .autoCreatedProduct(Boolean.TRUE.equals(t.getAutoCreatedProduct()))
                        .build())
                .collect(Collectors.toList());

        // Box-level totalCount is the sum of active slips (the winnable pool).
        // Chance % calculations and the box header tile use this value.
        int totalCount = tiers.stream()
                .mapToInt(t -> t.getActiveCount() != null ? t.getActiveCount() : 0)
                .sum();

        // Batch openedBy/closedBy resolution into a single query when both are present.
        UUID openedById = box.getOpenedBy();
        UUID closedById = box.getClosedBy();
        Map<UUID, String> userNamesById = new HashMap<>();
        if (openedById != null || closedById != null) {
            Set<UUID> userIds = new HashSet<>();
            if (openedById != null) userIds.add(openedById);
            if (closedById != null) userIds.add(closedById);
            for (User u : userRepository.findAllById(userIds)) {
                userNamesById.put(u.getId(), u.getFullName());
            }
        }
        String openedByName = openedById != null ? userNamesById.get(openedById) : null;
        String closedByName = closedById != null ? userNamesById.get(closedById) : null;

        String storageLocationName = null;
        try {
            if (location.getStorageLocation() != null) {
                storageLocationName = location.getStorageLocation().getName();
            }
        } catch (Exception ignored) {
            // leave null
        }

        return KujiBoxResponseDTO.builder()
                .id(box.getId())
                .productId(product.getId())
                .productName(product.getName())
                .locationId(location.getId())
                .locationCode(location.getLocationCode())
                .locationName(storageLocationName)
                .machineDisplayId(box.getMachineDisplay() != null
                        ? box.getMachineDisplay().getId() : null)
                .status(box.getStatus())
                .label(box.getLabel())
                .notes(box.getNotes())
                .openedAt(box.getOpenedAt())
                .openedBy(box.getOpenedBy())
                .openedByName(openedByName)
                .closedAt(box.getClosedAt())
                .closedBy(box.getClosedBy())
                .closedByName(closedByName)
                .createdAt(box.getCreatedAt())
                .updatedAt(box.getUpdatedAt())
                .tiers(tiers)
                .totalCount(totalCount)
                .build();
    }

    // ===================== Allocation reads =====================

    /**
     * Allocations of OPEN kuji boxes at a given location, one row per linked tier with count > 0.
     * Used by the location detail modal (virtual "S1-Display" row when machineDisplayId set)
     * and the adjust/transfer "available" cap.
     */
    public List<KujiAllocationByLocationDTO> getAllocationsByLocation(UUID locationId) {
        List<KujiBoxTier> tiers = kujiBoxTierRepository.findOpenAllocationsByLocation(locationId);
        return tiers.stream()
                .map(t -> {
                    KujiBox b = t.getBox();
                    MachineDisplay md = b.getMachineDisplay();
                    UUID mdId = md != null ? md.getId() : null;
                    String machineCode = md != null && md.getLocation() != null
                            ? md.getLocation().getLocationCode()
                            : null;
                    return KujiAllocationByLocationDTO.builder()
                            .boxId(b.getId())
                            .boxLabel(b.getLabel())
                            .tierId(t.getId())
                            .tierLabel(t.getLabel())
                            .tierLetter(t.getLetter())
                            .linkedProductId(t.getLinkedProduct().getId())
                            .linkedProductName(t.getLinkedProduct().getName())
                            .count(
                                    (t.getActiveCount() != null ? t.getActiveCount() : 0)
                                            + (t.getInactiveCount() != null ? t.getInactiveCount() : 0))
                            .machineDisplayId(mdId)
                            .machineCode(machineCode)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Allocations referencing a given product across all OPEN kuji boxes — drives the
     * "where is this product?" virtual rows in the product modal.
     */
    public List<KujiAllocationByProductDTO> getAllocationsByProduct(UUID productId) {
        List<KujiBoxTier> tiers = kujiBoxTierRepository.findOpenAllocationsByProduct(productId);
        return tiers.stream()
                .map(t -> {
                    KujiBox b = t.getBox();
                    Location loc = b.getLocation();
                    MachineDisplay md = b.getMachineDisplay();
                    UUID mdId = md != null ? md.getId() : null;
                    String machineCode = md != null && md.getLocation() != null
                            ? md.getLocation().getLocationCode()
                            : null;
                    return KujiAllocationByProductDTO.builder()
                            .boxId(b.getId())
                            .boxLabel(b.getLabel())
                            .locationId(loc != null ? loc.getId() : null)
                            .locationCode(loc != null ? loc.getLocationCode() : null)
                            .machineDisplayId(mdId)
                            .machineCode(machineCode)
                            .tierId(t.getId())
                            .tierLabel(t.getLabel())
                            .tierLetter(t.getLetter())
                            .count(
                                    (t.getActiveCount() != null ? t.getActiveCount() : 0)
                                            + (t.getInactiveCount() != null ? t.getInactiveCount() : 0))
                            .build();
                })
                .collect(Collectors.toList());
    }
}
