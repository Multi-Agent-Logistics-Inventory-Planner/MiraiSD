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
            EventOutboxService eventOutboxService) {
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
    }

    public Shipment createShipment(ShipmentRequestDTO requestDTO) {
        Shipment shipment = Shipment.builder()
                .shipmentNumber(requestDTO.getShipmentNumber())
                .supplierName(requestDTO.getSupplierName())
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

    // Statuses considered "active" (not yet fully received)
    private static final List<ShipmentStatus> PENDING_STATUSES = List.of(
            ShipmentStatus.PENDING, ShipmentStatus.IN_TRANSIT
    );

    /**
     * List shipments by display status with pagination.
     * Display statuses are: ACTIVE (pending with no received items), PARTIAL (partially received),
     * COMPLETED (fully delivered).
     */
    public Page<Shipment> listShipmentsByDisplayStatus(String displayStatus, String search, Pageable pageable) {
        String searchPattern = (search != null && !search.isBlank()) ? "%" + search.toLowerCase() + "%" : null;

        return switch (displayStatus) {
            case "ACTIVE" -> searchPattern != null
                    ? shipmentRepository.findActiveShipmentsWithSearch(PENDING_STATUSES, searchPattern, pageable)
                    : shipmentRepository.findActiveShipments(PENDING_STATUSES, pageable);
            case "PARTIAL" -> searchPattern != null
                    ? shipmentRepository.findPartialShipmentsWithSearch(PENDING_STATUSES, searchPattern, pageable)
                    : shipmentRepository.findPartialShipments(PENDING_STATUSES, pageable);
            case "COMPLETED" -> searchPattern != null
                    ? shipmentRepository.findCompletedShipmentsWithSearch(ShipmentStatus.DELIVERED, searchPattern, pageable)
                    : shipmentRepository.findCompletedShipments(ShipmentStatus.DELIVERED, pageable);
            default -> listShipmentsPaged(null, search, pageable);
        };
    }

    /**
     * Get counts for each display status (for tab indicators).
     */
    public Map<String, Long> getDisplayStatusCounts() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("ACTIVE", shipmentRepository.countActiveShipments(PENDING_STATUSES));
        counts.put("PARTIAL", shipmentRepository.countPartialShipments(PENDING_STATUSES));
        counts.put("COMPLETED", shipmentRepository.countCompletedShipments(ShipmentStatus.DELIVERED));
        counts.put("OVERDUE", shipmentRepository.countOverdueShipments(PENDING_STATUSES));
        return counts;
    }

    public List<Shipment> getShipmentsContainingProduct(UUID productId) {
        return shipmentRepository.findByItemsContainingProduct(productId);
    }

    public Shipment updateShipment(UUID id, ShipmentRequestDTO requestDTO) {
        Shipment shipment = getShipmentById(id);

        if (requestDTO.getShipmentNumber() != null) {
            shipment.setShipmentNumber(requestDTO.getShipmentNumber());
        }
        if (requestDTO.getSupplierName() != null) {
            shipment.setSupplierName(requestDTO.getSupplierName());
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
            // Cannot modify items on a delivered shipment
            if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
                throw new InvalidShipmentStatusException("Cannot modify items on a delivered shipment");
            }

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

                // Skip if this product was already received - it's preserved
                if (existingProductIds.contains(productId)) {
                    // Optionally update orderedQuantity if the new value is >= total received
                    ShipmentItem receivedItem = receivedItemsByProductId.get(productId);
                    int totalReceived = receivedItem.getReceivedQuantity()
                            + receivedItem.getDamagedQuantity()
                            + receivedItem.getDisplayQuantity()
                            + receivedItem.getShopQuantity();

                    if (itemDTO.getOrderedQuantity() >= totalReceived) {
                        receivedItem.setOrderedQuantity(itemDTO.getOrderedQuantity());
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

        // Broadcast real-time update to connected clients
        broadcastService.broadcastShipmentUpdated();

        return savedShipment;
    }

    public void deleteShipment(UUID id) {
        Shipment shipment = getShipmentById(id);
        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new InvalidShipmentStatusException("Cannot delete a delivered shipment");
        }
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

        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new InvalidShipmentStatusException("Shipment already delivered");
        }
        if (shipment.getStatus() == ShipmentStatus.CANCELLED) {
            throw new InvalidShipmentStatusException("Cannot receive a cancelled shipment");
        }

        // Set actual delivery date on first receipt, or update if this is a later receipt
        if (shipment.getActualDeliveryDate() == null) {
            shipment.setActualDeliveryDate(requestDTO.getActualDeliveryDate());
        }

        // Issue 7 fix: Resolve actor ID once before the loop
        UUID validatedActorId = null;
        if (requestDTO.getReceivedBy() != null) {
            if (userRepository.findById(requestDTO.getReceivedBy()).isPresent()) {
                validatedActorId = requestDTO.getReceivedBy();
            }
        }

        // Issue 8 fix: Batch fetch all shipment items upfront
        Set<UUID> shipmentItemIds = requestDTO.getItemReceipts().stream()
                .map(ReceiveShipmentRequestDTO.ItemReceiptDTO::getShipmentItemId)
                .collect(Collectors.toSet());
        Map<UUID, ShipmentItem> shipmentItemMap = shipmentItemRepository.findAllById(shipmentItemIds).stream()
                .collect(Collectors.toMap(ShipmentItem::getId, Function.identity()));

        Set<UUID> affectedProductIds = new java.util.HashSet<>();

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
                            shipment.getShipmentNumber(),
                            shipment.getId()
                    );
                } else {
                    // Add to regular location inventory
                    addToInventory(
                            locationType,
                            locationId,
                            shipmentItem.getItem(),
                            allocQty,
                            validatedActorId
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

        // Only mark as DELIVERED if all items are fully received, otherwise keep as PENDING
        if (allItemsFullyReceived) {
            shipment.setStatus(ShipmentStatus.DELIVERED);

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
                    .message("Shipment " + shipment.getShipmentNumber() + " has been fully received")
                    .metadata(completionMetadata)
                    .via(List.of("slack", "app"))
                    .build();
            notificationService.createNotification(completionNotif);
        } else {
            shipment.setStatus(ShipmentStatus.PENDING);
        }

        // Set receivedBy if provided
        if (requestDTO.getReceivedBy() != null) {
            userRepository.findById(requestDTO.getReceivedBy())
                    .ifPresent(shipment::setReceivedBy);
        }

        Shipment savedShipment = shipmentRepository.save(shipment);

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
     * @return The updated shipment
     * @throws ShipmentNotFoundException if shipment not found
     * @throws ShipmentItemNotFoundException if item not found or doesn't belong to shipment
     * @throws InvalidShipmentStatusException if item has nothing to undo
     * @throws InsufficientInventoryException if inventory has been depleted
     */
    public Shipment undoReceiveShipmentItem(UUID shipmentId, UUID itemId) {
        Shipment shipment = getShipmentById(shipmentId);

        // Find the shipment item
        ShipmentItem shipmentItem = shipment.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ShipmentItemNotFoundException(
                        "Shipment item " + itemId + " not found in shipment " + shipmentId));

        // Validate item has something to undo
        boolean hasReceivedQuantities = shipmentItem.getReceivedQuantity() > 0
                || shipmentItem.getDamagedQuantity() > 0
                || shipmentItem.getDisplayQuantity() > 0
                || shipmentItem.getShopQuantity() > 0;
        boolean hasAllocations = !shipmentItem.getAllocations().isEmpty();

        if (!hasReceivedQuantities && !hasAllocations) {
            throw new InvalidShipmentStatusException(
                    "Shipment item has not been received yet. Nothing to undo.");
        }

        Product product = shipmentItem.getItem();
        Set<UUID> affectedProductIds = new java.util.HashSet<>();
        affectedProductIds.add(product.getId());

        // Reverse each allocation (same logic as undoReceiveShipment)
        for (ShipmentItemAllocation allocation : shipmentItem.getAllocations()) {
            LocationType locationType = allocation.getLocationType();
            UUID locationId = allocation.getLocationId();
            int quantity = allocation.getQuantity();

            if (locationType == LocationType.NOT_ASSIGNED || locationId == null) {
                removeFromNotAssignedInventory(product, quantity);
            } else {
                removeFromInventory(locationType, locationId, product, quantity);
            }
        }

        // Clear allocations (orphanRemoval will delete them)
        shipmentItem.getAllocations().clear();

        // Reset quantities for this item only
        shipmentItem.setReceivedQuantity(0);
        shipmentItem.setDamagedQuantity(0);
        shipmentItem.setDisplayQuantity(0);
        shipmentItem.setShopQuantity(0);

        // Update shipment status: if shipment was DELIVERED, it should now be PENDING
        // since not all items are fully received anymore
        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
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
     * Add inventory to a location using the unified location_inventory table.
     */
    private void addToInventory(LocationType locationType, UUID locationId, Product product, int quantity, UUID validatedActorId) {
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

        String toLocationCode = location.getLocationCode();
        AuditLog auditLog = auditLogService.createAuditLog(
                validatedActorId,
                StockMovementReason.SHIPMENT_RECEIPT,
                null,
                null,
                locationId,
                toLocationCode,
                1,
                quantity,
                product.getName(),
                null
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("inventory_id", saved.getId().toString());
        metadata.put("shipment_receipt", true);

        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
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
     */
    private void addToNotAssignedInventory(Product product, int quantity, UUID validatedActorId, String shipmentNumber, UUID shipmentId) {
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

        AuditLog auditLog = auditLogService.createAuditLog(
                validatedActorId,
                StockMovementReason.SHIPMENT_RECEIPT,
                null,
                null,
                notAssignedLocation.getId(),
                "NA",
                1,
                quantity,
                product.getName(),
                null
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("inventory_id", saved.getId().toString());
        metadata.put("shipment_receipt", true);

        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
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
     * Uses the unified location_inventory table.
     */
    private void removeFromInventory(LocationType locationType, UUID locationId, Product product, int quantity) {
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
        String locationCode = inventory.getLocation().getLocationCode();

        // Create audit log for reversal
        AuditLog auditLog = auditLogService.createAuditLog(
                null,  // No specific actor for system reversal
                StockMovementReason.SHIPMENT_RECEIPT_REVERSED,
                locationId,
                locationCode,
                null,
                null,
                1,
                quantity,
                product.getName(),
                "Shipment receipt reversed"
        );

        // Create stock movement record
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("shipment_receipt_reversal", true);

        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .item(product)
                .locationType(locationType)
                .fromLocationId(locationId)
                .previousQuantity(currentQuantity)
                .currentQuantity(newQuantity)
                .quantityChange(-quantity)
                .reason(StockMovementReason.SHIPMENT_RECEIPT_REVERSED)
                .actorId(null)
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
     * Uses the unified location_inventory table.
     */
    private void removeFromNotAssignedInventory(Product product, int quantity) {
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

        // Create audit log
        AuditLog auditLog = auditLogService.createAuditLog(
                null,
                StockMovementReason.SHIPMENT_RECEIPT_REVERSED,
                notAssignedLocation.getId(),
                "NA",
                null,
                null,
                1,
                quantity,
                product.getName(),
                "Shipment receipt reversed"
        );

        // Create stock movement
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("shipment_receipt_reversal", true);

        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .item(product)
                .locationType(LocationType.NOT_ASSIGNED)
                .fromLocationId(notAssignedLocation.getId())
                .previousQuantity(currentQuantity)
                .currentQuantity(newQuantity)
                .quantityChange(-quantity)
                .reason(StockMovementReason.SHIPMENT_RECEIPT_REVERSED)
                .actorId(null)
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
}
