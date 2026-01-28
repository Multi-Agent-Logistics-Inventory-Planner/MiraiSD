package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
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

    private final Random random = new Random();

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
