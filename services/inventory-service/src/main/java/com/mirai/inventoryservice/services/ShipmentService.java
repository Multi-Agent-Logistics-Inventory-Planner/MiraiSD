package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.ReceiveShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentItemRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentRequestDTO;
import com.mirai.inventoryservice.exceptions.InsufficientInventoryException;
import com.mirai.inventoryservice.exceptions.InvalidShipmentStatusException;
import com.mirai.inventoryservice.exceptions.LocationNotFoundException;
import com.mirai.inventoryservice.exceptions.ProductNotFoundException;
import com.mirai.inventoryservice.exceptions.ShipmentItemNotFoundException;
import com.mirai.inventoryservice.exceptions.ShipmentNotFoundException;
import com.mirai.inventoryservice.exceptions.SiteNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.Site;
import com.mirai.inventoryservice.models.Supplier;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.NotificationSeverity;
import com.mirai.inventoryservice.models.enums.NotificationType;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.models.shipment.ShipmentItem;
import com.mirai.inventoryservice.models.shipment.ShipmentItemAllocation;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.repositories.*;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ShipmentService {
    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;
    private final LocationInventoryRepository locationInventoryRepository;
    private final LocationRepository locationRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final SiteRepository siteRepository;
    private final NotificationService notificationService;
    private final StockMovementService stockMovementService;
    private final AuditLogService auditLogService;
    private final SupabaseBroadcastService broadcastService;
    private final EventOutboxService eventOutboxService;
    private final SupplierService supplierService;

    private static final String DEFAULT_SITE_CODE = "MAIN";

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            ProductRepository productRepository,
            ProductService productService,
            UserService userService,
            UserRepository userRepository,
            StockMovementRepository stockMovementRepository,
            LocationInventoryRepository locationInventoryRepository,
            LocationRepository locationRepository,
            StorageLocationRepository storageLocationRepository,
            SiteRepository siteRepository,
            NotificationService notificationService,
            StockMovementService stockMovementService,
            AuditLogService auditLogService,
            SupabaseBroadcastService broadcastService,
            EventOutboxService eventOutboxService,
            SupplierService supplierService) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.locationInventoryRepository = locationInventoryRepository;
        this.locationRepository = locationRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.siteRepository = siteRepository;
        this.notificationService = notificationService;
        this.stockMovementService = stockMovementService;
        this.auditLogService = auditLogService;
        this.broadcastService = broadcastService;
        this.eventOutboxService = eventOutboxService;
        this.supplierService = supplierService;
    }

    public Shipment createShipment(ShipmentRequestDTO requestDTO) {
        // Resolve supplier from supplier name (creates if not exists)
        Supplier supplier = null;
        String supplierName = requestDTO.getSupplierName();
        if (supplierName != null && !supplierName.isBlank()) {
            supplier = supplierService.resolveOrCreate(supplierName);
        }

        Shipment shipment = Shipment.builder()
                .shipmentNumber(requestDTO.getShipmentNumber())
                .supplierName(supplier != null ? supplier.getDisplayName() : supplierName)
                .supplier(supplier)
                .status(requestDTO.getStatus() != null ? requestDTO.getStatus() : ShipmentStatus.PENDING)
                .orderDate(requestDTO.getOrderDate())
                .expectedDeliveryDate(requestDTO.getExpectedDeliveryDate())
                .actualDeliveryDate(requestDTO.getActualDeliveryDate())
                .totalCost(requestDTO.getTotalCost())
                .notes(requestDTO.getNotes())
                .trackingId(requestDTO.getTrackingId())
                .build();

        if (requestDTO.getCreatedBy() != null) {
            User user = userService.getUserById(requestDTO.getCreatedBy());
            shipment.setCreatedBy(user);
        }

        // Issue 9 fix: Batch fetch all products upfront
        Set<UUID> productIds = requestDTO.getItems().stream()
                .map(ShipmentItemRequestDTO::getItemId)
                .collect(Collectors.toSet());
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<ShipmentItem> items = new ArrayList<>();
        for (ShipmentItemRequestDTO itemDTO : requestDTO.getItems()) {
            Product product = productMap.get(itemDTO.getItemId());
            if (product == null) {
                throw new ProductNotFoundException("Product not found with id: " + itemDTO.getItemId());
            }

            ShipmentItem item = ShipmentItem.builder()
                    .shipment(shipment)
                    .item(product)
                    .orderedQuantity(itemDTO.getOrderedQuantity())
                    .receivedQuantity(0)
                    .unitCost(itemDTO.getUnitCost())
                    .destinationLocationType(itemDTO.getDestinationLocationType())
                    .destinationLocationId(itemDTO.getDestinationLocationId())
                    .notes(itemDTO.getNotes())
                    .build();

            items.add(item);
        }

        shipment.setItems(items);
        Shipment savedShipment = shipmentRepository.save(shipment);

        // Broadcast real-time update to connected clients
        broadcastService.broadcastShipmentUpdated();

        return savedShipment;
    }

    public Shipment getShipmentById(UUID id) {
        return shipmentRepository.findByIdWithAssociations(id)
                .orElseThrow(() -> new ShipmentNotFoundException("Shipment not found with id: " + id));
    }

    public List<Shipment> listShipments() {
        return shipmentRepository.findAllWithAssociationsList();
    }

    public List<Shipment> listShipmentsByStatus(ShipmentStatus status) {
        return shipmentRepository.findByStatusWithAssociationsList(status);
    }

    public Page<Shipment> listShipmentsPaged(ShipmentStatus status, String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            String searchPattern = "%" + search.toLowerCase() + "%";
            return shipmentRepository.findByStatusAndSearch(status, searchPattern, pageable);
        }
        return shipmentRepository.findByStatusPaged(status, pageable);
    }

    /**
     * List shipments by display status with pagination.
     * Display statuses (frontend-derived from inventory + carrier status):
     *   ACTIVE          - PENDING with no receipts and carrier_status != DELIVERED/FAILED
     *   AWAITING_RECEIPT - PENDING with no receipts and carrier_status = DELIVERED
     *   PARTIAL         - PENDING with some items received
     *   COMPLETED       - RECEIVED
     *   FAILED          - PENDING with carrier_status = FAILED
     */
    public Page<Shipment> listShipmentsByDisplayStatus(String displayStatus, String search, Pageable pageable) {
        String searchPattern = (search != null && !search.isBlank()) ? "%" + search.toLowerCase() + "%" : null;

        return switch (displayStatus) {
            case "ACTIVE" -> searchPattern != null
                    ? shipmentRepository.findActiveShipmentsWithSearch(searchPattern, pageable)
                    : shipmentRepository.findActiveShipments(pageable);
            case "AWAITING_RECEIPT" -> searchPattern != null
                    ? shipmentRepository.findAwaitingReceiptShipmentsWithSearch(searchPattern, pageable)
                    : shipmentRepository.findAwaitingReceiptShipments(pageable);
            case "PARTIAL" -> searchPattern != null
                    ? shipmentRepository.findPartialShipmentsWithSearch(searchPattern, pageable)
                    : shipmentRepository.findPartialShipments(pageable);
            case "COMPLETED" -> searchPattern != null
                    ? shipmentRepository.findCompletedShipmentsWithSearch(ShipmentStatus.RECEIVED, searchPattern, pageable)
                    : shipmentRepository.findCompletedShipments(ShipmentStatus.RECEIVED, pageable);
            case "FAILED" -> searchPattern != null
                    ? shipmentRepository.findFailedShipmentsWithSearch(searchPattern, pageable)
                    : shipmentRepository.findFailedShipments(pageable);
            default -> listShipmentsPaged(null, search, pageable);
        };
    }

    /**
     * Get counts for each display status (for tab indicators).
     */
    public Map<String, Long> getDisplayStatusCounts() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("ACTIVE", shipmentRepository.countActiveShipments());
        counts.put("AWAITING_RECEIPT", shipmentRepository.countAwaitingReceiptShipments());
        counts.put("PARTIAL", shipmentRepository.countPartialShipments());
        counts.put("COMPLETED", shipmentRepository.countCompletedShipments(ShipmentStatus.RECEIVED));
        counts.put("FAILED", shipmentRepository.countFailedShipments());
        counts.put("OVERDUE", shipmentRepository.countOverdueShipments());
        return counts;
    }

    public List<Shipment> getShipmentsContainingProduct(UUID productId) {
        return shipmentRepository.findByItemsContainingProduct(productId);
    }

    /**
     * Update shipment without audit logging (for backward compatibility)
     */
    public Shipment updateShipment(UUID id, ShipmentRequestDTO requestDTO) {
        return updateShipment(id, requestDTO, null, null);
    }

    /**
     * Update shipment with audit logging
     */
    public Shipment updateShipment(UUID id, ShipmentRequestDTO requestDTO, UUID actorId, String actorName) {
        Shipment shipment = getShipmentById(id);

        // Capture before state for audit
        ShipmentSnapshot before = captureShipmentSnapshot(shipment);

        if (requestDTO.getShipmentNumber() != null) {
            shipment.setShipmentNumber(requestDTO.getShipmentNumber());
        }
        if (requestDTO.getSupplierName() != null) {
            // Resolve supplier from supplier name (creates if not exists)
            if (!requestDTO.getSupplierName().isBlank()) {
                Supplier supplier = supplierService.resolveOrCreate(requestDTO.getSupplierName());
                shipment.setSupplier(supplier);
                shipment.setSupplierName(supplier != null ? supplier.getDisplayName() : requestDTO.getSupplierName());
            } else {
                shipment.setSupplierName(requestDTO.getSupplierName());
            }
        }
        if (requestDTO.getStatus() != null) {
            shipment.setStatus(requestDTO.getStatus());
        }
        if (requestDTO.getOrderDate() != null) {
            shipment.setOrderDate(requestDTO.getOrderDate());
        }
        if (requestDTO.getExpectedDeliveryDate() != null) {
            shipment.setExpectedDeliveryDate(requestDTO.getExpectedDeliveryDate());
        }
        if (requestDTO.getActualDeliveryDate() != null) {
            shipment.setActualDeliveryDate(requestDTO.getActualDeliveryDate());
        }
        if (requestDTO.getTotalCost() != null) {
            shipment.setTotalCost(requestDTO.getTotalCost());
        }
        if (requestDTO.getNotes() != null) {
            shipment.setNotes(requestDTO.getNotes());
        }
        // Always update trackingId (allows clearing by setting to null)
        shipment.setTrackingId(requestDTO.getTrackingId());

        // Handle items update
        if (requestDTO.getItems() != null && !requestDTO.getItems().isEmpty()) {
            // Batch fetch all products upfront
            Set<UUID> productIds = requestDTO.getItems().stream()
                    .map(ShipmentItemRequestDTO::getItemId)
                    .collect(Collectors.toSet());
            Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));

            // Separate existing items into received (must preserve) and unreceived (can replace)
            Map<UUID, ShipmentItem> receivedItemsByProductId = new HashMap<>();
            List<ShipmentItem> unreceivedItems = new ArrayList<>();

            for (ShipmentItem existingItem : shipment.getItems()) {
                boolean hasBeenReceived = existingItem.getReceivedQuantity() > 0
                        || existingItem.getDamagedQuantity() > 0
                        || existingItem.getDisplayQuantity() > 0
                        || existingItem.getShopQuantity() > 0;

                if (hasBeenReceived) {
                    receivedItemsByProductId.put(existingItem.getItem().getId(), existingItem);
                } else {
                    unreceivedItems.add(existingItem);
                }
            }

            // Remove only unreceived items (orphanRemoval will delete them)
            shipment.getItems().removeAll(unreceivedItems);

            // Track which products are already in the shipment (received items)
            Set<UUID> existingProductIds = receivedItemsByProductId.keySet();

            // Add new items from the request
            for (ShipmentItemRequestDTO itemDTO : requestDTO.getItems()) {
                UUID productId = itemDTO.getItemId();

                // Item has receipts - block ordered-quantity changes; corrections must go through Undo + Receive
                if (existingProductIds.contains(productId)) {
                    ShipmentItem receivedItem = receivedItemsByProductId.get(productId);
                    if (itemDTO.getOrderedQuantity() != null
                            && !itemDTO.getOrderedQuantity().equals(receivedItem.getOrderedQuantity())) {
                        throw new InvalidShipmentStatusException(
                                "Cannot change ordered quantity for an item with receipts. Undo this item first.");
                    }
                    continue;
                }

                Product product = productMap.get(productId);
                if (product == null) {
                    throw new ProductNotFoundException("Product not found with id: " + productId);
                }

                ShipmentItem item = ShipmentItem.builder()
                        .shipment(shipment)
                        .item(product)
                        .orderedQuantity(itemDTO.getOrderedQuantity())
                        .receivedQuantity(0)
                        .unitCost(itemDTO.getUnitCost())
                        .destinationLocationType(itemDTO.getDestinationLocationType())
                        .destinationLocationId(itemDTO.getDestinationLocationId())
                        .notes(itemDTO.getNotes())
                        .build();

                shipment.getItems().add(item);
            }
        }

        Shipment savedShipment = shipmentRepository.save(shipment);

        // Capture after state and create audit for any changes
        ShipmentSnapshot after = captureShipmentSnapshot(savedShipment);
        createShipmentEditAudit(savedShipment, before, after, actorId, actorName);

        // Broadcast real-time update to connected clients
        broadcastService.broadcastShipmentUpdated();

        return savedShipment;
    }

    /**
     * Delete shipment without audit logging (for backward compatibility)
     */
    public void deleteShipment(UUID id) {
        deleteShipment(id, null, null);
    }

    /**
     * Delete shipment with audit logging
     */
    public void deleteShipment(UUID id, UUID actorId, String actorName) {
        Shipment shipment = getShipmentById(id);
        if (shipment.getStatus() == ShipmentStatus.RECEIVED) {
            throw new InvalidShipmentStatusException("Cannot delete a received shipment");
        }

        // Create audit before deleting (captures full shipment details)
        createShipmentDeletionAudit(shipment, actorId, actorName);

        shipmentRepository.delete(shipment);

        // Broadcast real-time update to connected clients
        broadcastService.broadcastShipmentUpdated();
    }

    /**
     * Receive a shipment: update inventory, create stock movements, publish events
     * Supports partial receipts - shipment remains PENDING until all items are fully received
     */
    public Shipment receiveShipment(UUID shipmentId, ReceiveShipmentRequestDTO requestDTO) {
        Shipment shipment = getShipmentById(shipmentId);

        if (shipment.getStatus() == ShipmentStatus.CANCELLED) {
            throw new InvalidShipmentStatusException("Cannot receive a cancelled shipment");
        }

        // Set actual delivery date on first receipt, or update if this is a later receipt
        if (shipment.getActualDeliveryDate() == null) {
            shipment.setActualDeliveryDate(requestDTO.getActualDeliveryDate());
        }

        // Resolve receiving user once up front (used for both shipment.receivedBy and the audit log actor)
        User receivedByUser = null;
        UUID validatedActorId = null;
        if (requestDTO.getReceivedBy() != null) {
            receivedByUser = userRepository.findById(requestDTO.getReceivedBy()).orElse(null);
            if (receivedByUser != null) {
                validatedActorId = receivedByUser.getId();
            }
        }

        // Issue 8 fix: Batch fetch all shipment items upfront
        Set<UUID> shipmentItemIds = requestDTO.getItemReceipts().stream()
                .map(ReceiveShipmentRequestDTO.ItemReceiptDTO::getShipmentItemId)
                .collect(Collectors.toSet());
        Map<UUID, ShipmentItem> shipmentItemMap = shipmentItemRepository.findAllById(shipmentItemIds).stream()
                .collect(Collectors.toMap(ShipmentItem::getId, Function.identity()));

        Set<UUID> affectedProductIds = new java.util.HashSet<>();

        // Track what was received in this batch for audit
        List<String> receivedItemSummaries = new ArrayList<>();
        int totalReceivedInBatch = 0;

        // Create the parent audit log up front; its reason / counts are finalized after the loop.
        // All per-item stock movements get linked to this single audit log, mirroring the transfer pattern.
        AuditLog receiveAuditLog = auditLogService.createAuditLog(
                validatedActorId,
                receivedByUser != null ? receivedByUser.getFullName() : null,
                StockMovementReason.SHIPMENT_PARTIAL_RECEIPT,
                null, null, null, null,
                0,
                0,
                "Shipment " + shipment.getShipmentNumber(),
                null
        );
        receiveAuditLog.setShipmentId(shipment.getId());
        receiveAuditLog.setShipmentNumber(shipment.getShipmentNumber());

        for (ReceiveShipmentRequestDTO.ItemReceiptDTO receipt : requestDTO.getItemReceipts()) {
            ShipmentItem shipmentItem = shipmentItemMap.get(receipt.getShipmentItemId());
            if (shipmentItem == null) {
                throw new IllegalArgumentException("Shipment item not found: " + receipt.getShipmentItemId());
            }

            affectedProductIds.add(shipmentItem.getItem().getId());

            if (!shipmentItem.getShipment().getId().equals(shipmentId)) {
                throw new IllegalArgumentException("Shipment item does not belong to this shipment");
            }

            // Build allocations list - support both new multi-destination and legacy single-destination formats
            List<ReceiveShipmentRequestDTO.DestinationAllocationDTO> allocations;

            if (receipt.getAllocations() != null && !receipt.getAllocations().isEmpty()) {
                // New format: use allocations directly
                allocations = receipt.getAllocations();
            } else {
                // Legacy format: create single allocation from old fields
                LocationType legacyLocationType = receipt.getDestinationLocationType() != null
                        ? receipt.getDestinationLocationType()
                        : shipmentItem.getDestinationLocationType();
                UUID legacyLocationId = receipt.getDestinationLocationId() != null
                        ? receipt.getDestinationLocationId()
                        : shipmentItem.getDestinationLocationId();

                // Default to NOT_ASSIGNED if no location specified
                if (legacyLocationType == null) {
                    legacyLocationType = LocationType.NOT_ASSIGNED;
                    legacyLocationId = null;
                }

                allocations = List.of(ReceiveShipmentRequestDTO.DestinationAllocationDTO.builder()
                        .locationType(legacyLocationType)
                        .locationId(legacyLocationId)
                        .quantity(receipt.getReceivedQuantity())
                        .build());
            }

            // Calculate total quantity from allocations (good items to add to inventory)
            int quantityToReceive = allocations.stream()
                    .mapToInt(a -> a.getQuantity() != null ? a.getQuantity() : 0)
                    .sum();

            // Get quantities that won't be added to inventory
            int damagedQuantity = receipt.getDamagedQuantity() != null ? receipt.getDamagedQuantity() : 0;
            int displayQuantity = receipt.getDisplayQuantity() != null ? receipt.getDisplayQuantity() : 0;
            int shopQuantity = receipt.getShopQuantity() != null ? receipt.getShopQuantity() : 0;

            int currentReceivedQuantity = shipmentItem.getReceivedQuantity();
            int currentDamagedQuantity = shipmentItem.getDamagedQuantity();
            int currentDisplayQuantity = shipmentItem.getDisplayQuantity();
            int currentShopQuantity = shipmentItem.getShopQuantity();
            int newReceivedQuantity = currentReceivedQuantity + quantityToReceive;
            int newDamagedQuantity = currentDamagedQuantity + damagedQuantity;
            int newDisplayQuantity = currentDisplayQuantity + displayQuantity;
            int newShopQuantity = currentShopQuantity + shopQuantity;

            // Validate that we're not receiving more than ordered
            int totalAccountedFor = newReceivedQuantity + newDamagedQuantity + newDisplayQuantity + newShopQuantity;
            if (totalAccountedFor > shipmentItem.getOrderedQuantity()) {
                throw new IllegalArgumentException(
                    String.format("Cannot process %d items for shipment item %s. " +
                            "Already received: %d, Already damaged: %d, Already display: %d, Already shop: %d, Ordered: %d, " +
                            "Attempting to receive: %d, Attempting to mark damaged: %d, display: %d, shop: %d",
                            quantityToReceive + damagedQuantity + displayQuantity + shopQuantity, receipt.getShipmentItemId(),
                            currentReceivedQuantity, currentDamagedQuantity, currentDisplayQuantity, currentShopQuantity,
                            shipmentItem.getOrderedQuantity(),
                            quantityToReceive, damagedQuantity, displayQuantity, shopQuantity));
            }

            // Accumulate received quantity (only good items added to inventory)
            shipmentItem.setReceivedQuantity(newReceivedQuantity);
            // Accumulate quantities tracked but not added to inventory
            shipmentItem.setDamagedQuantity(newDamagedQuantity);
            shipmentItem.setDisplayQuantity(newDisplayQuantity);
            shipmentItem.setShopQuantity(newShopQuantity);

            // Track for audit: what was received in this batch
            int receivedInThisBatch = quantityToReceive + damagedQuantity + displayQuantity + shopQuantity;
            if (receivedInThisBatch > 0) {
                String itemName = shipmentItem.getItem().getName();
                receivedItemSummaries.add(itemName + " (" + receivedInThisBatch + ")");
                totalReceivedInBatch += receivedInThisBatch;
            }

            // Create notification for damaged items
            if (damagedQuantity > 0) {
                Product product = shipmentItem.getItem();
                Map<String, Object> damageMetadata = new HashMap<>();
                damageMetadata.put("shipment_name", shipment.getShipmentNumber());
                damageMetadata.put("product_name", product.getName());
                damageMetadata.put("damaged_quantity", damagedQuantity);
                damageMetadata.put("category", "shipment");

                // Include parent kuji name if this is a prize
                String productDisplayName = product.getName();
                if (product.getParentId() != null) {
                    Product parent = product.getParent();
                    if (parent != null) {
                        damageMetadata.put("parent_product_name", parent.getName());
                        productDisplayName = product.getName() + " (" + parent.getName() + ")";
                    }
                }

                Notification damageNotif = Notification.builder()
                        .type(NotificationType.SHIPMENT_DAMAGED)
                        .severity(NotificationSeverity.WARNING)
                        .message(damagedQuantity + " units of " + productDisplayName + " reported damaged in shipment " + shipment.getShipmentNumber())
                        .itemId(product.getId())
                        .metadata(damageMetadata)
                        .via(List.of("slack", "app"))
                        .build();
                notificationService.createNotification(damageNotif);
            }

            // Process each allocation
            for (ReceiveShipmentRequestDTO.DestinationAllocationDTO allocation : allocations) {
                Integer allocQty = allocation.getQuantity();
                if (allocQty == null || allocQty <= 0) {
                    continue;
                }

                LocationType locationType = allocation.getLocationType();
                UUID locationId = allocation.getLocationId();

                // Create allocation record for tracking
                ShipmentItemAllocation allocationRecord = ShipmentItemAllocation.builder()
                        .shipmentItem(shipmentItem)
                        .locationType(locationType != null ? locationType : LocationType.NOT_ASSIGNED)
                        .locationId(locationId)
                        .quantity(allocQty)
                        .build();
                shipmentItem.getAllocations().add(allocationRecord);

                if (locationType == LocationType.NOT_ASSIGNED
                        || locationType == null
                        || locationId == null) {
                    // Add to NotAssignedInventory
                    addToNotAssignedInventory(
                            shipmentItem.getItem(),
                            allocQty,
                            validatedActorId,
                            receiveAuditLog
                    );
                } else {
                    // Add to regular location inventory
                    addToInventory(
                            locationType,
                            locationId,
                            shipmentItem.getItem(),
                            allocQty,
                            validatedActorId,
                            receiveAuditLog
                    );
                }
            }
        }

        // Check if all items are fully accounted for (received + damaged + display + shop = ordered)
        boolean allItemsFullyReceived = shipment.getItems().stream()
                .allMatch(item -> {
                    int received = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : 0;
                    int damaged = item.getDamagedQuantity() != null ? item.getDamagedQuantity() : 0;
                    int display = item.getDisplayQuantity() != null ? item.getDisplayQuantity() : 0;
                    int shop = item.getShopQuantity() != null ? item.getShopQuantity() : 0;
                    int ordered = item.getOrderedQuantity() != null ? item.getOrderedQuantity() : 0;
                    return (received + damaged + display + shop) >= ordered;
                });

        // Status transitions:
        //   * If shipment is already RECEIVED (set via prior receipt or manual override), stay RECEIVED.
        //     This is the "sticky" semantic - corrections via Undo + Receive don't auto-revert.
        //   * Otherwise, flip to RECEIVED only when math is satisfied.
        //   * Don't auto-revert to PENDING here; reverting is the explicit job of Undo.
        boolean wasAlreadyReceived = shipment.getStatus() == ShipmentStatus.RECEIVED;
        boolean newlyReceived = !wasAlreadyReceived && allItemsFullyReceived;

        if (newlyReceived) {
            shipment.setStatus(ShipmentStatus.RECEIVED);

            // Auto-assign preferred supplier to products in this shipment
            if (shipment.getSupplier() != null) {
                autoAssignPreferredSupplier(shipment);
            }

            // Create notification for shipment completion
            List<String> productNames = shipment.getItems().stream()
                    .map(item -> {
                        Product p = item.getItem();
                        if (p.getParentId() != null && p.getParent() != null) {
                            return p.getName() + " (" + p.getParent().getName() + ")";
                        }
                        return p.getName();
                    })
                    .toList();

            Map<String, Object> completionMetadata = new HashMap<>();
            completionMetadata.put("shipment_name", shipment.getShipmentNumber());
            completionMetadata.put("items_count", shipment.getItems().size());
            completionMetadata.put("product_names", String.join(", ", productNames));
            completionMetadata.put("category", "shipment");

            Notification completionNotif = Notification.builder()
                    .type(NotificationType.SHIPMENT_COMPLETED)
                    .severity(NotificationSeverity.INFO)
                    .message("Shipment " + shipment.getShipmentNumber() + " is fully received")
                    .metadata(completionMetadata)
                    .via(List.of("slack", "app"))
                    .build();
            notificationService.createNotification(completionNotif);
        }

        // Set receivedBy on the shipment if a receiver was provided (user already resolved up front)
        if (receivedByUser != null) {
            shipment.setReceivedBy(receivedByUser);
        }

        Shipment savedShipment = shipmentRepository.save(shipment);

        // Finalize the parent audit log: set the right reason and aggregate counts based on what
        // actually got received. If nothing did, drop the orphan row entirely.
        if (receivedItemSummaries.isEmpty()) {
            auditLogService.delete(receiveAuditLog);
        } else {
            receiveAuditLog.setReason(allItemsFullyReceived
                    ? StockMovementReason.SHIPMENT_RECEIPT
                    : StockMovementReason.SHIPMENT_PARTIAL_RECEIPT);
            receiveAuditLog.setItemCount(receivedItemSummaries.size());
            receiveAuditLog.setTotalQuantityMoved(totalReceivedInBatch);
            auditLogService.save(receiveAuditLog);
        }

        // Ensure product quantity/isActive denormalized fields stay in sync for UI reads
        stockMovementService.syncProductTotals(new ArrayList<>(affectedProductIds));

        // Broadcast real-time updates to connected clients
        broadcastService.broadcastShipmentUpdated();
        broadcastService.broadcastInventoryUpdated();
        broadcastService.broadcastAuditLogCreated();

        return savedShipment;
    }

    /**
     * Undo receipt of a single shipment item: reverse stock additions for just that item.
     * If the shipment was DELIVERED, it will return to PENDING status.
     *
     * @param shipmentId The shipment ID
     * @param itemId The shipment item ID to undo
     * @param actorId The ID of the user performing the undo
     * @param actorName The name of the user performing the undo
     * @return The updated shipment
     * @throws ShipmentNotFoundException if shipment not found
     * @throws ShipmentItemNotFoundException if item not found or doesn't belong to shipment
     * @throws InvalidShipmentStatusException if item has nothing to undo
     * @throws InsufficientInventoryException if inventory has been depleted
     */
    public Shipment undoReceiveShipmentItem(UUID shipmentId, UUID itemId, UUID actorId, String actorName) {
        return undoReceiveShipmentItems(shipmentId, List.of(itemId), actorId, actorName);
    }

    /**
     * Reverse receipt of one or more shipment items in a single transaction. Treats the whole
     * batch as one user action: produces exactly one parent AuditLog (reason SHIPMENT_RECEIPT_REVERSED)
     * with N stock movement rows under it, mirroring how receiveShipment / batchTransferInventory work.
     */
    public Shipment undoReceiveShipmentItems(UUID shipmentId, List<UUID> itemIds, UUID actorId, String actorName) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("itemIds must not be empty");
        }

        Shipment shipment = getShipmentById(shipmentId);

        // Resolve every requested shipment item up front and validate it belongs to this shipment
        // and has something to undo. We fail the entire batch on the first invalid item — the user
        // explicitly selected each one, so partial-failure semantics would be confusing.
        List<ShipmentItem> targets = new ArrayList<>(itemIds.size());
        for (UUID itemId : itemIds) {
            ShipmentItem shipmentItem = shipment.getItems().stream()
                    .filter(it -> it.getId().equals(itemId))
                    .findFirst()
                    .orElseThrow(() -> new ShipmentItemNotFoundException(
                            "Shipment item " + itemId + " not found in shipment " + shipmentId));

            boolean hasReceivedQuantities = shipmentItem.getReceivedQuantity() > 0
                    || shipmentItem.getDamagedQuantity() > 0
                    || shipmentItem.getDisplayQuantity() > 0
                    || shipmentItem.getShopQuantity() > 0;
            boolean hasAllocations = !shipmentItem.getAllocations().isEmpty();
            if (!hasReceivedQuantities && !hasAllocations) {
                throw new InvalidShipmentStatusException(
                        "Shipment item " + itemId + " has not been received yet. Nothing to undo.");
            }
            targets.add(shipmentItem);
        }

        Set<UUID> affectedProductIds = new java.util.HashSet<>();
        for (ShipmentItem t : targets) {
            affectedProductIds.add(t.getItem().getId());
        }

        // Total quantity reversed across all selected items, used for the audit row's display values.
        int totalReversed = targets.stream()
                .flatMap(t -> t.getAllocations().stream())
                .mapToInt(ShipmentItemAllocation::getQuantity)
                .sum();

        // Create the single parent audit log up front. Even if no allocations exist (only
        // damaged/display/shop quantities), we still want a single audit row representing
        // the reversal action.
        AuditLog reverseAuditLog = auditLogService.createAuditLog(
                actorId,
                actorName,
                StockMovementReason.SHIPMENT_RECEIPT_REVERSED,
                null, null, null, null,
                targets.size(),
                totalReversed,
                "Shipment " + shipment.getShipmentNumber(),
                null
        );
        reverseAuditLog.setShipmentId(shipment.getId());
        reverseAuditLog.setShipmentNumber(shipment.getShipmentNumber());
        auditLogService.save(reverseAuditLog);

        // Now process each item under the single parent audit log
        for (ShipmentItem shipmentItem : targets) {
            Product product = shipmentItem.getItem();

            // Multiple partial-receives leave one allocation row per receive event, even
            // when they target the same location. Sum them per (locationType, locationId)
            // so the reversal emits one stock movement per location bucket — otherwise
            // the audit log shows the same product twice (e.g. 2->1 then 1->0) for what
            // is conceptually a single -2 reversal.
            Map<AllocationBucketKey, Integer> bucketedQuantities = new LinkedHashMap<>();
            for (ShipmentItemAllocation allocation : shipmentItem.getAllocations()) {
                LocationType locationType = allocation.getLocationType();
                UUID locationId = allocation.getLocationId();
                AllocationBucketKey key = (locationType == LocationType.NOT_ASSIGNED || locationId == null)
                        ? AllocationBucketKey.NOT_ASSIGNED
                        : new AllocationBucketKey(locationType, locationId);
                bucketedQuantities.merge(key, allocation.getQuantity(), Integer::sum);
            }

            for (Map.Entry<AllocationBucketKey, Integer> entry : bucketedQuantities.entrySet()) {
                AllocationBucketKey key = entry.getKey();
                int quantity = entry.getValue();
                if (key.locationId() == null) {
                    removeFromNotAssignedInventory(product, quantity, actorId, reverseAuditLog);
                } else {
                    removeFromInventory(key.locationType(), key.locationId(), product, quantity, actorId, reverseAuditLog);
                }
            }

            // Clear allocations (orphanRemoval will delete them)
            shipmentItem.getAllocations().clear();

            // Reset quantities for this item
            shipmentItem.setReceivedQuantity(0);
            shipmentItem.setDamagedQuantity(0);
            shipmentItem.setDisplayQuantity(0);
            shipmentItem.setShopQuantity(0);
        }

        // Recompute status from item math. Undo is an explicit reversal -
        // if the remaining items no longer satisfy the fully-received check, drop back to PENDING.
        boolean stillFullyReceived = shipment.getItems().stream()
                .allMatch(it -> {
                    int rec = it.getReceivedQuantity() != null ? it.getReceivedQuantity() : 0;
                    int dmg = it.getDamagedQuantity() != null ? it.getDamagedQuantity() : 0;
                    int dsp = it.getDisplayQuantity() != null ? it.getDisplayQuantity() : 0;
                    int shp = it.getShopQuantity() != null ? it.getShopQuantity() : 0;
                    int ord = it.getOrderedQuantity() != null ? it.getOrderedQuantity() : 0;
                    return (rec + dmg + dsp + shp) >= ord;
                });
        if (shipment.getStatus() == ShipmentStatus.RECEIVED && !stillFullyReceived) {
            shipment.setStatus(ShipmentStatus.PENDING);
            // Keep actualDeliveryDate and receivedBy since other items may still be received
        }

        Shipment savedShipment = shipmentRepository.save(shipment);

        // Sync product totals
        stockMovementService.syncProductTotals(new ArrayList<>(affectedProductIds));

        // Broadcast updates
        broadcastService.broadcastShipmentUpdated();
        broadcastService.broadcastInventoryUpdated();
        broadcastService.broadcastAuditLogCreated();

        return savedShipment;
    }

    /**
     * Manually override a shipment's inventory status. Pure label change - no inventory side effects.
     * Restricted to PENDING / RECEIVED targets; CANCELLED is reachable only via Delete.
     * Reason is required for the audit trail.
     */
    public Shipment overrideShipmentStatus(UUID shipmentId, ShipmentStatus newStatus, String reason,
                                           UUID actorId, String actorName) {
        if (newStatus != ShipmentStatus.PENDING && newStatus != ShipmentStatus.RECEIVED) {
            throw new InvalidShipmentStatusException(
                    "Status override only supports PENDING or RECEIVED. Use Delete to cancel a shipment.");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidShipmentStatusException("A reason is required when overriding shipment status.");
        }

        Shipment shipment = getShipmentById(shipmentId);
        ShipmentStatus oldStatus = shipment.getStatus();
        if (oldStatus == newStatus) {
            return shipment;
        }

        shipment.setStatus(newStatus);
        Shipment savedShipment = shipmentRepository.save(shipment);

        auditLogService.createShipmentEvent(
                actorId,
                actorName,
                StockMovementReason.SHIPMENT_STATUS_OVERRIDDEN,
                shipment.getId(),
                shipment.getShipmentNumber(),
                1,
                null,
                oldStatus.name(),
                newStatus.name(),
                reason.trim()
        );

        broadcastService.broadcastShipmentUpdated();
        broadcastService.broadcastAuditLogCreated();

        return savedShipment;
    }

    /**
     * Add inventory to a location using the unified location_inventory table.
     * The caller passes the parent audit log; this method only writes a stock movement under it.
     */
    private void addToInventory(LocationType locationType, UUID locationId, Product product, int quantity, UUID validatedActorId, AuditLog parentAuditLog) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new LocationNotFoundException("Location not found: " + locationId));

        Site site = location.getStorageLocation().getSite();

        // Find or create inventory at this location
        LocationInventory inventory = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(locationId, product.getId())
                .orElseGet(() -> LocationInventory.builder()
                        .location(location)
                        .site(site)
                        .product(product)
                        .quantity(0)
                        .build());

        int previousQuantity = inventory.getQuantity();
        inventory.setQuantity(previousQuantity + quantity);
        LocationInventory saved = locationInventoryRepository.save(inventory);
        int currentQuantity = saved.getQuantity();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("inventory_id", saved.getId().toString());
        metadata.put("shipment_receipt", true);

        StockMovement movement = StockMovement.builder()
                .auditLog(parentAuditLog)
                .item(product)
                .locationType(locationType)
                .toLocationId(locationId)
                .previousQuantity(previousQuantity)
                .currentQuantity(currentQuantity)
                .quantityChange(quantity)
                .reason(StockMovementReason.SHIPMENT_RECEIPT)
                .actorId(validatedActorId)
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();

        stockMovementRepository.save(movement);
        eventOutboxService.createStockMovementEvent(movement);
    }

    /**
     * Add inventory to NOT_ASSIGNED location using the unified location_inventory table.
     * The caller passes the parent audit log; this method only writes a stock movement under it.
     */
    private void addToNotAssignedInventory(Product product, int quantity, UUID validatedActorId, AuditLog parentAuditLog) {
        // Get NOT_ASSIGNED location
        Location notAssignedLocation = getNotAssignedLocation();

        LocationInventory inventory = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(notAssignedLocation.getId(), product.getId())
                .orElseGet(() -> LocationInventory.builder()
                        .location(notAssignedLocation)
                        .site(notAssignedLocation.getStorageLocation().getSite())
                        .product(product)
                        .quantity(0)
                        .build());

        int previousQuantity = inventory.getQuantity();
        inventory.setQuantity(previousQuantity + quantity);
        LocationInventory saved = locationInventoryRepository.save(inventory);
        int currentQuantity = saved.getQuantity();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("inventory_id", saved.getId().toString());
        metadata.put("shipment_receipt", true);

        StockMovement movement = StockMovement.builder()
                .auditLog(parentAuditLog)
                .item(product)
                .locationType(LocationType.NOT_ASSIGNED)
                .toLocationId(notAssignedLocation.getId())
                .previousQuantity(previousQuantity)
                .currentQuantity(currentQuantity)
                .quantityChange(quantity)
                .reason(StockMovementReason.SHIPMENT_RECEIPT)
                .actorId(validatedActorId)
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();

        stockMovementRepository.save(movement);
        eventOutboxService.createStockMovementEvent(movement);
    }

    /**
     * Get the NOT_ASSIGNED location for the default site.
     */
    private Location getNotAssignedLocation() {
        Site site = siteRepository.findByCode(DEFAULT_SITE_CODE)
                .orElseThrow(() -> new SiteNotFoundException("Default site not found: " + DEFAULT_SITE_CODE));

        return locationRepository.findByLocationCodeAndStorageLocationCodeAndSiteId("NA", "NOT_ASSIGNED", site.getId())
                .orElseThrow(() -> new LocationNotFoundException("NOT_ASSIGNED location not found"));
    }

    /**
     * Remove quantity from inventory at a specific location (for undo operations).
     * Uses the unified location_inventory table. The caller passes the parent audit log; this method
     * only writes a stock movement under it.
     */
    private void removeFromInventory(LocationType locationType, UUID locationId, Product product, int quantity,
                                     UUID actorId, AuditLog parentAuditLog) {
        LocationInventory inventory = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(locationId, product.getId())
                .orElseThrow(() -> new InsufficientInventoryException(
                        String.format("Cannot undo receipt: no inventory found for '%s' at location. " +
                                "The inventory record may have been deleted.", product.getName())
                ));

        int currentQuantity = inventory.getQuantity();
        if (currentQuantity < quantity) {
            throw new InsufficientInventoryException(
                    String.format("Cannot undo receipt for '%s': only %d units available at location, " +
                            "but %d were originally received. Items may have been sold or transferred.",
                            product.getName(), currentQuantity, quantity)
            );
        }

        int newQuantity = currentQuantity - quantity;

        // Create stock movement record
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("shipment_receipt_reversal", true);

        StockMovement movement = StockMovement.builder()
                .auditLog(parentAuditLog)
                .item(product)
                .locationType(locationType)
                .fromLocationId(locationId)
                .previousQuantity(currentQuantity)
                .currentQuantity(newQuantity)
                .quantityChange(-quantity)
                .reason(StockMovementReason.SHIPMENT_RECEIPT_REVERSED)
                .actorId(actorId)
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();

        stockMovementRepository.save(movement);
        eventOutboxService.createStockMovementEvent(movement);

        // Update or delete inventory
        if (newQuantity == 0) {
            locationInventoryRepository.delete(inventory);
        } else {
            inventory.setQuantity(newQuantity);
            locationInventoryRepository.save(inventory);
        }
    }

    /**
     * Remove quantity from NOT_ASSIGNED inventory (for undo operations).
     * Uses the unified location_inventory table. The caller passes the parent audit log; this method
     * only writes a stock movement under it.
     */
    private void removeFromNotAssignedInventory(Product product, int quantity,
                                                UUID actorId, AuditLog parentAuditLog) {
        Location notAssignedLocation = getNotAssignedLocation();

        LocationInventory inventory = locationInventoryRepository
                .findByLocation_IdAndProduct_Id(notAssignedLocation.getId(), product.getId())
                .orElseThrow(() -> new InsufficientInventoryException(
                        String.format("Cannot undo receipt: no unassigned inventory for '%s'. " +
                                "Items may have been assigned to locations.", product.getName())
                ));

        int currentQuantity = inventory.getQuantity();
        if (currentQuantity < quantity) {
            throw new InsufficientInventoryException(
                    String.format("Cannot undo receipt for '%s': only %d units in unassigned inventory, " +
                            "but %d were originally received. Items may have been assigned to locations.",
                            product.getName(), currentQuantity, quantity)
            );
        }

        int newQuantity = currentQuantity - quantity;

        // Create stock movement
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("shipment_receipt_reversal", true);

        StockMovement movement = StockMovement.builder()
                .auditLog(parentAuditLog)
                .item(product)
                .locationType(LocationType.NOT_ASSIGNED)
                .fromLocationId(notAssignedLocation.getId())
                .previousQuantity(currentQuantity)
                .currentQuantity(newQuantity)
                .quantityChange(-quantity)
                .reason(StockMovementReason.SHIPMENT_RECEIPT_REVERSED)
                .actorId(actorId)
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();

        stockMovementRepository.save(movement);
        eventOutboxService.createStockMovementEvent(movement);

        if (newQuantity == 0) {
            locationInventoryRepository.delete(inventory);
        } else {
            inventory.setQuantity(newQuantity);
            locationInventoryRepository.save(inventory);
        }
    }

    /**
     * Auto-assign the shipment's supplier as the preferred supplier for all products in the shipment.
     * Sets preferred_supplier_auto = true to indicate this was auto-assigned (not manually set).
     * Respects manual selections: only updates if no supplier set OR not explicitly manual (auto != false).
     *
     * Optimized to batch all product updates in a single saveAll() call instead of N individual saves.
     */
    private void autoAssignPreferredSupplier(Shipment shipment) {
        Supplier supplier = shipment.getSupplier();
        if (supplier == null) {
            return;
        }

        List<Product> productsToUpdate = new ArrayList<>();

        for (ShipmentItem item : shipment.getItems()) {
            Product product = item.getItem();
            if (product != null) {
                // Only auto-assign if no supplier OR not explicitly manual
                // Treats null as "auto" for backward compatibility with existing data
                if (product.getPreferredSupplierId() == null ||
                    !Boolean.FALSE.equals(product.getPreferredSupplierAuto())) {
                    product.setPreferredSupplier(supplier);
                    product.setPreferredSupplierAuto(true);
                    productsToUpdate.add(product);
                }
            }
        }

        if (!productsToUpdate.isEmpty()) {
            productRepository.saveAll(productsToUpdate);
        }
    }

    // ============================================================
    // Shipment Audit Helper Methods
    // ============================================================

    /**
     * Snapshot of shipment state for change detection
     */
    private record ShipmentSnapshot(
            String shipmentNumber,
            String supplierName,
            ShipmentStatus status,
            java.time.LocalDate orderDate,
            java.time.LocalDate expectedDeliveryDate,
            java.time.LocalDate actualDeliveryDate,
            java.math.BigDecimal totalCost,
            String notes,
            String trackingId,
            List<ItemSnapshot> items
    ) {}

    private record ItemSnapshot(
            UUID productId,
            String productName,
            int orderedQuantity
    ) {}

    /**
     * Capture current shipment state for comparison
     */
    private ShipmentSnapshot captureShipmentSnapshot(Shipment shipment) {
        List<ItemSnapshot> items = shipment.getItems().stream()
                .map(item -> new ItemSnapshot(
                        item.getItem().getId(),
                        item.getItem().getName(),
                        item.getOrderedQuantity() != null ? item.getOrderedQuantity() : 0
                ))
                .toList();

        return new ShipmentSnapshot(
                shipment.getShipmentNumber(),
                shipment.getSupplierName(),
                shipment.getStatus(),
                shipment.getOrderDate(),
                shipment.getExpectedDeliveryDate(),
                shipment.getActualDeliveryDate(),
                shipment.getTotalCost(),
                shipment.getNotes(),
                shipment.getTrackingId(),
                items
        );
    }

    /**
     * Create audit log for shipment edit, capturing all changes as a structured field_changes JSON array.
     */
    private void createShipmentEditAudit(
            Shipment shipment,
            ShipmentSnapshot before,
            ShipmentSnapshot after,
            UUID actorId,
            String actorName
    ) {
        List<Map<String, Object>> changes = new ArrayList<>();

        // Compare scalar fields
        if (!java.util.Objects.equals(before.supplierName(), after.supplierName())) {
            changes.add(fieldChange("supplier", before.supplierName(), after.supplierName()));
        }
        if (!java.util.Objects.equals(before.shipmentNumber(), after.shipmentNumber())) {
            changes.add(fieldChange("shipmentNumber", before.shipmentNumber(), after.shipmentNumber()));
        }
        if (!java.util.Objects.equals(before.status(), after.status())) {
            changes.add(fieldChange("status",
                    before.status() != null ? before.status().name() : null,
                    after.status() != null ? after.status().name() : null));
        }
        if (!java.util.Objects.equals(before.orderDate(), after.orderDate())) {
            changes.add(fieldChange("orderDate",
                    before.orderDate() != null ? before.orderDate().toString() : null,
                    after.orderDate() != null ? after.orderDate().toString() : null));
        }
        if (!java.util.Objects.equals(before.expectedDeliveryDate(), after.expectedDeliveryDate())) {
            changes.add(fieldChange("expectedDeliveryDate",
                    before.expectedDeliveryDate() != null ? before.expectedDeliveryDate().toString() : null,
                    after.expectedDeliveryDate() != null ? after.expectedDeliveryDate().toString() : null));
        }
        if (!java.util.Objects.equals(before.actualDeliveryDate(), after.actualDeliveryDate())) {
            changes.add(fieldChange("actualDeliveryDate",
                    before.actualDeliveryDate() != null ? before.actualDeliveryDate().toString() : null,
                    after.actualDeliveryDate() != null ? after.actualDeliveryDate().toString() : null));
        }
        if (!bigDecimalEquals(before.totalCost(), after.totalCost())) {
            changes.add(fieldChange("totalCost",
                    before.totalCost() != null ? before.totalCost().toPlainString() : null,
                    after.totalCost() != null ? after.totalCost().toPlainString() : null));
        }
        if (!java.util.Objects.equals(before.trackingId(), after.trackingId())) {
            changes.add(fieldChange("trackingId", before.trackingId(), after.trackingId()));
        }
        if (!java.util.Objects.equals(before.notes(), after.notes())) {
            // Don't reveal note contents in the audit log; just record that they changed.
            Map<String, Object> notesEntry = new HashMap<>();
            notesEntry.put("field", "notes");
            notesEntry.put("changed", true);
            changes.add(notesEntry);
        }

        // Compare items
        Set<UUID> beforeProductIds = before.items().stream().map(ItemSnapshot::productId).collect(Collectors.toSet());
        Set<UUID> afterProductIds = after.items().stream().map(ItemSnapshot::productId).collect(Collectors.toSet());

        List<Map<String, Object>> addedItems = after.items().stream()
                .filter(item -> !beforeProductIds.contains(item.productId()))
                .map(item -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", item.productName());
                    m.put("qty", item.orderedQuantity());
                    return m;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> removedItems = before.items().stream()
                .filter(item -> !afterProductIds.contains(item.productId()))
                .map(item -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", item.productName());
                    m.put("qty", item.orderedQuantity());
                    return m;
                })
                .collect(Collectors.toList());

        if (!addedItems.isEmpty()) {
            changes.add(fieldChange("items_added", null, addedItems));
        }
        if (!removedItems.isEmpty()) {
            changes.add(fieldChange("items_removed", removedItems, null));
        }

        if (changes.isEmpty()) {
            return;
        }

        auditLogService.createShipmentEvent(
                actorId,
                actorName,
                StockMovementReason.SHIPMENT_EDITED,
                shipment.getId(),
                shipment.getShipmentNumber(),
                1,
                changes,
                null,
                null,
                null
        );
    }

    private static Map<String, Object> fieldChange(String field, Object from, Object to) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("field", field);
        entry.put("from", from);
        entry.put("to", to);
        return entry;
    }

    /**
     * Create audit log for shipment deletion, capturing full details as structured JSON.
     */
    private void createShipmentDeletionAudit(Shipment shipment, UUID actorId, String actorName) {
        List<Map<String, Object>> deletedItems = shipment.getItems().stream()
                .map(item -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("name", item.getItem().getName());
                    entry.put("ordered", item.getOrderedQuantity() != null ? item.getOrderedQuantity() : 0);
                    entry.put("received", item.getReceivedQuantity() != null ? item.getReceivedQuantity() : 0);
                    return entry;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> changes = new ArrayList<>();
        Map<String, Object> supplierEntry = new HashMap<>();
        supplierEntry.put("field", "supplier");
        supplierEntry.put("value", shipment.getSupplierName());
        changes.add(supplierEntry);
        changes.add(fieldChange("deleted_items", null, deletedItems));

        auditLogService.createShipmentEvent(
                actorId,
                actorName,
                StockMovementReason.SHIPMENT_DELETED,
                shipment.getId(),
                shipment.getShipmentNumber(),
                shipment.getItems().size(),
                changes,
                null,
                null,
                null
        );
    }

    private String nvl(Object value) {
        return value == null ? "(none)" : value.toString();
    }

    /**
     * Compare BigDecimals by value (ignoring scale), handling nulls.
     * Uses compareTo() instead of equals() since 153.00 should equal 153.
     */
    private boolean bigDecimalEquals(java.math.BigDecimal a, java.math.BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    private record AllocationBucketKey(LocationType locationType, UUID locationId) {
        static final AllocationBucketKey NOT_ASSIGNED = new AllocationBucketKey(LocationType.NOT_ASSIGNED, null);
    }
}
