package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.BatchDisplaySwapRequestDTO;
import com.mirai.inventoryservice.dtos.requests.RenewDisplayRequestDTO;
import com.mirai.inventoryservice.dtos.requests.SetMachineDisplayBatchRequestDTO;
import com.mirai.inventoryservice.dtos.requests.SetMachineDisplayRequestDTO;
import com.mirai.inventoryservice.dtos.requests.SwapMachineDisplayRequestDTO;
import com.mirai.inventoryservice.dtos.responses.MachineDisplayDTO;
import com.mirai.inventoryservice.models.MachineDisplay;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.MachineDisplayRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MachineDisplayService {
    private final MachineDisplayRepository machineDisplayRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;
    private final EntityManager entityManager;
    private final AuditLogService auditLogService;

    @Value("${machine-display.stale-threshold-days:14}")
    private int staleThresholdDays;

    public MachineDisplayService(
            MachineDisplayRepository machineDisplayRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            StockMovementRepository stockMovementRepository,
            EntityManager entityManager,
            AuditLogService auditLogService) {
        this.machineDisplayRepository = machineDisplayRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.entityManager = entityManager;
        this.auditLogService = auditLogService;
    }

    /**
     * Add a product to a machine's display.
     * Allows multiple products per machine (does not close existing displays).
     */
    @Transactional
    public MachineDisplay setDisplay(SetMachineDisplayRequestDTO request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.getProductId()));

        List<MachineDisplay> existingDisplays = machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(request.getLocationType(), request.getMachineId());

        boolean alreadyDisplayed = existingDisplays.stream()
                .anyMatch(d -> d.getProduct().getId().equals(request.getProductId()));

        if (alreadyDisplayed) {
            throw new IllegalArgumentException("Product is already displayed on this machine");
        }

        OffsetDateTime now = OffsetDateTime.now();
        MachineDisplay newDisplay = MachineDisplay.builder()
                .locationType(request.getLocationType())
                .machineId(request.getMachineId())
                .product(product)
                .startedAt(now)
                .actorId(request.getActorId())
                .build();

        MachineDisplay saved = machineDisplayRepository.save(newDisplay);

        String machineCode = resolveLocationCode(request.getMachineId(), request.getLocationType());
        AuditLog auditLog = auditLogService.createAuditLog(
                request.getActorId(),
                StockMovementReason.DISPLAY_SET,
                null, null,
                request.getMachineId(), machineCode,
                1, 0,
                product.getName(),
                null
        );

        // Create StockMovement entry for the display set
        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .item(product)
                .locationType(request.getLocationType())
                .fromLocationId(null)
                .toLocationId(request.getMachineId())
                .previousQuantity(0)
                .currentQuantity(0)
                .quantityChange(0)
                .reason(StockMovementReason.DISPLAY_SET)
                .actorId(request.getActorId())
                .at(now)
                .build();
        stockMovementRepository.save(movement);

        return saved;
    }

    /**
     * Add multiple products to a machine's display in a single transaction.
     * Skips products that are already displayed (does not throw error).
     * Returns the list of newly created displays.
     */
    @Transactional
    public List<MachineDisplay> setDisplayBatch(SetMachineDisplayBatchRequestDTO request) {
        if (request.getProductIds().isEmpty()) {
            return Collections.emptyList();
        }

        // Get existing active displays for this machine (1 query)
        List<MachineDisplay> existingDisplays = machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(request.getLocationType(), request.getMachineId());

        Set<UUID> existingProductIds = existingDisplays.stream()
                .map(d -> d.getProduct().getId())
                .collect(Collectors.toSet());

        // Filter out already displayed products and duplicates in request
        List<UUID> newProductIds = request.getProductIds().stream()
                .distinct()
                .filter(id -> !existingProductIds.contains(id))
                .collect(Collectors.toList());

        if (newProductIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch fetch all products (1 query instead of N)
        Map<UUID, Product> productsById = productRepository.findAllById(newProductIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Validate all products exist
        for (UUID productId : newProductIds) {
            if (!productsById.containsKey(productId)) {
                throw new IllegalArgumentException("Product not found: " + productId);
            }
        }

        // Build all display entities
        OffsetDateTime now = OffsetDateTime.now();
        List<MachineDisplay> newDisplays = newProductIds.stream()
                .map(productId -> MachineDisplay.builder()
                        .locationType(request.getLocationType())
                        .machineId(request.getMachineId())
                        .product(productsById.get(productId))
                        .startedAt(now)
                        .actorId(request.getActorId())
                        .build())
                .collect(Collectors.toList());

        List<MachineDisplay> saved = machineDisplayRepository.saveAll(newDisplays);

        String machineCode = resolveLocationCode(request.getMachineId(), request.getLocationType());
        List<String> productNames = newProductIds.stream()
                .map(id -> productsById.get(id).getName())
                .collect(Collectors.toList());
        AuditLog auditLog = auditLogService.createAuditLog(
                request.getActorId(),
                StockMovementReason.DISPLAY_SET,
                null, null,
                request.getMachineId(), machineCode,
                productNames.size(), 0,
                buildProductSummary(productNames),
                null
        );

        // Create StockMovement entries for each product added (batch insert)
        List<StockMovement> movements = newProductIds.stream()
                .map(productId -> StockMovement.builder()
                        .auditLog(auditLog)
                        .item(productsById.get(productId))
                        .locationType(request.getLocationType())
                        .fromLocationId(null)
                        .toLocationId(request.getMachineId())
                        .previousQuantity(0)
                        .currentQuantity(0)
                        .quantityChange(0)
                        .reason(StockMovementReason.DISPLAY_SET)
                        .actorId(request.getActorId())
                        .at(now)
                        .build())
                .collect(Collectors.toList());
        stockMovementRepository.saveAll(movements);

        return saved;
    }

    /**
     * Clear all displays for a machine (end all active displays)
     */
    @Transactional
    public void clearDisplay(LocationType locationType, UUID machineId, UUID actorId) {
        List<MachineDisplay> existingDisplays = machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(locationType, machineId);

        if (existingDisplays.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        existingDisplays.forEach(display -> display.setEndedAt(now));
        machineDisplayRepository.saveAll(existingDisplays);

        String machineCode = resolveLocationCode(machineId, locationType);
        List<String> productNames = existingDisplays.stream()
                .map(d -> d.getProduct().getName())
                .collect(Collectors.toList());
        AuditLog auditLog = auditLogService.createAuditLog(
                actorId,
                StockMovementReason.DISPLAY_REMOVED,
                machineId, machineCode,
                null, null,
                productNames.size(), 0,
                buildProductSummary(productNames),
                null
        );

        // Create StockMovement entries for each product removed (batch insert)
        List<StockMovement> movements = existingDisplays.stream()
                .map(display -> StockMovement.builder()
                        .auditLog(auditLog)
                        .item(display.getProduct())
                        .locationType(locationType)
                        .fromLocationId(machineId)
                        .toLocationId(null)
                        .previousQuantity(0)
                        .currentQuantity(0)
                        .quantityChange(0)
                        .reason(StockMovementReason.DISPLAY_REMOVED)
                        .actorId(actorId)
                        .at(now)
                        .build())
                .collect(Collectors.toList());
        stockMovementRepository.saveAll(movements);
    }

    /**
     * Clear a specific display by ID
     */
    @Transactional
    public void clearDisplayById(UUID displayId, UUID actorId) {
        MachineDisplay display = machineDisplayRepository.findByIdWithProduct(displayId)
                .orElseThrow(() -> new IllegalArgumentException("Display not found: " + displayId));

        if (display.getEndedAt() != null) {
            throw new IllegalArgumentException("Display is already ended");
        }

        OffsetDateTime now = OffsetDateTime.now();
        display.setEndedAt(now);
        machineDisplayRepository.save(display);

        String machineCode = resolveLocationCode(display.getMachineId(), display.getLocationType());
        AuditLog auditLog = auditLogService.createAuditLog(
                actorId,
                StockMovementReason.DISPLAY_REMOVED,
                display.getMachineId(), machineCode,
                null, null,
                1, 0,
                display.getProduct().getName(),
                null
        );

        // Create StockMovement entry for the display removed
        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .item(display.getProduct())
                .locationType(display.getLocationType())
                .fromLocationId(display.getMachineId())
                .toLocationId(null)
                .previousQuantity(0)
                .currentQuantity(0)
                .quantityChange(0)
                .reason(StockMovementReason.DISPLAY_REMOVED)
                .actorId(actorId)
                .at(now)
                .build();
        stockMovementRepository.save(movement);
    }

    /**
     * Atomically swap one displayed product for another on the same machine.
     * Ends the outgoing display record and creates a new one for the incoming product.
     */
    @Transactional
    public List<MachineDisplayDTO> swapDisplay(SwapMachineDisplayRequestDTO request) {
        MachineDisplay outgoing = machineDisplayRepository.findByIdWithProduct(request.getOutgoingDisplayId())
                .orElseThrow(() -> new IllegalArgumentException("Display not found: " + request.getOutgoingDisplayId()));

        if (outgoing.getEndedAt() != null) {
            throw new IllegalArgumentException("Display is already ended");
        }

        String outgoingProductName = outgoing.getProduct().getName();

        outgoing.setEndedAt(OffsetDateTime.now());
        machineDisplayRepository.save(outgoing);

        Product incoming = productRepository.findById(request.getIncomingProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.getIncomingProductId()));

        List<MachineDisplay> currentDisplays = machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(request.getLocationType(), request.getMachineId());

        boolean alreadyDisplayed = currentDisplays.stream()
                .anyMatch(d -> d.getProduct().getId().equals(request.getIncomingProductId()));

        if (alreadyDisplayed) {
            throw new IllegalArgumentException("Product is already displayed on this machine");
        }

        MachineDisplay newDisplay = MachineDisplay.builder()
                .locationType(request.getLocationType())
                .machineId(request.getMachineId())
                .product(incoming)
                .startedAt(OffsetDateTime.now())
                .actorId(request.getActorId())
                .build();
        machineDisplayRepository.save(newDisplay);

        String machineCode = resolveLocationCode(request.getMachineId(), request.getLocationType());
        String productSummary = outgoingProductName + " → " + incoming.getName();
        auditLogService.createAuditLog(
                request.getActorId(),
                StockMovementReason.DISPLAY_SWAP,
                request.getMachineId(), machineCode,
                request.getMachineId(), machineCode,
                1, 0,
                productSummary,
                null
        );

        return getActiveDisplaysForMachine(request.getLocationType(), request.getMachineId());
    }

    /**
     * Represents a single display change for creating StockMovement entries
     */
    private record DisplayChange(
            Product product,
            LocationType locationType,
            UUID fromMachineId,
            UUID toMachineId
    ) {}

    /**
     * Batch display swap operation that handles both swap modes in a single transaction:
     * 1. Swap with products - remove displays and add new products
     * 2. Swap with another machine - trade displays between two machines
     * Creates a single audit log entry with StockMovement entries for each change.
     */
    @Transactional
    public List<MachineDisplayDTO> batchSwapDisplay(BatchDisplaySwapRequestDTO request) {
        OffsetDateTime now = OffsetDateTime.now();
        List<DisplayChange> displayChanges = new ArrayList<>();
        List<String> allProductNames = new ArrayList<>();
        String sourceMachineCode = resolveLocationCode(request.getMachineId(), request.getLocationType());
        String targetMachineCode = null;

        // Mode 1: Swap with products (remove displays, add new products)
        if (request.getDisplayIdsToRemove() != null && !request.getDisplayIdsToRemove().isEmpty()) {
            for (UUID displayId : request.getDisplayIdsToRemove()) {
                MachineDisplay display = machineDisplayRepository.findByIdWithProduct(displayId)
                        .orElseThrow(() -> new IllegalArgumentException("Display not found: " + displayId));

                if (display.getEndedAt() != null) {
                    throw new IllegalArgumentException("Display is already ended: " + displayId);
                }

                Product product = display.getProduct();
                displayChanges.add(new DisplayChange(
                        product,
                        request.getLocationType(),
                        request.getMachineId(),  // from
                        null  // to (removed from display)
                ));
                allProductNames.add(product.getName());
                display.setEndedAt(now);
                machineDisplayRepository.save(display);
            }
        }

        if (request.getProductIdsToAdd() != null && !request.getProductIdsToAdd().isEmpty()) {
            // Get existing active displays for this machine
            List<MachineDisplay> existingDisplays = machineDisplayRepository
                    .findActiveByLocationTypeAndMachineId(request.getLocationType(), request.getMachineId());
            Set<UUID> existingProductIds = existingDisplays.stream()
                    .map(d -> d.getProduct().getId())
                    .collect(Collectors.toSet());

            // Filter out already displayed products
            List<UUID> newProductIds = request.getProductIdsToAdd().stream()
                    .distinct()
                    .filter(id -> !existingProductIds.contains(id))
                    .collect(Collectors.toList());

            if (!newProductIds.isEmpty()) {
                Map<UUID, Product> productsById = productRepository.findAllById(newProductIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

                for (UUID productId : newProductIds) {
                    Product product = productsById.get(productId);
                    if (product == null) {
                        throw new IllegalArgumentException("Product not found: " + productId);
                    }

                    MachineDisplay newDisplay = MachineDisplay.builder()
                            .locationType(request.getLocationType())
                            .machineId(request.getMachineId())
                            .product(product)
                            .startedAt(now)
                            .actorId(request.getActorId())
                            .build();
                    machineDisplayRepository.save(newDisplay);
                    displayChanges.add(new DisplayChange(
                            product,
                            request.getLocationType(),
                            null,  // from (added to display)
                            request.getMachineId()  // to
                    ));
                    allProductNames.add(product.getName());
                }
            }
        }

        // Mode 2: Machine-to-machine swap
        if (request.getTargetMachineId() != null && request.getTargetLocationType() != null) {
            targetMachineCode = resolveLocationCode(request.getTargetMachineId(), request.getTargetLocationType());

            // Move displays FROM target machine TO current machine
            if (request.getDisplayIdsFromTarget() != null && !request.getDisplayIdsFromTarget().isEmpty()) {
                // Get existing active displays for current machine
                List<MachineDisplay> existingDisplays = machineDisplayRepository
                        .findActiveByLocationTypeAndMachineId(request.getLocationType(), request.getMachineId());
                Set<UUID> existingProductIds = existingDisplays.stream()
                        .map(d -> d.getProduct().getId())
                        .collect(Collectors.toSet());

                for (UUID displayId : request.getDisplayIdsFromTarget()) {
                    MachineDisplay display = machineDisplayRepository.findByIdWithProduct(displayId)
                            .orElseThrow(() -> new IllegalArgumentException("Display not found: " + displayId));

                    if (display.getEndedAt() != null) {
                        throw new IllegalArgumentException("Display is already ended: " + displayId);
                    }

                    // Skip if product is already displayed on current machine
                    if (existingProductIds.contains(display.getProduct().getId())) {
                        continue;
                    }

                    Product product = display.getProduct();

                    // End the display on target machine
                    display.setEndedAt(now);
                    machineDisplayRepository.save(display);

                    // Create new display on current machine
                    MachineDisplay newDisplay = MachineDisplay.builder()
                            .locationType(request.getLocationType())
                            .machineId(request.getMachineId())
                            .product(product)
                            .startedAt(now)
                            .actorId(request.getActorId())
                            .build();
                    machineDisplayRepository.save(newDisplay);

                    displayChanges.add(new DisplayChange(
                            product,
                            request.getLocationType(),
                            request.getTargetMachineId(),  // from target
                            request.getMachineId()  // to source
                    ));
                    allProductNames.add(product.getName());
                    existingProductIds.add(product.getId());
                }
            }

            // Move displays FROM current machine TO target machine
            if (request.getDisplayIdsToTarget() != null && !request.getDisplayIdsToTarget().isEmpty()) {
                // Get existing active displays for target machine
                List<MachineDisplay> targetDisplays = machineDisplayRepository
                        .findActiveByLocationTypeAndMachineId(request.getTargetLocationType(), request.getTargetMachineId());
                Set<UUID> targetProductIds = targetDisplays.stream()
                        .map(d -> d.getProduct().getId())
                        .collect(Collectors.toSet());

                for (UUID displayId : request.getDisplayIdsToTarget()) {
                    MachineDisplay display = machineDisplayRepository.findByIdWithProduct(displayId)
                            .orElseThrow(() -> new IllegalArgumentException("Display not found: " + displayId));

                    if (display.getEndedAt() != null) {
                        throw new IllegalArgumentException("Display is already ended: " + displayId);
                    }

                    // Skip if product is already displayed on target machine
                    if (targetProductIds.contains(display.getProduct().getId())) {
                        continue;
                    }

                    Product product = display.getProduct();

                    // End the display on current machine
                    display.setEndedAt(now);
                    machineDisplayRepository.save(display);

                    // Create new display on target machine
                    MachineDisplay newDisplay = MachineDisplay.builder()
                            .locationType(request.getTargetLocationType())
                            .machineId(request.getTargetMachineId())
                            .product(product)
                            .startedAt(now)
                            .actorId(request.getActorId())
                            .build();
                    machineDisplayRepository.save(newDisplay);

                    displayChanges.add(new DisplayChange(
                            product,
                            request.getLocationType(),
                            request.getMachineId(),  // from source
                            request.getTargetMachineId()  // to target
                    ));
                    allProductNames.add(product.getName());
                    targetProductIds.add(product.getId());
                }
            }
        }

        // Create audit log and stock movements for the swap operation
        if (!displayChanges.isEmpty()) {
            String productSummary = buildProductSummary(allProductNames.stream().distinct().collect(Collectors.toList()));

            // Create the audit log entry
            AuditLog auditLog = auditLogService.createAuditLog(
                    request.getActorId(),
                    StockMovementReason.DISPLAY_SWAP,
                    request.getMachineId(), sourceMachineCode,
                    request.getTargetMachineId() != null ? request.getTargetMachineId() : request.getMachineId(),
                    request.getTargetMachineId() != null ? targetMachineCode : sourceMachineCode,
                    displayChanges.size(), 0,
                    productSummary,
                    null
            );

            // Create StockMovement entries for each display change (batch insert)
            List<StockMovement> movements = displayChanges.stream()
                    .map(change -> StockMovement.builder()
                            .auditLog(auditLog)
                            .item(change.product())
                            .locationType(change.locationType())
                            .fromLocationId(change.fromMachineId())
                            .toLocationId(change.toMachineId())
                            .previousQuantity(0)
                            .currentQuantity(0)
                            .quantityChange(0)  // Display changes don't affect quantity
                            .reason(StockMovementReason.DISPLAY_SWAP)
                            .actorId(request.getActorId())
                            .at(now)
                            .build())
                    .collect(Collectors.toList());
            stockMovementRepository.saveAll(movements);
        }

        return getActiveDisplaysForMachine(request.getLocationType(), request.getMachineId());
    }

    /**
     * Renew display records - ends current displays and creates new ones with fresh startedAt.
     * Used when restocking the same product to reset tracking.
     */
    @Transactional
    public List<MachineDisplayDTO> renewDisplays(RenewDisplayRequestDTO request) {
        if (request.getDisplayIds() == null || request.getDisplayIds().isEmpty()) {
            return getActiveDisplaysForMachine(request.getLocationType(), request.getMachineId());
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<MachineDisplay> renewedDisplays = new ArrayList<>();
        List<String> productNames = new ArrayList<>();

        for (UUID displayId : request.getDisplayIds()) {
            MachineDisplay existing = machineDisplayRepository.findByIdWithProduct(displayId)
                    .orElseThrow(() -> new IllegalArgumentException("Display not found: " + displayId));

            if (existing.getEndedAt() != null) {
                throw new IllegalArgumentException("Display is already ended: " + displayId);
            }

            Product product = existing.getProduct();
            productNames.add(product.getName());

            // End the existing display
            existing.setEndedAt(now);
            machineDisplayRepository.save(existing);

            // Create a new display with fresh startedAt
            MachineDisplay newDisplay = MachineDisplay.builder()
                    .locationType(request.getLocationType())
                    .machineId(request.getMachineId())
                    .product(product)
                    .startedAt(now)
                    .actorId(request.getActorId())
                    .build();
            renewedDisplays.add(machineDisplayRepository.save(newDisplay));
        }

        // Create audit log
        String machineCode = resolveLocationCode(request.getMachineId(), request.getLocationType());
        AuditLog auditLog = auditLogService.createAuditLog(
                request.getActorId(),
                StockMovementReason.DISPLAY_SWAP, // Reuse DISPLAY_SWAP for renewal
                request.getMachineId(), machineCode,
                request.getMachineId(), machineCode,
                productNames.size(), 0,
                "Renewed: " + buildProductSummary(productNames),
                null
        );

        // Create StockMovement entries for each renewed display
        List<StockMovement> movements = renewedDisplays.stream()
                .map(display -> StockMovement.builder()
                        .auditLog(auditLog)
                        .item(display.getProduct())
                        .locationType(request.getLocationType())
                        .fromLocationId(request.getMachineId())
                        .toLocationId(request.getMachineId())
                        .previousQuantity(0)
                        .currentQuantity(0)
                        .quantityChange(0)
                        .reason(StockMovementReason.DISPLAY_SWAP)
                        .actorId(request.getActorId())
                        .at(now)
                        .build())
                .collect(Collectors.toList());
        stockMovementRepository.saveAll(movements);

        return getActiveDisplaysForMachine(request.getLocationType(), request.getMachineId());
    }

    /**
     * Get all active displays for a specific machine
     */
    public List<MachineDisplayDTO> getActiveDisplaysForMachine(LocationType locationType, UUID machineId) {
        List<MachineDisplay> displays = machineDisplayRepository
                .findActiveByLocationTypeAndMachineId(locationType, machineId);
        return toDTOList(displays);
    }

    /**
     * Get current display for a specific machine
     */
    public Optional<MachineDisplayDTO> getCurrentDisplay(LocationType locationType, UUID machineId) {
        return machineDisplayRepository
                .findByLocationTypeAndMachineIdAndEndedAtIsNull(locationType, machineId)
                .map(display -> toDTO(display));
    }

    /**
     * Get all current active displays
     */
    public List<MachineDisplayDTO> getAllActiveDisplays() {
        List<MachineDisplay> displays = machineDisplayRepository.findByEndedAtIsNullOrderByStartedAtAsc();
        return toDTOList(displays);
    }

    /**
     * Get all current active displays with pagination
     */
    public Page<MachineDisplayDTO> getAllActiveDisplays(Pageable pageable) {
        Page<MachineDisplay> displays = machineDisplayRepository.findByEndedAtIsNull(pageable);
        return toDTOPage(displays);
    }

    /**
     * Get active displays for a specific location type
     */
    public List<MachineDisplayDTO> getActiveDisplaysByLocationType(LocationType locationType) {
        List<MachineDisplay> displays = machineDisplayRepository
                .findByLocationTypeAndEndedAtIsNullOrderByStartedAtAsc(locationType);
        return toDTOList(displays);
    }

    /**
     * Get display history for a specific machine
     */
    public List<MachineDisplayDTO> getMachineHistory(LocationType locationType, UUID machineId) {
        List<MachineDisplay> displays = machineDisplayRepository
                .findByLocationTypeAndMachineIdOrderByStartedAtDesc(locationType, machineId);
        return toDTOList(displays);
    }

    /**
     * Get display history for a specific machine with pagination
     */
    public Page<MachineDisplayDTO> getMachineHistory(LocationType locationType, UUID machineId, Pageable pageable) {
        Page<MachineDisplay> displays = machineDisplayRepository
                .findByLocationTypeAndMachineIdOrderByStartedAtDesc(locationType, machineId, pageable);
        return toDTOPage(displays);
    }

    /**
     * Get all stale displays (active longer than threshold)
     */
    public List<MachineDisplayDTO> getStaleDisplays() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(staleThresholdDays);
        List<MachineDisplay> displays = machineDisplayRepository.findStaleDisplays(threshold);
        return toDTOList(displays);
    }

    /**
     * Get stale displays for a specific location type
     */
    public List<MachineDisplayDTO> getStaleDisplaysByLocationType(LocationType locationType) {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(staleThresholdDays);
        List<MachineDisplay> displays = machineDisplayRepository
                .findStaleDisplaysByLocationType(locationType, threshold);
        return toDTOList(displays);
    }

    /**
     * Get stale displays with custom threshold
     */
    public List<MachineDisplayDTO> getStaleDisplays(int thresholdDays) {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(thresholdDays);
        List<MachineDisplay> displays = machineDisplayRepository.findStaleDisplays(threshold);
        return toDTOList(displays);
    }

    /**
     * Get display history for a product
     */
    public List<MachineDisplayDTO> getProductHistory(UUID productId) {
        List<MachineDisplay> displays = machineDisplayRepository.findByProduct_IdOrderByStartedAtDesc(productId);
        return toDTOList(displays);
    }

    /**
     * Delete a display history record (permanently removes from database)
     */
    @Transactional
    public void deleteDisplayHistory(UUID displayId) {
        MachineDisplay display = machineDisplayRepository.findById(displayId)
                .orElseThrow(() -> new IllegalArgumentException("Display history not found: " + displayId));

        machineDisplayRepository.delete(display);
    }

    // ========= Helper Methods =========

    private MachineDisplayDTO toDTO(MachineDisplay display) {
        String machineCode = resolveLocationCode(display.getMachineId(), display.getLocationType());
        String actorName = resolveActorName(display.getActorId());

        return MachineDisplayDTO.fromEntity(display, machineCode, actorName, staleThresholdDays);
    }

    private List<MachineDisplayDTO> toDTOList(List<MachineDisplay> displays) {
        if (displays.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch fetch actor names to avoid N+1
        Set<UUID> actorIds = displays.stream()
                .map(MachineDisplay::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> actorNames = batchResolveActorNames(actorIds);

        // Batch fetch machine codes
        Map<String, String> machineCodes = batchResolveMachineCodes(displays);

        return displays.stream()
                .map(display -> {
                    String key = display.getLocationType() + ":" + display.getMachineId();
                    String machineCode = machineCodes.get(key);
                    String actorName = display.getActorId() != null ? actorNames.get(display.getActorId()) : null;

                    return MachineDisplayDTO.fromEntity(display, machineCode, actorName, staleThresholdDays);
                })
                .collect(Collectors.toList());
    }

    private Page<MachineDisplayDTO> toDTOPage(Page<MachineDisplay> displays) {
        if (displays.isEmpty()) {
            return displays.map(d -> null); // Returns empty page with same metadata
        }

        List<MachineDisplay> content = displays.getContent();

        // Batch fetch actor names to avoid N+1
        Set<UUID> actorIds = content.stream()
                .map(MachineDisplay::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> actorNames = batchResolveActorNames(actorIds);

        // Batch fetch machine codes
        Map<String, String> machineCodes = batchResolveMachineCodes(content);

        // Map content with pre-fetched data
        List<MachineDisplayDTO> dtoContent = content.stream()
                .map(display -> {
                    String key = display.getLocationType() + ":" + display.getMachineId();
                    String machineCode = machineCodes.get(key);
                    String actorName = display.getActorId() != null ? actorNames.get(display.getActorId()) : null;

                    return MachineDisplayDTO.fromEntity(display, machineCode, actorName, staleThresholdDays);
                })
                .collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(
                dtoContent,
                displays.getPageable(),
                displays.getTotalElements()
        );
    }

    private String resolveLocationCode(UUID locationId, LocationType locationType) {
        if (locationId == null) return null;

        Object result = entityManager.createNativeQuery("SELECT resolve_location_code(:locationId, :locationType)")
                .setParameter("locationId", locationId)
                .setParameter("locationType", locationType.name())
                .getSingleResult();

        return result != null ? result.toString() : null;
    }

    private String resolveActorName(UUID actorId) {
        if (actorId == null) return null;

        return userRepository.findById(actorId)
                .map(user -> user.getFullName())
                .orElse(null);
    }

    private Map<UUID, String> batchResolveActorNames(Set<UUID> actorIds) {
        if (actorIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(
                        user -> user.getId(),
                        user -> user.getFullName()
                ));
    }

    private Map<String, String> batchResolveMachineCodes(List<MachineDisplay> displays) {
        Map<String, String> result = new HashMap<>();

        // Group by location type
        Map<LocationType, Set<UUID>> machineIdsByType = displays.stream()
                .collect(Collectors.groupingBy(
                        MachineDisplay::getLocationType,
                        Collectors.mapping(MachineDisplay::getMachineId, Collectors.toSet())
                ));

        // Batch fetch codes per location type (1 query per type instead of N queries per machine)
        for (Map.Entry<LocationType, Set<UUID>> entry : machineIdsByType.entrySet()) {
            LocationType locationType = entry.getKey();
            Set<UUID> machineIds = entry.getValue();

            if (machineIds.isEmpty()) continue;

            String tableName = getTableNameForLocationType(locationType);
            String codeColumn = getCodeColumnForLocationType(locationType);

            if (tableName == null || codeColumn == null) {
                // Handle NOT_ASSIGNED or unknown types
                for (UUID machineId : machineIds) {
                    result.put(locationType + ":" + machineId, "NA");
                }
                continue;
            }

            // Single query to fetch all codes for this location type
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT id, " + codeColumn + " FROM " + tableName + " WHERE id IN (:ids)")
                    .setParameter("ids", machineIds)
                    .getResultList();

            for (Object[] row : rows) {
                UUID id = (UUID) row[0];
                String code = (String) row[1];
                result.put(locationType + ":" + id, code);
            }
        }

        return result;
    }

    private String getTableNameForLocationType(LocationType locationType) {
        return switch (locationType) {
            case BOX_BIN -> "box_bins";
            case SINGLE_CLAW_MACHINE -> "single_claw_machines";
            case DOUBLE_CLAW_MACHINE -> "double_claw_machines";
            case KEYCHAIN_MACHINE -> "keychain_machines";
            case CABINET -> "cabinets";
            case RACK -> "racks";
            case FOUR_CORNER_MACHINE -> "four_corner_machines";
            case GACHAPON -> "gachapons";
            case PUSHER_MACHINE -> "pusher_machines";
            case WINDOW, NOT_ASSIGNED -> null;
        };
    }

    private String buildProductSummary(List<String> productNames) {
        if (productNames.isEmpty()) return null;
        if (productNames.size() == 1) return productNames.get(0);
        if (productNames.size() == 2) return productNames.get(0) + " + " + productNames.get(1);
        return productNames.get(0) + " + " + (productNames.size() - 1) + " more";
    }

    private String getCodeColumnForLocationType(LocationType locationType) {
        return switch (locationType) {
            case BOX_BIN -> "box_bin_code";
            case SINGLE_CLAW_MACHINE -> "single_claw_machine_code";
            case DOUBLE_CLAW_MACHINE -> "double_claw_machine_code";
            case KEYCHAIN_MACHINE -> "keychain_machine_code";
            case CABINET -> "cabinet_code";
            case RACK -> "rack_code";
            case FOUR_CORNER_MACHINE -> "four_corner_machine_code";
            case GACHAPON -> "gachapon_code";
            case PUSHER_MACHINE -> "pusher_machine_code";
            case WINDOW, NOT_ASSIGNED -> null;
        };
    }
}
