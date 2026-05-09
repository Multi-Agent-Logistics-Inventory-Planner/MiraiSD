package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.kuji.AddSlipRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.CloseKujiBoxRequestDTO;
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
                // Birth a child product under the kuji parent via the canonical
                // ProductService.createProduct path. Pass initialStock = slip + heldBack
                // so the product starts active and Product.quantity is correct from the
                // start (createProduct sets quantity directly for prize children without
                // creating a LocationInventory — kuji code creates that row at the box
                // location below).
                int autoSlipCount = tierDto.getCount() != null ? tierDto.getCount() : 0;
                int autoHeldBack = tierDto.getHeldBackQuantity() == null ? 0 : tierDto.getHeldBackQuantity();
                int autoInitialStock = autoSlipCount + autoHeldBack;
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
                        autoInitialStock > 0 ? autoInitialStock : null, // initialStock
                        null,                                       // kujiType
                        null                                        // kujiSlackWebhookUrl
                );
                if (autoInitialStock <= 0) {
                    // No materialized inventory (rare: 0-count tier with no held-back). Still
                    // make it drawable since opening a tier with 0 slips is valid bookkeeping.
                    linkedProduct.setIsActive(true);
                    linkedProduct = productRepository.save(linkedProduct);
                }
            }

            KujiBoxTier tier = KujiBoxTier.builder()
                    .box(box)
                    .label(tierDto.getLabel())
                    .letter(tierDto.getLetter())
                    .linkedProduct(linkedProduct)
                    .count(tierDto.getCount() != null ? tierDto.getCount() : 0)
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
            int slipCount = tier.getCount() == null ? 0 : tier.getCount();
            int heldBack = dto.getHeldBackQuantity() == null ? 0 : dto.getHeldBackQuantity();
            int totalQuantity = slipCount + heldBack;
            if (totalQuantity <= 0) {
                continue;
            }

            if (Boolean.TRUE.equals(tier.getAutoCreatedProduct())) {
                // Birth inventory directly at the box location.
                Product newChild = tier.getLinkedProduct();
                LocationInventory inv = LocationInventory.builder()
                        .location(box.getLocation())
                        .site(box.getLocation().getStorageLocation().getSite())
                        .product(newChild)
                        .quantity(totalQuantity)
                        .build();
                locationInventoryRepository.save(inv);

                Map<String, Object> metadata = buildTierMetadata(box, tier, "open_box_auto_create");
                if (heldBack > 0) {
                    metadata.put("slipQuantity", slipCount);
                    metadata.put("heldBackQuantity", heldBack);
                }
                StockMovement birth = StockMovement.builder()
                        .item(newChild)
                        .locationType(mapLocationType(box.getLocation()))
                        .fromLocationId(null)
                        .toLocationId(box.getLocation().getId())
                        .previousQuantity(0)
                        .currentQuantity(totalQuantity)
                        .quantityChange(totalQuantity)
                        .reason(StockMovementReason.INITIAL_STOCK)
                        .actorId(request.getActorId())
                        .at(now)
                        .metadata(metadata)
                        .build();
                stockMovementRepository.save(birth);
                // Auto-create child: Product.quantity was already set correctly by
                // createProduct(initialStock=totalQuantity). No syncProductTotals needed.
                continue;
            }

            if (dto.getSourceLocationId() == null) {
                throw new IllegalArgumentException(
                        "Tier '" + tier.getLabel() + "' has a linked product but no sourceLocationId");
            }

            Map<String, Object> metadata = buildTierMetadata(box, tier, "open_box_transfer_in");
            if (heldBack > 0) {
                metadata.put("slipQuantity", slipCount);
                metadata.put("heldBackQuantity", heldBack);
            }

            executeKujiTransfer(
                    dto.getSourceLocationId(),
                    box.getLocation(),
                    tier.getLinkedProduct(),
                    totalQuantity,
                    request.getActorId(),
                    StockMovementReason.TRANSFER,
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
            if (tier.getLinkedProduct() == null) {
                continue;
            }

            Optional<LocationInventory> existingOpt = locationInventoryRepository
                    .findByLocation_IdAndProduct_Id(box.getLocation().getId(), tier.getLinkedProduct().getId());

            if (Boolean.TRUE.equals(tier.getAutoCreatedProduct())) {
                // Auto-created prize products are scoped to this one box. Zero out any
                // leftover inventory at the box (audited via a REMOVED stock movement),
                // soft-delete the product so it disappears from every product list, and
                // hard-delete the image on the frontend after closeBox returns. Reopen
                // restores isActive but does NOT replenish inventory — user runs
                // Transfer-In More if they want stock back.
                Product child = tier.getLinkedProduct();
                if (existingOpt.isPresent() && existingOpt.get().getQuantity() > 0) {
                    LocationInventory inv = existingOpt.get();
                    int prev = inv.getQuantity();
                    StockMovement removal = StockMovement.builder()
                            .item(child)
                            .locationType(mapLocationType(box.getLocation()))
                            .fromLocationId(box.getLocation().getId())
                            .toLocationId(null)
                            .previousQuantity(prev)
                            .currentQuantity(0)
                            .quantityChange(-prev)
                            .reason(StockMovementReason.REMOVED)
                            .actorId(request.getActorId())
                            .at(OffsetDateTime.now())
                            .metadata(buildTierMetadata(box, tier, "close_box_auto_remove"))
                            .build();
                    stockMovementRepository.save(removal);
                    locationInventoryRepository.delete(inv);
                }
                child.setIsActive(false);
                productRepository.save(child);
                affectedProductIds.add(child.getId());
                continue;
            }

            if (existingOpt.isEmpty() || existingOpt.get().getQuantity() <= 0) {
                continue;
            }
            int qty = existingOpt.get().getQuantity();
            UUID destinationLocationId = destinationByTierId.get(tier.getId());
            if (destinationLocationId == null) {
                throw new IllegalArgumentException(
                        "Tier " + tier.getLabel() + ": provide a destination for "
                                + qty + " units of " + tier.getLinkedProduct().getName());
            }

            Location destinationLocation = locationRepository.findById(destinationLocationId)
                    .orElseThrow(() -> new LocationNotFoundException(
                            "Destination location not found: " + destinationLocationId));

            executeKujiTransfer(
                    box.getLocation().getId(),
                    destinationLocation,
                    tier.getLinkedProduct(),
                    qty,
                    request.getActorId(),
                    StockMovementReason.TRANSFER,
                    buildTierMetadata(box, tier, "close_box_transfer_out"),
                    box.getProduct().getId()
            );
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

        // Find close-time transfer-out movements: reason=TRANSFER, metadata.kuji_box_id == boxId,
        // at >= box.closedAt, fromLocationId == box.location_id, with quantityChange < 0.
        // We reverse each by transferring quantityChange-magnitude FROM the destination BACK TO box.location.
        OffsetDateTime closedAt = box.getClosedAt();
        Set<UUID> affectedProductIds = new HashSet<>();
        if (closedAt != null) {
            List<StockMovement> closeMovements = findKujiBoxMovementsAtOrAfter(boxId, closedAt);

            for (StockMovement mv : closeMovements) {
                if (mv.getReason() != StockMovementReason.TRANSFER) {
                    continue;
                }
                if (mv.getQuantityChange() == null || mv.getQuantityChange() >= 0) {
                    continue; // We only want the withdrawal half of each transfer pair.
                }
                if (mv.getFromLocationId() == null
                        || !mv.getFromLocationId().equals(box.getLocation().getId())) {
                    continue;
                }
                if (mv.getToLocationId() == null) {
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
            if (tier.getCount() == null || tier.getCount() < draw.getQuantity()) {
                throw new IllegalArgumentException(
                        "Tier '" + tier.getLabel() + "' has only "
                                + (tier.getCount() == null ? 0 : tier.getCount())
                                + " slips remaining; cannot draw " + draw.getQuantity());
            }
            if (tier.getLinkedProduct() != null) {
                LocationInventory inv = locationInventoryRepository
                        .findByLocation_IdAndProduct_Id(box.getLocation().getId(),
                                tier.getLinkedProduct().getId())
                        .orElse(null);
                int available = inv != null ? inv.getQuantity() : 0;
                if (available < draw.getQuantity()) {
                    throw new IllegalStateException(
                            "Out of " + tier.getLinkedProduct().getName()
                                    + " at this box's location — Transfer-In more, "
                                    + "or Edit Tier to change the linked product.");
                }
            }
            totalQuantity += draw.getQuantity();
        }

        // Decrement counts (in-memory; saved when tier persists below)
        for (RecordDrawRequestDTO.DrawLine draw : request.getDraws()) {
            KujiBoxTier tier = tiersById.get(draw.getTierId());
            tier.setCount(tier.getCount() - draw.getQuantity());
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
        Set<UUID> affectedProductIds = new HashSet<>();
        List<Map<String, Object>> tierSummaries = new ArrayList<>();

        for (RecordDrawRequestDTO.DrawLine draw : request.getDraws()) {
            KujiBoxTier tier = tiersById.get(draw.getTierId());
            int qty = draw.getQuantity();

            Map<String, Object> metadata = baseDrawMetadata(box, tier, parentLog.getId());
            metadata.put("tier_label", tier.getLabel());
            if (tier.getLetter() != null) {
                metadata.put("tier_letter", tier.getLetter());
            }
            // Always store slip_quantity so undo can reliably restore tier counts even when
            // quantityChange is 0 (free-text tiers).
            metadata.put("slip_quantity", qty);

            StockMovement movement;
            if (tier.getLinkedProduct() != null) {
                Product linked = tier.getLinkedProduct();
                LocationInventory inv = locationInventoryRepository
                        .findByLocation_IdAndProduct_Id(box.getLocation().getId(), linked.getId())
                        .orElseThrow(() -> new InventoryNotFoundException(
                                "LocationInventory missing for linked product after lock check: "
                                        + linked.getId()));

                int prev = inv.getQuantity();
                int next = prev - qty;
                if (next == 0) {
                    locationInventoryRepository.delete(inv);
                } else {
                    inv.setQuantity(next);
                    locationInventoryRepository.save(inv);
                }

                LocationType locType = mapLocationType(box.getLocation());
                movement = StockMovement.builder()
                        .auditLog(parentLog)
                        .item(linked)
                        .locationType(locType)
                        .fromLocationId(box.getLocation().getId())
                        .toLocationId(null)
                        .previousQuantity(prev)
                        .currentQuantity(next)
                        .quantityChange(-qty)
                        .reason(StockMovementReason.KUJI_PRIZE_WON)
                        .actorId(request.getActorId())
                        .at(now)
                        .metadata(metadata)
                        .build();
                affectedProductIds.add(linked.getId());
            } else {
                // Free-text tier: emit a quantityChange=0 movement on the parent product
                movement = StockMovement.builder()
                        .auditLog(parentLog)
                        .item(parentProduct)
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
            }

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
            line.put("count_after", tier.getCount());
            tierSummaries.add(line);
        }

        if (!affectedProductIds.isEmpty()) {
            stockMovementService.syncProductTotals(new ArrayList<>(affectedProductIds));
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

            if (mv.getQuantityChange() != null && mv.getQuantityChange() < 0
                    && mv.getItem() != null
                    && mv.getFromLocationId() != null
                    && mv.getFromLocationId().equals(box.getLocation().getId())) {
                // Restore inventory at box.location for the linked product
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

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("kuji_box_id", boxId.toString());
                metadata.put("reverses_audit_log_id", auditLogId.toString());
                metadata.put("reverses_movement_id", String.valueOf(mv.getId()));
                if (tier != null) {
                    metadata.put("kuji_box_tier_id", tier.getId().toString());
                }
                metadata.put("kuji_product_id", linked.getId().toString());

                StockMovement reverse = StockMovement.builder()
                        .auditLog(reverseLog)
                        .item(linked)
                        .locationType(mapLocationType(box.getLocation()))
                        .fromLocationId(null)
                        .toLocationId(box.getLocation().getId())
                        .previousQuantity(prev)
                        .currentQuantity(next)
                        .quantityChange(qty)
                        .reason(StockMovementReason.KUJI_DRAW_REVERSED)
                        .actorId(actorId)
                        .at(now)
                        .metadata(metadata)
                        .build();
                StockMovement saved = stockMovementRepository.save(reverse);
                eventOutboxService.createStockMovementEvent(saved);
            } else {
                // Free-text tier (or zero-quantity movement) — write symmetric reversal with qty=0
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("kuji_box_id", boxId.toString());
                metadata.put("reverses_audit_log_id", auditLogId.toString());
                metadata.put("reverses_movement_id", String.valueOf(mv.getId()));
                if (tier != null) {
                    metadata.put("kuji_box_tier_id", tier.getId().toString());
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
            }

            // Restore tier slip count
            if (tier != null) {
                Integer current = tier.getCount() != null ? tier.getCount() : 0;
                tier.setCount(current + qty);
                kujiBoxTierRepository.save(tier);

                Map<String, Object> line = new HashMap<>();
                line.put("tier_id", tier.getId().toString());
                line.put("label", tier.getLabel());
                line.put("letter", tier.getLetter());
                line.put("linked_product_name",
                        tier.getLinkedProduct() != null ? tier.getLinkedProduct().getName() : null);
                line.put("price", tier.getPrice());
                line.put("quantity", qty);
                line.put("count_after", tier.getCount());
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
        tier.setCount((tier.getCount() == null ? 0 : tier.getCount()) + quantity);
        kujiBoxTierRepository.save(tier);

        Product parentProduct = box.getProduct();
        // Slip adjustments don't change inventory — only the tier's slip count. The
        // AuditLog drives the kuji session activity log; no StockMovement or outbox
        // event is needed (and none is consumed downstream for KUJI_SLIP_ADJUSTMENT).
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
                "Kuji slip added: " + tier.getLabel(),
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
        if (autoCreateMint) {
            mintAutoCreatedAtBox(box, tier, request.getQuantity(), request.getActorId(), "transfer_in_more");
        } else {
            Map<String, Object> metadata = baseDrawMetadata(box, tier, null);
            metadata.put("action", "transfer_in_more");
            executeKujiTransfer(
                    request.getSourceLocationId(),
                    box.getLocation(),
                    tier.getLinkedProduct(),
                    request.getQuantity(),
                    request.getActorId(),
                    StockMovementReason.TRANSFER,
                    metadata,
                    box.getProduct().getId()
            );
        }

        tier.setCount((tier.getCount() == null ? 0 : tier.getCount()) + request.getQuantity());
        kujiBoxTierRepository.save(tier);

        // Auto-create mint already updated Product.quantity in mintAutoCreatedAtBox; only
        // call syncProductTotals for transfers that move stock across locations.
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
        if (autoCreateMint) {
            mintAutoCreatedAtBox(box, tier, request.getQuantity(), request.getActorId(), "transfer_in_inventory_only");
        } else {
            Map<String, Object> metadata = baseDrawMetadata(box, tier, null);
            metadata.put("action", "transfer_in_inventory_only");
            executeKujiTransfer(
                    request.getSourceLocationId(),
                    box.getLocation(),
                    tier.getLinkedProduct(),
                    request.getQuantity(),
                    request.getActorId(),
                    StockMovementReason.TRANSFER,
                    metadata,
                    box.getProduct().getId()
            );
        }

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
            // Pass initialStock = slip + heldBack so createProduct sets Product.quantity
            // and isActive correctly from the start. Kuji code below still creates the
            // LocationInventory row at the box location.
            int autoSlipCount = request.getCount() != null ? request.getCount() : 0;
            int autoHeldBack = request.getHeldBackQuantity() == null ? 0 : request.getHeldBackQuantity();
            int autoInitialStock = autoSlipCount + autoHeldBack;
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
                    autoInitialStock > 0 ? autoInitialStock : null, // initialStock
                    null,                                       // kujiType
                    null                                        // kujiSlackWebhookUrl
            );
            if (autoInitialStock <= 0) {
                linkedProduct.setIsActive(true);
                linkedProduct = productRepository.save(linkedProduct);
            }
        }

        KujiBoxTier tier = KujiBoxTier.builder()
                .box(box)
                .label(request.getLabel())
                .letter(request.getLetter())
                .linkedProduct(linkedProduct)
                .count(request.getCount() != null ? request.getCount() : 0)
                .price(request.getPrice())
                .autoCreatedProduct(autoCreate)
                .build();
        tier = kujiBoxTierRepository.save(tier);
        if (box.getTiers() != null) {
            box.getTiers().add(tier);
        }
        entityManager.flush();

        // Materialize inventory: birth at box for auto-create, transfer-in otherwise.
        int slipCount = tier.getCount() == null ? 0 : tier.getCount();
        int heldBack = request.getHeldBackQuantity() == null ? 0 : request.getHeldBackQuantity();
        int totalQuantity = slipCount + heldBack;

        Set<UUID> affectedProductIds = new HashSet<>();
        if (linkedProduct != null && totalQuantity > 0) {
            if (autoCreate) {
                LocationInventory inv = LocationInventory.builder()
                        .location(box.getLocation())
                        .site(box.getLocation().getStorageLocation().getSite())
                        .product(linkedProduct)
                        .quantity(totalQuantity)
                        .build();
                locationInventoryRepository.save(inv);

                Map<String, Object> metadata = buildTierMetadata(box, tier, "add_tier_auto_create");
                if (heldBack > 0) {
                    metadata.put("slipQuantity", slipCount);
                    metadata.put("heldBackQuantity", heldBack);
                }
                StockMovement birth = StockMovement.builder()
                        .item(linkedProduct)
                        .locationType(mapLocationType(box.getLocation()))
                        .fromLocationId(null)
                        .toLocationId(box.getLocation().getId())
                        .previousQuantity(0)
                        .currentQuantity(totalQuantity)
                        .quantityChange(totalQuantity)
                        .reason(StockMovementReason.INITIAL_STOCK)
                        .actorId(request.getActorId())
                        .at(OffsetDateTime.now())
                        .metadata(metadata)
                        .build();
                stockMovementRepository.save(birth);
                // Auto-create child: Product.quantity was already set correctly by
                // createProduct(initialStock=totalQuantity). No syncProductTotals needed.
            } else {
                if (request.getSourceLocationId() == null) {
                    throw new IllegalArgumentException(
                            "Tier '" + tier.getLabel() + "' has a linked product but no sourceLocationId");
                }
                Map<String, Object> metadata = buildTierMetadata(box, tier, "add_tier_transfer_in");
                if (heldBack > 0) {
                    metadata.put("slipQuantity", slipCount);
                    metadata.put("heldBackQuantity", heldBack);
                }
                executeKujiTransfer(
                        request.getSourceLocationId(),
                        box.getLocation(),
                        linkedProduct,
                        totalQuantity,
                        request.getActorId(),
                        StockMovementReason.TRANSFER,
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
            if (oldLinked != null) {
                LocationInventory existing = locationInventoryRepository
                        .findByLocation_IdAndProduct_Id(box.getLocation().getId(), oldLinked.getId())
                        .orElse(null);
                int qty = existing != null ? existing.getQuantity() : 0;
                if (qty > 0) {
                    if (request.getLinkedProductDestinationLocationId() == null) {
                        throw new IllegalArgumentException(
                                "Tier " + tier.getLabel() + ": provide linkedProductDestinationLocationId for "
                                        + qty + " units of " + oldLinked.getName());
                    }
                    Location destination = locationRepository
                            .findById(request.getLinkedProductDestinationLocationId())
                            .orElseThrow(() -> new LocationNotFoundException(
                                    "Destination location not found: "
                                            + request.getLinkedProductDestinationLocationId()));

                    Map<String, Object> metadata = baseDrawMetadata(box, tier, null);
                    metadata.put("action", "patch_tier_old_product_out");
                    executeKujiTransfer(
                            box.getLocation().getId(),
                            destination,
                            oldLinked,
                            qty,
                            request.getActorId(),
                            StockMovementReason.TRANSFER,
                            metadata,
                            box.getProduct().getId()
                    );
                    affectedProductIds.add(oldLinked.getId());
                }
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
                // Per spec: do not auto-transfer-in. User can run Transfer-In More.
            }
        }

        // Other patches
        if (request.getLabel() != null && !request.getLabel().equals(tier.getLabel())) {
            tier.setLabel(request.getLabel());
            changes.add("label");
        }
        if (Boolean.TRUE.equals(request.getClearLetter())) {
            tier.setLetter(null);
            changes.add("cleared letter");
        } else if (request.getLetter() != null && !request.getLetter().equals(tier.getLetter())) {
            tier.setLetter(request.getLetter());
            changes.add("letter");
        }
        if (request.getCount() != null && !request.getCount().equals(tier.getCount())) {
            tier.setCount(request.getCount());
            changes.add("count (manual adjustment)");
        }
        if (Boolean.TRUE.equals(request.getClearPrice())) {
            tier.setPrice(null);
            changes.add("cleared price");
        } else if (request.getPrice() != null && !request.getPrice().equals(tier.getPrice())) {
            tier.setPrice(request.getPrice());
            changes.add("price");
        }

        kujiBoxTierRepository.save(tier);

        Product parentProduct = box.getProduct();
        if (!changes.isEmpty()) {
            String summary = "Edited tier '" + tier.getLabel() + "': " + String.join(", ", changes);
            AuditLog auditLog = createAuditLog(
                    request.getActorId(),
                    StockMovementReason.ADJUSTMENT,
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
                    .reason(StockMovementReason.ADJUSTMENT)
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
                            .linkedInventoryAtBoxLocation(null)
                            .count(t.getCount())
                            .price(t.getPrice())
                            .autoCreatedProduct(false)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ===================== Internal helpers =====================

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
                    draw.getQuantity()
            ));
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
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
            parts.add(formatTierLine(letter, label, linkedName, qty));
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private String stringOrNull(Map<String, Object> meta, String key) {
        if (meta == null) return null;
        Object v = meta.get(key);
        return v != null ? v.toString() : null;
    }

    private String formatTierLine(String letter, String label, String linkedProductName, int qty) {
        String head;
        if (letter != null && !letter.isBlank()) {
            head = letter;
        } else if (label != null && !label.isBlank()) {
            head = label;
        } else {
            head = "Prize";
        }
        StringBuilder sb = new StringBuilder(head);
        if (linkedProductName != null && !linkedProductName.isBlank()) {
            sb.append(" (").append(linkedProductName).append(")");
        }
        sb.append(" × ").append(qty);
        return sb.toString();
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
                            .mapToInt(t -> t.getCount() != null ? t.getCount() : 0)
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
        if (metadata == null) {
            return fallback;
        }
        Object v = metadata.get("slip_quantity");
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

        // Batch the per-tier inventory lookup into a single query.
        Map<UUID, Integer> linkedInventoryByProductId = new HashMap<>();
        if (box.getTiers() != null) {
            Set<UUID> linkedProductIds = new HashSet<>();
            for (KujiBoxTier t : box.getTiers()) {
                if (t.getLinkedProduct() != null) {
                    linkedProductIds.add(t.getLinkedProduct().getId());
                }
            }
            if (!linkedProductIds.isEmpty()) {
                List<LocationInventory> rows = locationInventoryRepository
                        .findByLocation_IdAndProduct_IdIn(location.getId(), linkedProductIds);
                for (LocationInventory li : rows) {
                    linkedInventoryByProductId.put(li.getProduct().getId(), li.getQuantity());
                }
                for (UUID pid : linkedProductIds) {
                    linkedInventoryByProductId.putIfAbsent(pid, 0);
                }
            }
        }

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
                        .linkedInventoryAtBoxLocation(
                                t.getLinkedProduct() != null
                                        ? linkedInventoryByProductId.get(t.getLinkedProduct().getId())
                                        : null)
                        .count(t.getCount())
                        .price(t.getPrice())
                        .autoCreatedProduct(Boolean.TRUE.equals(t.getAutoCreatedProduct()))
                        .build())
                .collect(Collectors.toList());

        int totalCount = tiers.stream()
                .mapToInt(t -> t.getCount() != null ? t.getCount() : 0)
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
                            .count(t.getCount())
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
                            .count(t.getCount())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
