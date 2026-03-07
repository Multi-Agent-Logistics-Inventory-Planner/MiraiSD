package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.NotificationSeverity;
import com.mirai.inventoryservice.models.enums.NotificationType;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.models.inventory.BoxBinInventory;
import com.mirai.inventoryservice.models.review.Review;
import com.mirai.inventoryservice.models.review.ReviewDailyCount;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.models.storage.BoxBin;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.repositories.BoxBinInventoryRepository;
import com.mirai.inventoryservice.repositories.BoxBinRepository;
import com.mirai.inventoryservice.repositories.CategoryRepository;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import com.mirai.inventoryservice.repositories.NotificationRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.ReviewDailyCountRepository;
import com.mirai.inventoryservice.repositories.ReviewRepository;
import com.mirai.inventoryservice.repositories.ShipmentRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
@Validated
public class DevSeedController {

    // Seed data configuration constants
    private static final int SEED_DATA_DAYS_RANGE = 60;
    private static final int BUSINESS_HOURS_START = 8;
    private static final int BUSINESS_HOURS_DURATION = 12;
    private static final int MAX_ITEMS_PER_ACTION = 5;
    private static final int MAX_QUANTITY_PER_ITEM = 10;
    private static final int BASE_STOCK_QUANTITY = 10;
    private static final int STOCK_QUANTITY_RANGE = 20;
    private static final int PRODUCT_SUMMARY_DISPLAY_LIMIT = 3;
    private static final int NOTES_INTERVAL = 5;
    private static final String DEV_SEED_AUDIT_SOURCE = "dev_seed_audit";

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditLogRepository auditLogRepository;
    private final BoxBinRepository boxBinRepository;
    private final BoxBinInventoryRepository boxBinInventoryRepository;
    private final ShipmentRepository shipmentRepository;
    private final CategoryRepository categoryRepository;
    private final ForecastPredictionRepository forecastPredictionRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewDailyCountRepository reviewDailyCountRepository;

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
        List<Category> categories = categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();
        if (categories.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "No categories found. Run the database migration first."
            ));
        }
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            Category category = categories.get(i % categories.size());
            BigDecimal unitCost = BigDecimal.valueOf(5 + random.nextInt(50))
                .add(BigDecimal.valueOf(random.nextInt(100)).divide(BigDecimal.valueOf(100)));
            products.add(Product.builder()
                .sku("DEV-" + String.format("%04d", i + 1))
                .name("Test Product " + (i + 1))
                .category(category)
                .description("Development test product for " + category.getName())
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

        List<Category> categories = categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();
        if (categories.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "No categories found. Run the database migration first."
            ));
        }

        List<Product> products = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Category category = categories.get(random.nextInt(categories.size()));
            BigDecimal unitCost = BigDecimal.valueOf(5 + random.nextInt(50))
                .add(BigDecimal.valueOf(random.nextInt(100)).divide(BigDecimal.valueOf(100)));

            Product product = Product.builder()
                .sku("DEV-" + String.format("%04d", i + 1))
                .name("Test Product " + (i + 1))
                .category(category)
                .description("Development test product for " + category.getName())
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

    /**
     * Seed forecast predictions for all products with distribution across risk bands.
     * Risk bands: Critical (<=3d), Warning (4-7d), Healthy (8-30d), Safe (31-60d), Overstocked (>60d)
     */
    @PostMapping("/seed/forecasts")
    public ResponseEntity<Map<String, Object>> seedForecasts() {
        List<Product> products = productRepository.findAll();

        if (products.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "No products found. Run /api/dev/seed/all first."
            ));
        }

        // Get current inventory quantities
        Map<UUID, Integer> inventoryByProduct = new HashMap<>();
        boxBinInventoryRepository.findAll().forEach(inv ->
            inventoryByProduct.merge(inv.getItem().getId(), inv.getQuantity(), Integer::sum)
        );

        List<ForecastPrediction> forecasts = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int productIndex = 0;

        // Distribution: 10% critical-urgent, 10% critical, 20% warning, 30% healthy, 20% safe, 10% overstocked
        for (Product product : products) {
            int currentStock = inventoryByProduct.getOrDefault(product.getId(), 0);
            int leadTimeDays = product.getLeadTimeDays() != null ? product.getLeadTimeDays() : 7;

            // Determine risk band based on product index for even distribution
            String riskBand;
            double daysToStockout;
            int mod = productIndex % 10;

            if (mod == 0) {
                // Critical urgent (0-1 days) - 10%
                riskBand = "critical_urgent";
                daysToStockout = 0.5 + random.nextDouble();
            } else if (mod == 1) {
                // Critical (2-3 days) - 10%
                riskBand = "critical";
                daysToStockout = 2 + random.nextDouble() * 1.5;
            } else if (mod <= 3) {
                // Warning (4-7 days) - 20%
                riskBand = "warning";
                daysToStockout = 4 + random.nextDouble() * 3;
            } else if (mod <= 6) {
                // Healthy (8-30 days) - 30%
                riskBand = "healthy";
                daysToStockout = 8 + random.nextDouble() * 22;
            } else if (mod <= 8) {
                // Safe (31-60 days) - 20%
                riskBand = "safe";
                daysToStockout = 31 + random.nextDouble() * 29;
            } else {
                // Overstocked (>60 days) - 10%
                riskBand = "overstocked";
                daysToStockout = 61 + random.nextDouble() * 120;
            }

            // Calculate daily consumption rate
            double avgDailyDelta = currentStock > 0 && daysToStockout > 0
                ? -1 * (currentStock / daysToStockout)
                : -1 * (2 + random.nextDouble() * 8);

            // Calculate reorder quantity
            int reorderQty = riskBand.equals("overstocked")
                ? 0
                : Math.max(10, (int) Math.ceil(Math.abs(avgDailyDelta) * leadTimeDays * 1.5));

            // Calculate order date
            LocalDate orderDate = daysToStockout <= leadTimeDays
                ? LocalDate.now()
                : LocalDate.now().plusDays((long) (daysToStockout - leadTimeDays));

            // Confidence varies by risk band (more sales data = higher confidence)
            double confidence = switch (riskBand) {
                case "critical_urgent", "critical" -> 0.85 + random.nextDouble() * 0.10;
                case "warning" -> 0.75 + random.nextDouble() * 0.15;
                case "healthy" -> 0.70 + random.nextDouble() * 0.15;
                case "safe" -> 0.65 + random.nextDouble() * 0.15;
                default -> 0.50 + random.nextDouble() * 0.20;
            };

            // Build features map
            Map<String, Object> features = Map.of(
                "ma7", Math.abs(avgDailyDelta) * (0.9 + random.nextDouble() * 0.2),
                "ma14", Math.abs(avgDailyDelta) * (0.85 + random.nextDouble() * 0.3),
                "std14", Math.abs(avgDailyDelta) * 0.3 * random.nextDouble(),
                "risk_band", riskBand,
                "seed_generated", true
            );

            ForecastPrediction forecast = ForecastPrediction.builder()
                .itemId(product.getId())
                .horizonDays(30)
                .avgDailyDelta(BigDecimal.valueOf(avgDailyDelta).setScale(2, java.math.RoundingMode.HALF_UP))
                .daysToStockout(BigDecimal.valueOf(daysToStockout).setScale(1, java.math.RoundingMode.HALF_UP))
                .suggestedReorderQty(reorderQty)
                .suggestedOrderDate(orderDate)
                .confidence(BigDecimal.valueOf(confidence).setScale(2, java.math.RoundingMode.HALF_UP))
                .features(features)
                .computedAt(now)
                .build();

            forecasts.add(forecast);
            productIndex++;
        }

        forecastPredictionRepository.saveAll(forecasts);

        // Count by risk band for response
        Map<String, Long> distribution = new HashMap<>();
        forecasts.forEach(f -> {
            String band = (String) f.getFeatures().get("risk_band");
            distribution.merge(band, 1L, Long::sum);
        });

        log.info("Seeded {} forecast predictions across risk bands: {}", forecasts.size(), distribution);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "forecastsCreated", forecasts.size(),
            "distribution", distribution
        ));
    }

    /**
     * Seed notifications with various types and severities for dashboard visualization.
     */
    @PostMapping("/seed/notifications")
    public ResponseEntity<Map<String, Object>> seedNotifications() {
        List<Product> products = productRepository.findAll();

        if (products.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "No products found. Run /api/dev/seed/all first."
            ));
        }

        List<Notification> notifications = new ArrayList<>();
        int limit = Math.min(30, products.size());

        for (int i = 0; i < limit; i++) {
            Product product = products.get(i);
            NotificationType type;
            NotificationSeverity severity;
            String message;

            // Vary notification types based on index
            switch (i % 6) {
                case 0 -> {
                    type = NotificationType.LOW_STOCK;
                    severity = NotificationSeverity.WARNING;
                    message = String.format("Low stock alert: %s (%s) is running low", product.getName(), product.getSku());
                }
                case 1 -> {
                    type = NotificationType.OUT_OF_STOCK;
                    severity = NotificationSeverity.CRITICAL;
                    message = String.format("Out of stock: %s (%s) has no inventory", product.getName(), product.getSku());
                }
                case 2 -> {
                    type = NotificationType.REORDER_SUGGESTION;
                    severity = NotificationSeverity.INFO;
                    message = String.format("Reorder suggested: Consider ordering %s (%s)", product.getName(), product.getSku());
                }
                case 3 -> {
                    type = NotificationType.LOW_STOCK;
                    severity = NotificationSeverity.CRITICAL;
                    message = String.format("Critical stock level: %s (%s) needs attention", product.getName(), product.getSku());
                }
                case 4 -> {
                    type = NotificationType.SYSTEM_ALERT;
                    severity = NotificationSeverity.INFO;
                    message = String.format("Stock update: %s (%s) inventory adjusted", product.getName(), product.getSku());
                }
                default -> {
                    type = NotificationType.UNASSIGNED_ITEM;
                    severity = NotificationSeverity.WARNING;
                    message = String.format("Unassigned: %s (%s) needs location", product.getName(), product.getSku());
                }
            }

            // Some notifications are resolved
            OffsetDateTime resolvedAt = (i % 3 == 0)
                ? OffsetDateTime.now(ZoneOffset.UTC).minusDays(random.nextInt(7))
                : null;

            Notification notification = Notification.builder()
                .type(type)
                .severity(severity)
                .message(message)
                .itemId(product.getId())
                .via(List.of("DASHBOARD"))
                .metadata(Map.of(
                    "seed_generated", true,
                    "product_sku", product.getSku()
                ))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(random.nextInt(14)))
                .deliveredAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(random.nextInt(14)))
                .resolvedAt(resolvedAt)
                .build();

            notifications.add(notification);
        }

        // Add system notifications
        notifications.add(Notification.builder()
            .type(NotificationType.SYSTEM_ALERT)
            .severity(NotificationSeverity.INFO)
            .message("Weekly inventory report generated successfully")
            .via(List.of("DASHBOARD"))
            .metadata(Map.of("seed_generated", true))
            .createdAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
            .deliveredAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
            .build());

        notifications.add(Notification.builder()
            .type(NotificationType.SYSTEM_ALERT)
            .severity(NotificationSeverity.INFO)
            .message("Forecasting service completed daily predictions")
            .via(List.of("DASHBOARD"))
            .metadata(Map.of("seed_generated", true))
            .createdAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2))
            .deliveredAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2))
            .build());

        notificationRepository.saveAll(notifications);

        long active = notifications.stream().filter(n -> n.getResolvedAt() == null).count();
        long resolved = notifications.size() - active;

        log.info("Seeded {} notifications ({} active, {} resolved)", notifications.size(), active, resolved);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "notificationsCreated", notifications.size(),
            "active", active,
            "resolved", resolved
        ));
    }

    @DeleteMapping("/seed/forecasts")
    public ResponseEntity<Map<String, Object>> clearSeedForecasts() {
        List<ForecastPrediction> seedForecasts = forecastPredictionRepository.findAll().stream()
            .filter(f -> f.getFeatures() != null && Boolean.TRUE.equals(f.getFeatures().get("seed_generated")))
            .toList();

        forecastPredictionRepository.deleteAll(seedForecasts);

        log.info("Cleared {} seeded forecasts", seedForecasts.size());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "deletedCount", seedForecasts.size()
        ));
    }

    @DeleteMapping("/seed/notifications")
    public ResponseEntity<Map<String, Object>> clearSeedNotifications() {
        List<Notification> seedNotifications = notificationRepository.findAll().stream()
            .filter(n -> n.getMetadata() != null && Boolean.TRUE.equals(n.getMetadata().get("seed_generated")))
            .toList();

        notificationRepository.deleteAll(seedNotifications);

        log.info("Cleared {} seeded notifications", seedNotifications.size());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "deletedCount", seedNotifications.size()
        ));
    }

    /**
     * Seed review data for the reviews leaderboard page.
     * Creates test users if needed, enables review tracking, and generates
     * daily review counts and individual reviews spanning 4 months.
     */
    @PostMapping("/seed/reviews")
    public ResponseEntity<Map<String, Object>> seedReviews() {
        // Sample reviewer names for generated reviews
        String[] reviewerNames = {
            "John D.", "Sarah M.", "Mike R.", "Emily K.", "David L.",
            "Jessica P.", "Chris W.", "Amanda H.", "Brian T.", "Nicole S."
        };

        // Sample review texts
        String[] reviewTexts = {
            "Great service! Very helpful staff.",
            "Amazing experience, will definitely come back!",
            "The employee was so patient and knowledgeable.",
            "Best arcade service I've ever received.",
            "Super friendly and accommodating.",
            "Went above and beyond to help us.",
            "Made our visit memorable!",
            "Exceptional customer service.",
            "Very professional and courteous.",
            "Highly recommend this place!"
        };

        // Employee data for review tracking
        String[][] employeeData = {
            {"AJ", "aj@mirai.test", "AJ"},
            {"Angelina", "angelina@mirai.test", "Angelina"},
            {"Averey", "averey@mirai.test", "Averey", "Avery"},
            {"Christine", "christine@mirai.test", "Christine", "Christina", "Kristine"},
            {"Emma", "emma@mirai.test", "Emma", "Ema"},
            {"Grace", "grace@mirai.test", "Grace"},
            {"Lucas", "lucas@mirai.test", "Lucas"},
            {"Matthew", "matthew@mirai.test", "Matthew", "Mathew", "Matt"},
            {"Mina", "mina@mirai.test", "Mina"},
            {"Victoria", "victoria@mirai.test", "Victoria"}
        };

        List<User> trackedUsers = new ArrayList<>();

        // Create or update users for review tracking
        for (String[] emp : employeeData) {
            String name = emp[0];
            String email = emp[1];
            List<String> variants = new ArrayList<>();
            for (int i = 2; i < emp.length; i++) {
                variants.add(emp[i]);
            }

            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                user = User.builder()
                    .fullName(name)
                    .email(email)
                    .role(UserRole.EMPLOYEE)
                    .canonicalName(name)
                    .nameVariants(variants)
                    .isReviewTracked(true)
                    .build();
            } else {
                user.setCanonicalName(name);
                user.setNameVariants(variants);
                user.setIsReviewTracked(true);
            }
            trackedUsers.add(userRepository.save(user));
        }

        // Generate review data spanning 4 months
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(4).withDayOfMonth(1);
        int totalDailyCounts = 0;
        int totalReviews = 0;

        List<ReviewDailyCount> dailyCounts = new ArrayList<>();
        List<Review> reviews = new ArrayList<>();

        for (User user : trackedUsers) {
            // Vary the activity level per user (some are more active)
            int baseReviewsPerDay = 1 + random.nextInt(4);
            double activityMultiplier = 0.5 + random.nextDouble();

            LocalDate currentDate = startDate;
            while (!currentDate.isAfter(today)) {
                // Skip some days randomly (weekends, days off)
                if (random.nextDouble() < 0.3) {
                    currentDate = currentDate.plusDays(1);
                    continue;
                }

                // Calculate reviews for this day
                int reviewCount = (int) Math.max(1,
                    Math.round(baseReviewsPerDay * activityMultiplier * (0.5 + random.nextDouble())));

                // Create daily count
                ReviewDailyCount dailyCount = ReviewDailyCount.builder()
                    .user(user)
                    .date(currentDate)
                    .reviewCount(reviewCount)
                    .build();
                dailyCounts.add(dailyCount);
                totalDailyCounts++;

                // Create individual reviews for this day
                for (int i = 0; i < reviewCount; i++) {
                    String externalId = String.format("seed-%s-%s-%d",
                        user.getId().toString().substring(0, 8),
                        currentDate.toString(),
                        i);

                    Review review = Review.builder()
                        .externalId(externalId)
                        .user(user)
                        .reviewDate(currentDate)
                        .reviewText(reviewTexts[random.nextInt(reviewTexts.length)])
                        .rating(3 + random.nextInt(3)) // 3-5 stars
                        .reviewerName(reviewerNames[random.nextInt(reviewerNames.length)])
                        .build();
                    reviews.add(review);
                    totalReviews++;
                }

                currentDate = currentDate.plusDays(1);
            }
        }

        reviewDailyCountRepository.saveAll(dailyCounts);
        reviewRepository.saveAll(reviews);

        // Calculate summary stats
        Map<String, Integer> userReviewCounts = new HashMap<>();
        for (ReviewDailyCount dc : dailyCounts) {
            String userName = dc.getUser().getFullName();
            userReviewCounts.merge(userName, dc.getReviewCount(), Integer::sum);
        }

        log.info("Seeded review data: {} users, {} daily counts, {} reviews",
            trackedUsers.size(), totalDailyCounts, totalReviews);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "usersCreated", trackedUsers.size(),
            "dailyCountsCreated", totalDailyCounts,
            "reviewsCreated", totalReviews,
            "dateRange", Map.of(
                "from", startDate.toString(),
                "to", today.toString()
            ),
            "userSummary", userReviewCounts
        ));
    }

    @DeleteMapping("/seed/reviews")
    public ResponseEntity<Map<String, Object>> clearSeedReviews() {
        // Delete reviews with seed external IDs
        List<Review> seedReviews = reviewRepository.findAll().stream()
            .filter(r -> r.getExternalId() != null && r.getExternalId().startsWith("seed-"))
            .toList();
        reviewRepository.deleteAll(seedReviews);

        // Delete daily counts for seed users (users with @mirai.test email)
        List<User> seedUsers = userRepository.findAll().stream()
            .filter(u -> u.getEmail() != null && u.getEmail().endsWith("@mirai.test"))
            .toList();

        int dailyCountsDeleted = 0;
        for (User user : seedUsers) {
            List<ReviewDailyCount> userCounts = reviewDailyCountRepository.findAll().stream()
                .filter(dc -> user.equals(dc.getUser()))
                .toList();
            reviewDailyCountRepository.deleteAll(userCounts);
            dailyCountsDeleted += userCounts.size();

            // Reset review tracking on seed users
            user.setIsReviewTracked(false);
            userRepository.save(user);
        }

        log.info("Cleared seed reviews: {} reviews, {} daily counts", seedReviews.size(), dailyCountsDeleted);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "reviewsDeleted", seedReviews.size(),
            "dailyCountsDeleted", dailyCountsDeleted,
            "usersReset", seedUsers.size()
        ));
    }

    /**
     * Seed audit logs with associated stock movements for the audit log page.
     * Creates realistic grouped operations spanning the last 60 days.
     */
    @PostMapping("/seed/audit-logs")
    public ResponseEntity<Map<String, Object>> seedAuditLogs(
            @RequestParam(defaultValue = "50") @Min(10) @Max(200) int count) {

        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "No products found. Run /api/dev/seed/all first."
            ));
        }

        List<BoxBin> boxBins = boxBinRepository.findAll();
        if (boxBins.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "No box bins found. Run /api/dev/seed/all first."
            ));
        }

        // Actor names for realistic data
        String[] actorNames = {
            "John Smith", "Sarah Johnson", "Mike Chen", "Emily Davis",
            "David Wilson", "Jessica Brown", "Chris Lee", "Amanda Garcia"
        };

        // Reason distribution weights
        StockMovementReason[] reasons = {
            StockMovementReason.TRANSFER, StockMovementReason.TRANSFER,
            StockMovementReason.RESTOCK, StockMovementReason.RESTOCK,
            StockMovementReason.ADJUSTMENT,
            StockMovementReason.SALE, StockMovementReason.SALE, StockMovementReason.SALE,
            StockMovementReason.DAMAGE,
            StockMovementReason.RETURN
        };

        List<AuditLog> auditLogs = new ArrayList<>();
        List<StockMovement> allMovements = new ArrayList<>();
        Map<String, Integer> reasonCounts = new HashMap<>();

        for (int i = 0; i < count; i++) {
            // Random timestamp within configured days range during business hours
            int daysAgo = random.nextInt(SEED_DATA_DAYS_RANGE);
            int hoursOffset = random.nextInt(BUSINESS_HOURS_DURATION) + BUSINESS_HOURS_START;
            OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                .minusDays(daysAgo)
                .withHour(hoursOffset)
                .withMinute(random.nextInt(60));

            // Select reason and actor
            StockMovementReason reason = reasons[random.nextInt(reasons.length)];
            String actorName = actorNames[random.nextInt(actorNames.length)];
            UUID actorId = UUID.nameUUIDFromBytes(actorName.getBytes());

            // Determine number of items in this action
            int itemCount = 1 + random.nextInt(MAX_ITEMS_PER_ACTION);
            int totalQuantity = 0;

            // Select random products for this action
            List<Product> selectedProducts = new ArrayList<>();
            for (int j = 0; j < itemCount && j < products.size(); j++) {
                Product product = products.get(random.nextInt(products.size()));
                if (!selectedProducts.contains(product)) {
                    selectedProducts.add(product);
                }
            }
            itemCount = selectedProducts.size();

            // Build product summary
            StringBuilder summary = new StringBuilder();
            for (int j = 0; j < Math.min(PRODUCT_SUMMARY_DISPLAY_LIMIT, selectedProducts.size()); j++) {
                if (j > 0) summary.append(", ");
                summary.append(selectedProducts.get(j).getName());
            }
            if (selectedProducts.size() > PRODUCT_SUMMARY_DISPLAY_LIMIT) {
                summary.append(" +").append(selectedProducts.size() - PRODUCT_SUMMARY_DISPLAY_LIMIT).append(" more");
            }

            // Select locations based on reason
            BoxBin fromBin = boxBins.get(random.nextInt(boxBins.size()));
            BoxBin toBin = boxBins.get(random.nextInt(boxBins.size()));
            while (toBin.equals(fromBin) && boxBins.size() > 1) {
                toBin = boxBins.get(random.nextInt(boxBins.size()));
            }

            UUID fromLocationId = null;
            UUID toLocationId = null;
            String fromLocationCode = null;
            String toLocationCode = null;

            switch (reason) {
                case TRANSFER -> {
                    fromLocationId = fromBin.getId();
                    toLocationId = toBin.getId();
                    fromLocationCode = fromBin.getBoxBinCode();
                    toLocationCode = toBin.getBoxBinCode();
                }
                case RESTOCK, INITIAL_STOCK -> {
                    toLocationId = toBin.getId();
                    toLocationCode = toBin.getBoxBinCode();
                }
                case SALE, DAMAGE, REMOVED -> {
                    fromLocationId = fromBin.getId();
                    fromLocationCode = fromBin.getBoxBinCode();
                }
                case ADJUSTMENT, RETURN -> {
                    toLocationId = toBin.getId();
                    toLocationCode = toBin.getBoxBinCode();
                }
            }

            // Create the audit log entry
            AuditLog auditLog = AuditLog.builder()
                .actorId(actorId)
                .actorName(actorName)
                .reason(reason)
                .primaryFromLocationId(fromLocationId)
                .primaryToLocationId(toLocationId)
                .primaryFromLocationCode(fromLocationCode)
                .primaryToLocationCode(toLocationCode)
                .itemCount(itemCount)
                .productSummary(summary.toString())
                .notes(i % NOTES_INTERVAL == 0 ? "Seed generated audit log entry" : null)
                .createdAt(timestamp)
                .build();

            // Create stock movements for each product
            List<StockMovement> movements = new ArrayList<>();
            for (Product product : selectedProducts) {
                int qty = 1 + random.nextInt(MAX_QUANTITY_PER_ITEM);
                totalQuantity += qty;

                int quantityChange = switch (reason) {
                    case SALE, DAMAGE, REMOVED -> -qty;
                    case RESTOCK, RETURN, INITIAL_STOCK, SHIPMENT_RECEIPT -> qty;
                    case TRANSFER, ADJUSTMENT -> random.nextBoolean() ? qty : -qty;
                    case DISPLAY_SET, DISPLAY_REMOVED, DISPLAY_SWAP -> 0;
                };

                int previousQty = BASE_STOCK_QUANTITY + random.nextInt(STOCK_QUANTITY_RANGE);
                StockMovement movement = StockMovement.builder()
                    .auditLog(auditLog)
                    .locationType(LocationType.BOX_BIN)
                    .item(product)
                    .fromLocationId(fromLocationId)
                    .toLocationId(toLocationId)
                    .quantityChange(quantityChange)
                    .previousQuantity(previousQty)
                    .currentQuantity(Math.max(0, previousQty + quantityChange))
                    .reason(reason)
                    .actorId(actorId)
                    .at(timestamp)
                    .metadata(Map.of("source", DEV_SEED_AUDIT_SOURCE))
                    .build();

                movements.add(movement);
            }

            auditLog.setTotalQuantityMoved(totalQuantity);
            auditLog.setMovements(movements);

            auditLogs.add(auditLog);
            allMovements.addAll(movements);
            reasonCounts.merge(reason.name(), 1, Integer::sum);
        }

        // Save audit logs first (cascading doesn't work here due to bidirectional)
        auditLogRepository.saveAll(auditLogs);
        stockMovementRepository.saveAll(allMovements);

        log.info("Seeded {} audit logs with {} stock movements", auditLogs.size(), allMovements.size());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "auditLogsCreated", auditLogs.size(),
            "stockMovementsCreated", allMovements.size(),
            "reasonDistribution", reasonCounts
        ));
    }

    @DeleteMapping("/seed/audit-logs")
    public ResponseEntity<Map<String, Object>> clearSeedAuditLogs() {
        // Find all stock movements with dev_seed_audit source using database-level filtering
        List<StockMovement> seedMovements = stockMovementRepository.findByMetadataSource(DEV_SEED_AUDIT_SOURCE);

        // Collect unique audit log IDs
        List<UUID> auditLogIds = seedMovements.stream()
            .filter(m -> m.getAuditLog() != null)
            .map(m -> m.getAuditLog().getId())
            .distinct()
            .toList();

        // Delete stock movements first (due to FK constraint)
        stockMovementRepository.deleteAll(seedMovements);

        // Delete associated audit logs
        int auditLogsDeleted = 0;
        for (UUID auditLogId : auditLogIds) {
            auditLogRepository.deleteById(auditLogId);
            auditLogsDeleted++;
        }

        log.info("Cleared {} seeded stock movements and {} audit logs", seedMovements.size(), auditLogsDeleted);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "stockMovementsDeleted", seedMovements.size(),
            "auditLogsDeleted", auditLogsDeleted
        ));
    }
}
