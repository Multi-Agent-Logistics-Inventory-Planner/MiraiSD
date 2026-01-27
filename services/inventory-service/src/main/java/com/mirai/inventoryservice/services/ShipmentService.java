package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.ReceiveShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentItemRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentRequestDTO;
import com.mirai.inventoryservice.exceptions.BoxBinNotFoundException;
import com.mirai.inventoryservice.exceptions.CabinetNotFoundException;
import com.mirai.inventoryservice.exceptions.DoubleClawMachineNotFoundException;
import com.mirai.inventoryservice.exceptions.InvalidShipmentStatusException;
import com.mirai.inventoryservice.exceptions.KeychainMachineNotFoundException;
import com.mirai.inventoryservice.exceptions.ProductNotFoundException;
import com.mirai.inventoryservice.exceptions.RackNotFoundException;
import com.mirai.inventoryservice.exceptions.ShipmentNotFoundException;
import com.mirai.inventoryservice.exceptions.SingleClawMachineNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.*;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.models.shipment.ShipmentItem;
import com.mirai.inventoryservice.repositories.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ShipmentService {
    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final ProductService productService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;
    private final EventOutboxService eventOutboxService;
    private final BoxBinInventoryRepository boxBinInventoryRepository;
    private final SingleClawMachineInventoryRepository singleClawMachineInventoryRepository;
    private final DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository;
    private final KeychainMachineInventoryRepository keychainMachineInventoryRepository;
    private final CabinetInventoryRepository cabinetInventoryRepository;
    private final RackInventoryRepository rackInventoryRepository;
    private final BoxBinRepository boxBinRepository;
    private final RackRepository rackRepository;
    private final CabinetRepository cabinetRepository;
    private final SingleClawMachineRepository singleClawMachineRepository;
    private final DoubleClawMachineRepository doubleClawMachineRepository;
    private final KeychainMachineRepository keychainMachineRepository;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            ProductService productService,
            UserService userService,
            UserRepository userRepository,
            StockMovementRepository stockMovementRepository,
            EventOutboxService eventOutboxService,
            BoxBinInventoryRepository boxBinInventoryRepository,
            SingleClawMachineInventoryRepository singleClawMachineInventoryRepository,
            DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository,
            KeychainMachineInventoryRepository keychainMachineInventoryRepository,
            CabinetInventoryRepository cabinetInventoryRepository,
            RackInventoryRepository rackInventoryRepository,
            BoxBinRepository boxBinRepository,
            RackRepository rackRepository,
            CabinetRepository cabinetRepository,
            SingleClawMachineRepository singleClawMachineRepository,
            DoubleClawMachineRepository doubleClawMachineRepository,
            KeychainMachineRepository keychainMachineRepository) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.productService = productService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.eventOutboxService = eventOutboxService;
        this.boxBinInventoryRepository = boxBinInventoryRepository;
        this.singleClawMachineInventoryRepository = singleClawMachineInventoryRepository;
        this.doubleClawMachineInventoryRepository = doubleClawMachineInventoryRepository;
        this.keychainMachineInventoryRepository = keychainMachineInventoryRepository;
        this.cabinetInventoryRepository = cabinetInventoryRepository;
        this.rackInventoryRepository = rackInventoryRepository;
        this.boxBinRepository = boxBinRepository;
        this.rackRepository = rackRepository;
        this.cabinetRepository = cabinetRepository;
        this.singleClawMachineRepository = singleClawMachineRepository;
        this.doubleClawMachineRepository = doubleClawMachineRepository;
        this.keychainMachineRepository = keychainMachineRepository;
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
                .build();

        if (requestDTO.getCreatedBy() != null) {
            User user = userService.getUserById(requestDTO.getCreatedBy());
            shipment.setCreatedBy(user);
        }

        List<ShipmentItem> items = new ArrayList<>();
        for (ShipmentItemRequestDTO itemDTO : requestDTO.getItems()) {
            Product product = productService.getProductById(itemDTO.getItemId());

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
        return shipmentRepository.save(shipment);
    }

    public Shipment getShipmentById(UUID id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ShipmentNotFoundException("Shipment not found with id: " + id));
    }

    public List<Shipment> listShipments() {
        return shipmentRepository.findAll();
    }

    public List<Shipment> listShipmentsByStatus(ShipmentStatus status) {
        return shipmentRepository.findByStatusOrderByCreatedAtDesc(status);
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

        return shipmentRepository.save(shipment);
    }

    public void deleteShipment(UUID id) {
        Shipment shipment = getShipmentById(id);
        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new InvalidShipmentStatusException("Cannot delete a delivered shipment");
        }
        shipmentRepository.delete(shipment);
    }

    /**
     * Receive a shipment: update inventory, create stock movements, publish events
     */
    public Shipment receiveShipment(UUID shipmentId, ReceiveShipmentRequestDTO requestDTO) {
        Shipment shipment = getShipmentById(shipmentId);

        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new InvalidShipmentStatusException("Shipment already delivered");
        }
        if (shipment.getStatus() == ShipmentStatus.CANCELLED) {
            throw new InvalidShipmentStatusException("Cannot receive a cancelled shipment");
        }

        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setActualDeliveryDate(requestDTO.getActualDeliveryDate());

        for (ReceiveShipmentRequestDTO.ItemReceiptDTO receipt : requestDTO.getItemReceipts()) {
            ShipmentItem shipmentItem = shipmentItemRepository.findById(receipt.getShipmentItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Shipment item not found: " + receipt.getShipmentItemId()));

            if (!shipmentItem.getShipment().getId().equals(shipmentId)) {
                throw new IllegalArgumentException("Shipment item does not belong to this shipment");
            }

            int receivedQuantity = receipt.getReceivedQuantity();
            shipmentItem.setReceivedQuantity(receivedQuantity);

            // Use destination location from request if provided, otherwise use existing one from shipment item
            LocationType destinationLocationType = receipt.getDestinationLocationType() != null 
                    ? receipt.getDestinationLocationType() 
                    : shipmentItem.getDestinationLocationType();
            UUID destinationLocationId = receipt.getDestinationLocationId() != null 
                    ? receipt.getDestinationLocationId() 
                    : shipmentItem.getDestinationLocationId();

            // Update shipment item with destination location if provided in request
            if (receipt.getDestinationLocationType() != null) {
                shipmentItem.setDestinationLocationType(receipt.getDestinationLocationType());
            }
            if (receipt.getDestinationLocationId() != null) {
                shipmentItem.setDestinationLocationId(receipt.getDestinationLocationId());
            }

            // Update inventory if we have a destination location and received quantity > 0
            if (receivedQuantity > 0 && destinationLocationId != null && destinationLocationType != null) {
                addToInventory(
                        destinationLocationType,
                        destinationLocationId,
                        shipmentItem.getItem(),
                        receivedQuantity,
                        requestDTO.getReceivedBy()
                );
            }
        }

        return shipmentRepository.save(shipment);
    }

    private void addToInventory(LocationType locationType, UUID locationId, Product product, int quantity, UUID actorId) {
        Object inventory = findOrCreateInventory(locationType, locationId, product);
        int currentQuantity = getInventoryQuantity(inventory);
        setInventoryQuantity(inventory, currentQuantity + quantity);
        UUID inventoryId = saveInventoryAndGetId(locationType, inventory);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("inventory_id", inventoryId.toString());
        metadata.put("shipment_receipt", true);

        // Validate actorId exists in users table, set to null if not found
        UUID validatedActorId = null;
        if (actorId != null) {
            // Use repository directly to avoid transaction rollback issues
            if (userRepository.findById(actorId).isPresent()) {
                validatedActorId = actorId;
            }
            // If user not found, validatedActorId remains null (actorId is nullable)
        }

        StockMovement movement = StockMovement.builder()
                .item(product)
                .locationType(locationType)
                .toLocationId(locationId)
                .quantityChange(quantity)
                .reason(StockMovementReason.RESTOCK)
                .actorId(validatedActorId)
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .build();

        StockMovement savedMovement = stockMovementRepository.save(movement);
        eventOutboxService.createStockMovementEvent(savedMovement);
    }

    private Object findOrCreateInventory(LocationType locationType, UUID locationId, Product product) {
        return switch (locationType) {
            case BOX_BIN -> boxBinInventoryRepository
                    .findByBoxBin_IdAndItem_Id(locationId, product.getId())
                    .orElseGet(() -> {
                        BoxBinInventory inv = new BoxBinInventory();
                        inv.setBoxBin(boxBinRepository.findById(locationId)
                                .orElseThrow(() -> new BoxBinNotFoundException("BoxBin not found with id: " + locationId)));
                        inv.setItem(product);
                        inv.setQuantity(0);
                        return inv;
                    });
            case SINGLE_CLAW_MACHINE -> singleClawMachineInventoryRepository
                    .findBySingleClawMachine_IdAndItem_Id(locationId, product.getId())
                    .orElseGet(() -> {
                        SingleClawMachineInventory inv = new SingleClawMachineInventory();
                        inv.setSingleClawMachine(singleClawMachineRepository.findById(locationId)
                                .orElseThrow(() -> new SingleClawMachineNotFoundException("SingleClawMachine not found with id: " + locationId)));
                        inv.setItem(product);
                        inv.setQuantity(0);
                        return inv;
                    });
            case DOUBLE_CLAW_MACHINE -> doubleClawMachineInventoryRepository
                    .findByDoubleClawMachine_IdAndItem_Id(locationId, product.getId())
                    .orElseGet(() -> {
                        DoubleClawMachineInventory inv = new DoubleClawMachineInventory();
                        inv.setDoubleClawMachine(doubleClawMachineRepository.findById(locationId)
                                .orElseThrow(() -> new DoubleClawMachineNotFoundException("DoubleClawMachine not found with id: " + locationId)));
                        inv.setItem(product);
                        inv.setQuantity(0);
                        return inv;
                    });
            case KEYCHAIN_MACHINE -> keychainMachineInventoryRepository
                    .findByKeychainMachine_IdAndItem_Id(locationId, product.getId())
                    .orElseGet(() -> {
                        KeychainMachineInventory inv = new KeychainMachineInventory();
                        inv.setKeychainMachine(keychainMachineRepository.findById(locationId)
                                .orElseThrow(() -> new KeychainMachineNotFoundException("KeychainMachine not found with id: " + locationId)));
                        inv.setItem(product);
                        inv.setQuantity(0);
                        return inv;
                    });
            case CABINET -> cabinetInventoryRepository
                    .findByCabinet_IdAndItem_Id(locationId, product.getId())
                    .orElseGet(() -> {
                        CabinetInventory inv = new CabinetInventory();
                        inv.setCabinet(cabinetRepository.findById(locationId)
                                .orElseThrow(() -> new CabinetNotFoundException("Cabinet not found with id: " + locationId)));
                        inv.setItem(product);
                        inv.setQuantity(0);
                        return inv;
                    });
            case RACK -> rackInventoryRepository
                    .findByRack_IdAndItem_Id(locationId, product.getId())
                    .orElseGet(() -> {
                        RackInventory inv = new RackInventory();
                        inv.setRack(rackRepository.findById(locationId)
                                .orElseThrow(() -> new RackNotFoundException("Rack not found with id: " + locationId)));
                        inv.setItem(product);
                        inv.setQuantity(0);
                        return inv;
                    });
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
            default -> throw new IllegalArgumentException("Unknown inventory type");
        }
    }

    private UUID saveInventoryAndGetId(LocationType locationType, Object inventory) {
        return switch (locationType) {
            case BOX_BIN -> boxBinInventoryRepository.save((BoxBinInventory) inventory).getId();
            case SINGLE_CLAW_MACHINE -> singleClawMachineInventoryRepository.save((SingleClawMachineInventory) inventory).getId();
            case DOUBLE_CLAW_MACHINE -> doubleClawMachineInventoryRepository.save((DoubleClawMachineInventory) inventory).getId();
            case KEYCHAIN_MACHINE -> keychainMachineInventoryRepository.save((KeychainMachineInventory) inventory).getId();
            case CABINET -> cabinetInventoryRepository.save((CabinetInventory) inventory).getId();
            case RACK -> rackInventoryRepository.save((RackInventory) inventory).getId();
        };
    }
}
