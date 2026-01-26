package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.ReceiveShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentItemRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentRequestDTO;
import com.mirai.inventoryservice.exceptions.InvalidShipmentStatusException;
import com.mirai.inventoryservice.exceptions.ProductNotFoundException;
import com.mirai.inventoryservice.exceptions.ShipmentNotFoundException;
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
    private final StockMovementRepository stockMovementRepository;
    private final EventOutboxService eventOutboxService;
    private final BoxBinInventoryRepository boxBinInventoryRepository;
    private final SingleClawMachineInventoryRepository singleClawMachineInventoryRepository;
    private final DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository;
    private final KeychainMachineInventoryRepository keychainMachineInventoryRepository;
    private final CabinetInventoryRepository cabinetInventoryRepository;
    private final RackInventoryRepository rackInventoryRepository;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            ProductService productService,
            UserService userService,
            StockMovementRepository stockMovementRepository,
            EventOutboxService eventOutboxService,
            BoxBinInventoryRepository boxBinInventoryRepository,
            SingleClawMachineInventoryRepository singleClawMachineInventoryRepository,
            DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository,
            KeychainMachineInventoryRepository keychainMachineInventoryRepository,
            CabinetInventoryRepository cabinetInventoryRepository,
            RackInventoryRepository rackInventoryRepository) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.productService = productService;
        this.userService = userService;
        this.stockMovementRepository = stockMovementRepository;
        this.eventOutboxService = eventOutboxService;
        this.boxBinInventoryRepository = boxBinInventoryRepository;
        this.singleClawMachineInventoryRepository = singleClawMachineInventoryRepository;
        this.doubleClawMachineInventoryRepository = doubleClawMachineInventoryRepository;
        this.keychainMachineInventoryRepository = keychainMachineInventoryRepository;
        this.cabinetInventoryRepository = cabinetInventoryRepository;
        this.rackInventoryRepository = rackInventoryRepository;
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

            if (receivedQuantity > 0 && shipmentItem.getDestinationLocationId() != null) {
                addToInventory(
                        shipmentItem.getDestinationLocationType(),
                        shipmentItem.getDestinationLocationId(),
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

        StockMovement movement = StockMovement.builder()
                .item(product)
                .locationType(locationType)
                .toLocationId(locationId)
                .quantityChange(quantity)
                .reason(StockMovementReason.RESTOCK)
                .actorId(actorId)
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
                        inv.setBoxBin(boxBinInventoryRepository.findById(locationId)
                                .map(BoxBinInventory::getBoxBin)
                                .orElseThrow(() -> new IllegalArgumentException("BoxBin not found")));
                        inv.setItem(product);
                        inv.setQuantity(0);
                        return inv;
                    });
            case SINGLE_CLAW_MACHINE -> singleClawMachineInventoryRepository
                    .findBySingleClawMachine_IdAndItem_Id(locationId, product.getId())
                    .orElseGet(() -> {
                        SingleClawMachineInventory inv = new SingleClawMachineInventory();
                        inv.setSingleClawMachine(singleClawMachineInventoryRepository.findById(locationId)
                                .map(SingleClawMachineInventory::getSingleClawMachine)
                                .orElseThrow(() -> new IllegalArgumentException("SingleClawMachine not found")));
                        inv.setItem(product);
                        inv.setQuantity(0);
                        return inv;
                    });
            case DOUBLE_CLAW_MACHINE -> doubleClawMachineInventoryRepository
                    .findByDoubleClawMachine_IdAndItem_Id(locationId, product.getId())
                    .orElseGet(() -> {
                        DoubleClawMachineInventory inv = new DoubleClawMachineInventory();
                        inv.setDoubleClawMachine(doubleClawMachineInventoryRepository.findById(locationId)
                                .map(DoubleClawMachineInventory::getDoubleClawMachine)
                                .orElseThrow(() -> new IllegalArgumentException("DoubleClawMachine not found")));
                        inv.setItem(product);
                        inv.setQuantity(0);
                        return inv;
                    });
            case KEYCHAIN_MACHINE -> keychainMachineInventoryRepository
                    .findByKeychainMachine_IdAndItem_Id(locationId, product.getId())
                    .orElseGet(() -> {
                        KeychainMachineInventory inv = new KeychainMachineInventory();
                        inv.setKeychainMachine(keychainMachineInventoryRepository.findById(locationId)
                                .map(KeychainMachineInventory::getKeychainMachine)
                                .orElseThrow(() -> new IllegalArgumentException("KeychainMachine not found")));
                        inv.setItem(product);
                        inv.setQuantity(0);
                        return inv;
                    });
            case CABINET -> cabinetInventoryRepository
                    .findByCabinet_IdAndItem_Id(locationId, product.getId())
                    .orElseGet(() -> {
                        CabinetInventory inv = new CabinetInventory();
                        inv.setCabinet(cabinetInventoryRepository.findById(locationId)
                                .map(CabinetInventory::getCabinet)
                                .orElseThrow(() -> new IllegalArgumentException("Cabinet not found")));
                        inv.setItem(product);
                        inv.setQuantity(0);
                        return inv;
                    });
            case RACK -> rackInventoryRepository
                    .findByRack_IdAndItem_Id(locationId, product.getId())
                    .orElseGet(() -> {
                        RackInventory inv = new RackInventory();
                        inv.setRack(rackInventoryRepository.findById(locationId)
                                .map(RackInventory::getRack)
                                .orElseThrow(() -> new IllegalArgumentException("Rack not found")));
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
