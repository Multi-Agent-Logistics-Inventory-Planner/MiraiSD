package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.BoxBinInventory;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.models.storage.BoxBin;
import com.mirai.inventoryservice.repositories.BoxBinInventoryRepository;
import com.mirai.inventoryservice.repositories.BoxBinRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.ShipmentRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
@Validated
public class DevSeedController {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final BoxBinRepository boxBinRepository;
    private final BoxBinInventoryRepository boxBinInventoryRepository;
    private final ShipmentRepository shipmentRepository;

    private final Random random = new Random();

    @PostMapping("/seed/all")
    public ResponseEntity<Map<String, Object>> seedAll() {
        if (productRepository.count() > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Seed data already exists.",
                "hint", "Reset with: docker compose -f docker-compose.dev.yml down -v && docker compose -f docker-compose.dev.yml up -d"
            ));
        }

        // 1. Products (15)
        ProductCategory[] categories = ProductCategory.values();
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            ProductCategory category = categories[i % categories.length];
            BigDecimal unitCost = BigDecimal.valueOf(5 + random.nextInt(50))
                .add(BigDecimal.valueOf(random.nextInt(100)).divide(BigDecimal.valueOf(100)));
            products.add(Product.builder()
                .sku("DEV-" + String.format("%04d", i + 1))
                .name("Test Product " + (i + 1))
                .category(category)
                .description("Development test product for " + category.name())
                .unitCost(unitCost)
                .reorderPoint(5 + random.nextInt(10))
                .targetStockLevel(20 + random.nextInt(30))
                .leadTimeDays(3 + random.nextInt(7))
                .isActive(true)
                .build());
        }
        products = productRepository.saveAll(products);

        // 2. Box bins (5)
        List<BoxBin> boxBins = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            boxBins.add(BoxBin.builder()
                .boxBinCode("B" + String.format("%03d", i + 1))
                .build());
        }
        boxBins = boxBinRepository.saveAll(boxBins);

        // 3. Inventory records — mix of good / low / critical / out-of-stock
        List<BoxBinInventory> inventoryRecords = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            BoxBin bin = boxBins.get(i % boxBins.size());
            int reorderPoint = product.getReorderPoint() != null ? product.getReorderPoint() : 10;

            int quantity;
            if (i < 5) {
                // good: 15-40 units (~33%)
                quantity = 15 + random.nextInt(26);
            } else if (i < 9) {
                // low: reorderPoint+1 to 14 (~27%)
                int lowMin = reorderPoint + 1;
                int lowMax = Math.max(lowMin + 1, 14);
                quantity = lowMin + random.nextInt(lowMax - lowMin + 1);
            } else if (i < 12) {
                // critical: 1 to reorderPoint/2 (~20%)
                int critMax = Math.max(1, reorderPoint / 2);
                quantity = 1 + random.nextInt(critMax);
            } else {
                // out of stock (~20%)
                quantity = 0;
            }

            inventoryRecords.add(BoxBinInventory.builder()
                .boxBin(bin)
                .item(product)
                .quantity(quantity)
                .build());
        }
        boxBinInventoryRepository.saveAll(inventoryRecords);

        // 4. Shipments (8) spread across current month
        String[] suppliers = {"Mirai Wholesale", "Japan Arcade Supply Co.", "ACE Toys Ltd", "Pacific Toy Imports"};
        ShipmentStatus[] statuses = {
            ShipmentStatus.DELIVERED, ShipmentStatus.DELIVERED, ShipmentStatus.DELIVERED,
            ShipmentStatus.IN_TRANSIT, ShipmentStatus.IN_TRANSIT, ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.PENDING, ShipmentStatus.PENDING
        };
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        List<Shipment> shipments = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            LocalDate orderDate = monthStart.plusDays(i * 3);
            if (orderDate.isAfter(today)) {
                orderDate = today.minusDays(8 - i);
            }
            LocalDate expectedDelivery = orderDate.plusDays(5 + random.nextInt(5));
            LocalDate actualDelivery = statuses[i] == ShipmentStatus.DELIVERED
                ? orderDate.plusDays(3 + random.nextInt(5))
                : null;
            shipments.add(Shipment.builder()
                .shipmentNumber("DEV-SHIP-" + String.format("%03d", i + 1))
                .supplierName(suppliers[i % suppliers.length])
                .status(statuses[i])
                .orderDate(orderDate)
                .expectedDeliveryDate(expectedDelivery)
                .actualDeliveryDate(actualDelivery)
                .totalCost(BigDecimal.valueOf(100 + random.nextInt(900)))
                .build());
        }
        shipmentRepository.saveAll(shipments);

        // 5. Sales history (50-150 sales per product, 365 days back)
        List<StockMovement> movements = new ArrayList<>();
        for (Product product : products) {
            int salesCount = 50 + random.nextInt(100);
            for (int i = 0; i < salesCount; i++) {
                int daysAgo = random.nextInt(365);
                OffsetDateTime saleDate = OffsetDateTime.now(ZoneOffset.UTC)
                    .minusDays(daysAgo)
                    .plusHours(random.nextInt(12));
                int qty = 1 + random.nextInt(5);
                movements.add(StockMovement.builder()
                    .locationType(LocationType.BOX_BIN)
                    .item(product)
                    .quantityChange(-qty)
                    .previousQuantity(qty)
                    .currentQuantity(0)
                    .reason(StockMovementReason.SALE)
                    .at(saleDate)
                    .metadata(Map.of("source", "dev_seed"))
                    .build());
            }
        }
        stockMovementRepository.saveAll(movements);

        log.info("Seeded: {} products, {} box bins, {} inventory records, {} shipments, {} sales",
            products.size(), boxBins.size(), inventoryRecords.size(), shipments.size(), movements.size());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "productsCreated", products.size(),
            "boxBinsCreated", boxBins.size(),
            "inventoryRecordsCreated", inventoryRecords.size(),
            "shipmentsCreated", shipments.size(),
            "salesCreated", movements.size()
        ));
    }

    @PostMapping("/seed/sales")
    public ResponseEntity<Map<String, Object>> seedSalesData(
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int salesPerProduct,
            @RequestParam(defaultValue = "365") @Min(1) @Max(730) int daysBack) {

        List<Product> products = productRepository.findAll();

        if (products.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "No products found. Create some products first.",
                "hint", "POST /api/products to create products"
            ));
        }

        int totalSales = 0;
        List<StockMovement> movements = new ArrayList<>();

        for (Product product : products) {
            int salesCount = salesPerProduct / 2 + random.nextInt(salesPerProduct);

            for (int i = 0; i < salesCount; i++) {
                int daysAgo = random.nextInt(daysBack);
                int hoursOffset = random.nextInt(12);

                OffsetDateTime saleDate = OffsetDateTime.now(ZoneOffset.UTC)
                    .minusDays(daysAgo)
                    .plusHours(hoursOffset);

                int quantity = 1 + random.nextInt(5);

                StockMovement movement = StockMovement.builder()
                    .locationType(LocationType.BOX_BIN)
                    .item(product)
                    .quantityChange(-quantity)
                    .previousQuantity(quantity)
                    .currentQuantity(0)
                    .reason(StockMovementReason.SALE)
                    .at(saleDate)
                    .metadata(Map.of("source", "dev_seed"))
                    .build();

                movements.add(movement);
                totalSales++;
            }
        }

        stockMovementRepository.saveAll(movements);

        log.info("Seeded {} sales across {} products", totalSales, products.size());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "totalSales", totalSales,
            "productsAffected", products.size(),
            "dateRange", Map.of(
                "from", OffsetDateTime.now().minusDays(daysBack).toString(),
                "to", OffsetDateTime.now().toString()
            )
        ));
    }

    @PostMapping("/seed/products")
    public ResponseEntity<Map<String, Object>> seedProducts(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int count) {

        List<Product> products = new ArrayList<>();
        ProductCategory[] categories = ProductCategory.values();

        for (int i = 0; i < count; i++) {
            ProductCategory category = categories[random.nextInt(categories.length)];
            BigDecimal unitCost = BigDecimal.valueOf(5 + random.nextInt(50))
                .add(BigDecimal.valueOf(random.nextInt(100)).divide(BigDecimal.valueOf(100)));

            Product product = Product.builder()
                .sku("DEV-" + String.format("%04d", i + 1))
                .name("Test Product " + (i + 1))
                .category(category)
                .description("Development test product for " + category.name())
                .unitCost(unitCost)
                .reorderPoint(5 + random.nextInt(10))
                .targetStockLevel(20 + random.nextInt(30))
                .leadTimeDays(3 + random.nextInt(7))
                .isActive(true)
                .build();

            products.add(product);
        }

        productRepository.saveAll(products);

        log.info("Seeded {} test products", count);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "productsCreated", count
        ));
    }

    @DeleteMapping("/seed/sales")
    public ResponseEntity<Map<String, Object>> clearSeedSales() {
        List<StockMovement> seedMovements = stockMovementRepository.findAll().stream()
            .filter(m -> m.getMetadata() != null && "dev_seed".equals(m.getMetadata().get("source")))
            .toList();

        stockMovementRepository.deleteAll(seedMovements);

        log.info("Cleared {} seeded sales", seedMovements.size());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "deletedCount", seedMovements.size()
        ));
    }
}
