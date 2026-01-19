package com.mirai.inventoryservice.integration;

import com.mirai.inventoryservice.dtos.requests.*;
import com.mirai.inventoryservice.models.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class TestDataFactory {

    // Storage location requests
    public static BoxBinRequestDTO validBoxBinRequest() {
        return BoxBinRequestDTO.builder().boxBinCode("B1").build();
    }

    public static BoxBinRequestDTO boxBinWithCode(String code) {
        return BoxBinRequestDTO.builder().boxBinCode(code).build();
    }

    public static RackRequestDTO validRackRequest() {
        return RackRequestDTO.builder().rackCode("R1").build();
    }

    public static RackRequestDTO rackWithCode(String code) {
        return RackRequestDTO.builder().rackCode(code).build();
    }

    public static CabinetRequestDTO validCabinetRequest() {
        return CabinetRequestDTO.builder().cabinetCode("C1").build();
    }

    public static CabinetRequestDTO cabinetWithCode(String code) {
        return CabinetRequestDTO.builder().cabinetCode(code).build();
    }

    public static KeychainMachineRequestDTO validKeychainMachineRequest() {
        return KeychainMachineRequestDTO.builder().keychainMachineCode("M1").build();
    }

    public static KeychainMachineRequestDTO keychainMachineWithCode(String code) {
        return KeychainMachineRequestDTO.builder().keychainMachineCode(code).build();
    }

    public static SingleClawMachineRequestDTO validSingleClawMachineRequest() {
        return SingleClawMachineRequestDTO.builder().singleClawMachineCode("S1").build();
    }

    public static SingleClawMachineRequestDTO singleClawMachineWithCode(String code) {
        return SingleClawMachineRequestDTO.builder().singleClawMachineCode(code).build();
    }

    public static DoubleClawMachineRequestDTO validDoubleClawMachineRequest() {
        return DoubleClawMachineRequestDTO.builder().doubleClawMachineCode("D1").build();
    }

    public static DoubleClawMachineRequestDTO doubleClawMachineWithCode(String code) {
        return DoubleClawMachineRequestDTO.builder().doubleClawMachineCode(code).build();
    }

    // Product requests
    public static ProductRequestDTO validProductRequest() {
        return ProductRequestDTO.builder()
                .category(ProductCategory.PLUSHIE)
                .name("Test Plushie")
                .description("A test plushie for testing")
                .reorderPoint(10)
                .targetStockLevel(50)
                .leadTimeDays(7)
                .unitCost(new BigDecimal("19.99"))
                .build();
    }

    public static ProductRequestDTO productWithName(String name) {
        return ProductRequestDTO.builder()
                .category(ProductCategory.PLUSHIE)
                .name(name)
                .build();
    }

    public static ProductRequestDTO productWithSku(String sku) {
        return ProductRequestDTO.builder()
                .sku(sku)
                .category(ProductCategory.FIGURINE)
                .name("Product with SKU")
                .build();
    }

    public static ProductRequestDTO productWithCategory(ProductCategory category) {
        return ProductRequestDTO.builder()
                .category(category)
                .name("Test Product " + category.name())
                .build();
    }

    // User requests
    public static UserRequestDTO validUserRequest() {
        return UserRequestDTO.builder()
                .fullName("Test User")
                .email("test@example.com")
                .role(UserRole.EMPLOYEE)
                .build();
    }

    public static UserRequestDTO userWithEmail(String email) {
        return UserRequestDTO.builder()
                .fullName("Test User")
                .email(email)
                .role(UserRole.EMPLOYEE)
                .build();
    }

    public static UserRequestDTO userWithName(String name) {
        return UserRequestDTO.builder()
                .fullName(name)
                .email(name.toLowerCase().replace(" ", ".") + "@example.com")
                .role(UserRole.EMPLOYEE)
                .build();
    }

    // Inventory requests
    public static InventoryRequestDTO validInventoryRequest(UUID productId) {
        return InventoryRequestDTO.builder()
                .itemId(productId)
                .quantity(10)
                .build();
    }

    public static InventoryRequestDTO inventoryWithQuantity(UUID productId, int quantity) {
        return InventoryRequestDTO.builder()
                .itemId(productId)
                .quantity(quantity)
                .build();
    }

    // Shipment requests
    public static ShipmentRequestDTO validShipmentRequest(UUID productId) {
        return ShipmentRequestDTO.builder()
                .shipmentNumber("SHP-" + UUID.randomUUID().toString().substring(0, 8))
                .supplierName("Test Supplier")
                .status(ShipmentStatus.PENDING)
                .orderDate(LocalDate.now())
                .expectedDeliveryDate(LocalDate.now().plusDays(7))
                .totalCost(new BigDecimal("199.99"))
                .items(List.of(validShipmentItemRequest(productId)))
                .build();
    }

    public static ShipmentItemRequestDTO validShipmentItemRequest(UUID productId) {
        return ShipmentItemRequestDTO.builder()
                .itemId(productId)
                .orderedQuantity(10)
                .unitCost(new BigDecimal("19.99"))
                .build();
    }

    public static ShipmentRequestDTO shipmentWithStatus(UUID productId, ShipmentStatus status) {
        return ShipmentRequestDTO.builder()
                .shipmentNumber("SHP-" + UUID.randomUUID().toString().substring(0, 8))
                .supplierName("Test Supplier")
                .status(status)
                .orderDate(LocalDate.now())
                .items(List.of(validShipmentItemRequest(productId)))
                .build();
    }

    public static ReceiveShipmentRequestDTO validReceiveShipmentRequest(UUID shipmentItemId) {
        return ReceiveShipmentRequestDTO.builder()
                .actualDeliveryDate(LocalDate.now())
                .itemReceipts(List.of(
                        ReceiveShipmentRequestDTO.ItemReceiptDTO.builder()
                                .shipmentItemId(shipmentItemId)
                                .receivedQuantity(10)
                                .build()
                ))
                .build();
    }

    // Stock movement requests
    public static AdjustStockRequestDTO validRestockRequest(int quantity) {
        return AdjustStockRequestDTO.builder()
                .quantityChange(quantity)
                .reason(StockMovementReason.RESTOCK)
                .notes("Test restock")
                .build();
    }

    public static AdjustStockRequestDTO validSaleRequest(int quantity) {
        return AdjustStockRequestDTO.builder()
                .quantityChange(-quantity)
                .reason(StockMovementReason.SALE)
                .notes("Test sale")
                .build();
    }

    public static AdjustStockRequestDTO adjustRequest(int quantityChange, StockMovementReason reason) {
        return AdjustStockRequestDTO.builder()
                .quantityChange(quantityChange)
                .reason(reason)
                .build();
    }

    public static TransferInventoryRequestDTO validTransferRequest(
            LocationType sourceType, UUID sourceId,
            LocationType destType, UUID destId,
            int quantity) {
        return TransferInventoryRequestDTO.builder()
                .sourceLocationType(sourceType)
                .sourceInventoryId(sourceId)
                .destinationLocationType(destType)
                .destinationInventoryId(destId)
                .quantity(quantity)
                .notes("Test transfer")
                .build();
    }

    // Invalid DTOs for validation tests
    public static BoxBinRequestDTO invalidBoxBinRequest() {
        return BoxBinRequestDTO.builder().boxBinCode("INVALID").build();
    }

    public static BoxBinRequestDTO blankBoxBinRequest() {
        return BoxBinRequestDTO.builder().boxBinCode("").build();
    }

    public static ProductRequestDTO productWithNullCategory() {
        return ProductRequestDTO.builder()
                .category(null)
                .name("Test Product")
                .build();
    }

    public static ProductRequestDTO productWithBlankName() {
        return ProductRequestDTO.builder()
                .category(ProductCategory.PLUSHIE)
                .name("")
                .build();
    }

    public static UserRequestDTO userWithInvalidEmail() {
        return UserRequestDTO.builder()
                .fullName("Test User")
                .email("invalid-email")
                .role(UserRole.EMPLOYEE)
                .build();
    }

    public static InventoryRequestDTO inventoryWithNullProduct() {
        return InventoryRequestDTO.builder()
                .itemId(null)
                .quantity(10)
                .build();
    }

    public static InventoryRequestDTO inventoryWithNegativeQuantity(UUID productId) {
        return InventoryRequestDTO.builder()
                .itemId(productId)
                .quantity(-1)
                .build();
    }

    public static ShipmentRequestDTO shipmentWithNullStatus(UUID productId) {
        return ShipmentRequestDTO.builder()
                .shipmentNumber("SHP-003")
                .status(null)
                .orderDate(LocalDate.now())
                .items(List.of(validShipmentItemRequest(productId)))
                .build();
    }

    public static ShipmentRequestDTO shipmentWithNullOrderDate(UUID productId) {
        return ShipmentRequestDTO.builder()
                .shipmentNumber("SHP-004")
                .status(ShipmentStatus.PENDING)
                .orderDate(null)
                .items(List.of(validShipmentItemRequest(productId)))
                .build();
    }

    public static ShipmentRequestDTO shipmentWithEmptyItems() {
        return ShipmentRequestDTO.builder()
                .shipmentNumber("SHP-005")
                .status(ShipmentStatus.PENDING)
                .orderDate(LocalDate.now())
                .items(List.of())
                .build();
    }

    public static AdjustStockRequestDTO adjustWithNullQuantity() {
        return AdjustStockRequestDTO.builder()
                .quantityChange(null)
                .reason(StockMovementReason.ADJUSTMENT)
                .build();
    }

    public static AdjustStockRequestDTO adjustWithNullReason() {
        return AdjustStockRequestDTO.builder()
                .quantityChange(10)
                .reason(null)
                .build();
    }

    public static TransferInventoryRequestDTO transferWithNullSourceType(UUID sourceId, UUID destId) {
        return TransferInventoryRequestDTO.builder()
                .sourceLocationType(null)
                .sourceInventoryId(sourceId)
                .destinationLocationType(LocationType.BOX_BIN)
                .destinationInventoryId(destId)
                .quantity(5)
                .build();
    }

    public static TransferInventoryRequestDTO transferWithZeroQuantity(
            LocationType sourceType, UUID sourceId,
            LocationType destType, UUID destId) {
        return TransferInventoryRequestDTO.builder()
                .sourceLocationType(sourceType)
                .sourceInventoryId(sourceId)
                .destinationLocationType(destType)
                .destinationInventoryId(destId)
                .quantity(0)
                .build();
    }
}
