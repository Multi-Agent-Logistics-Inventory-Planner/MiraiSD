package com.mirai.inventoryservice.integration;

import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.Site;
import com.mirai.inventoryservice.models.audit.EventOutbox;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.models.storage.StorageLocation;
import com.mirai.inventoryservice.repositories.CategoryRepository;
import com.mirai.inventoryservice.repositories.EventDeadLetterRepository;
import com.mirai.inventoryservice.repositories.EventOutboxRepository;
import com.mirai.inventoryservice.repositories.LocationInventoryRepository;
import com.mirai.inventoryservice.repositories.LocationRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.SiteRepository;
import com.mirai.inventoryservice.repositories.StorageLocationRepository;
import com.mirai.inventoryservice.services.EventOutboxService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the inventory adjustment -> outbox -> Kafka publish flow.
 *
 * Verifies:
 * 1. POST /adjust creates an EventOutbox record with correct payload
 * 2. publishPendingEvents() sends the event to Kafka
 * 3. The outbox record is marked as published
 * 4. The Kafka message contains all expected payload fields
 */
class AdjustToKafkaIT extends BaseKafkaIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private StorageLocationRepository storageLocationRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationInventoryRepository locationInventoryRepository;

    @Autowired
    private EventOutboxRepository eventOutboxRepository;

    @Autowired
    private EventDeadLetterRepository eventDeadLetterRepository;

    @Autowired
    private EventOutboxService eventOutboxService;

    private LocationInventory testInventory;
    private Product testProduct;

    @BeforeEach
    void seedTestData() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);

        Category category = new Category();
        category.setName("Test Category " + suffix);
        category = categoryRepository.save(category);

        testProduct = new Product();
        testProduct.setName("Test Product");
        testProduct.setSku("TST-INT-" + suffix);
        testProduct.setCategory(category);
        testProduct.setQuantity(50);
        testProduct.setReorderPoint(10);
        testProduct = productRepository.save(testProduct);

        Site site = siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder()
                        .code("MAIN")
                        .name("Main Warehouse")
                        .build()));

        StorageLocation boxBinsStorage = storageLocationRepository
                .findByCodeAndSite_Code("BOX_BINS", "MAIN")
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(site)
                        .code("BOX_BINS")
                        .name("Box Bins")
                        .hasDisplay(false)
                        .isDisplayOnly(false)
                        .displayOrder(1)
                        .build()));

        Location location = locationRepository.save(Location.builder()
                .storageLocation(boxBinsStorage)
                .locationCode("B99-" + suffix)
                .build());

        testInventory = locationInventoryRepository.save(LocationInventory.builder()
                .location(location)
                .site(site)
                .product(testProduct)
                .quantity(20)
                .build());
    }

    @AfterEach
    void cleanup() {
        eventOutboxRepository.deleteAll();
        eventDeadLetterRepository.deleteAll();
        locationInventoryRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /adjust creates outbox record with correct payload fields")
    void adjustCreatesOutboxRecord() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "quantityChange", -3,
                "reason", "SALE",
                "actorId", UUID.randomUUID().toString()
        ));

        mockMvc.perform(post("/api/stock-movements/BOX_BIN/{inventoryId}/adjust", testInventory.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        // Verify outbox record was created
        List<EventOutbox> outboxEvents = eventOutboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();
        assertThat(outboxEvents).hasSize(1);

        EventOutbox outboxEvent = outboxEvents.get(0);
        assertThat(outboxEvent.getTopic()).isEqualTo("inventory-changes");
        assertThat(outboxEvent.getEventType()).isEqualTo("CREATED");
        assertThat(outboxEvent.getEntityType()).isEqualTo("stock_movement");
        assertThat(outboxEvent.getPublishedAt()).isNull();

        // Verify payload completeness
        Map<String, Object> payload = outboxEvent.getPayload();
        assertThat(payload).containsKey("product_id");
        assertThat(payload).containsKey("product_name");
        assertThat(payload).containsKey("sku");
        assertThat(payload).containsKey("item_id");
        assertThat(payload).containsKey("quantity_change");
        assertThat(payload).containsKey("reason");
        assertThat(payload).containsKey("at");
        assertThat(payload).containsKey("from_location_code");
        assertThat(payload).containsKey("to_location_code");
        assertThat(payload).containsKey("previous_location_qty");
        assertThat(payload).containsKey("current_location_qty");
        assertThat(payload).containsKey("previous_total_qty");
        assertThat(payload).containsKey("current_total_qty");
        assertThat(payload).containsKey("reorder_point");
        assertThat(payload).containsKey("actor_id");
        assertThat(payload).containsKey("stock_movement_id");

        // Verify payload values
        assertThat(payload.get("product_id")).isEqualTo(testProduct.getId().toString());
        assertThat(payload.get("product_name")).isEqualTo("Test Product");
        assertThat(payload.get("sku")).isEqualTo(testProduct.getSku());
        assertThat(payload.get("quantity_change")).isEqualTo(-3);
        assertThat(payload.get("reason")).isEqualTo("sale");
        assertThat(payload.get("reorder_point")).isEqualTo(10);
    }

    @Test
    @DisplayName("publishPendingEvents sends message to Kafka and marks outbox as published")
    void outboxPublishesToKafka() throws Exception {
        // First create an adjustment to generate an outbox event
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "quantityChange", -2,
                "reason", "SALE"
        ));

        mockMvc.perform(post("/api/stock-movements/BOX_BIN/{inventoryId}/adjust", testInventory.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        // Verify outbox has unpublished event
        List<EventOutbox> beforePublish = eventOutboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();
        assertThat(beforePublish).hasSize(1);
        UUID outboxEventId = beforePublish.get(0).getId();

        // Manually trigger the outbox publisher (normally runs on schedule)
        eventOutboxService.publishPendingEvents();

        // Verify outbox event is marked as published
        EventOutbox publishedEvent = eventOutboxRepository.findById(outboxEventId).orElseThrow();
        assertThat(publishedEvent.getPublishedAt()).isNotNull();

        // Verify the message arrived in Kafka (retry to handle rebalancing delays)
        try (KafkaConsumer<String, Map<String, Object>> consumer = createKafkaConsumer()) {
            consumer.subscribe(Collections.singletonList("inventory-changes"));
            ConsumerRecords<String, Map<String, Object>> records = pollWithRetry(consumer, 3);

            assertThat(records.count()).isGreaterThanOrEqualTo(1);

            ConsumerRecord<String, Map<String, Object>> record = records.iterator().next();
            Map<String, Object> message = record.value();

            // Verify envelope structure
            assertThat(message).containsKey("event_id");
            assertThat(message).containsKey("topic");
            assertThat(message).containsKey("event_type");
            assertThat(message).containsKey("entity_type");
            assertThat(message).containsKey("entity_id");
            assertThat(message).containsKey("payload");
            assertThat(message).containsKey("created_at");

            assertThat(message.get("topic")).isEqualTo("inventory-changes");
            assertThat(message.get("event_type")).isEqualTo("CREATED");
            assertThat(message.get("entity_type")).isEqualTo("stock_movement");

            // Verify payload inside envelope
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) message.get("payload");
            assertThat(payload.get("product_id")).isEqualTo(testProduct.getId().toString());
            assertThat(payload.get("quantity_change")).isEqualTo(-2);
            assertThat(payload.get("reason")).isEqualTo("sale");

            // Verify Kafka key is the item_id (for partitioning)
            assertThat(record.key()).isEqualTo(testProduct.getId().toString());
        }
    }

    @Test
    @DisplayName("Outbox records quantities correctly for sale adjustment")
    void outboxRecordsQuantitiesCorrectly() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "quantityChange", -5,
                "reason", "SALE"
        ));

        mockMvc.perform(post("/api/stock-movements/BOX_BIN/{inventoryId}/adjust", testInventory.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        List<EventOutbox> outboxEvents = eventOutboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();
        Map<String, Object> payload = outboxEvents.get(0).getPayload();

        // Location-level: was 20, now 15
        assertThat(payload.get("previous_location_qty")).isEqualTo(20);
        assertThat(payload.get("current_location_qty")).isEqualTo(15);

        // Total-level: current_total is computed by service
        assertThat(payload.get("current_total_qty")).isNotNull();
        assertThat(payload.get("previous_total_qty")).isNotNull();
        int currentTotal = (int) payload.get("current_total_qty");
        int previousTotal = (int) payload.get("previous_total_qty");
        assertThat(previousTotal - currentTotal).isEqualTo(5);
    }

    @Test
    @DisplayName("Restock adjustment produces positive quantity_change in outbox")
    void restockAdjustmentPositiveQuantity() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "quantityChange", 10,
                "reason", "RESTOCK"
        ));

        mockMvc.perform(post("/api/stock-movements/BOX_BIN/{inventoryId}/adjust", testInventory.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        List<EventOutbox> outboxEvents = eventOutboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();
        Map<String, Object> payload = outboxEvents.get(0).getPayload();

        assertThat(payload.get("quantity_change")).isEqualTo(10);
        assertThat(payload.get("reason")).isEqualTo("restock");
        assertThat(payload.get("current_location_qty")).isEqualTo(30);
        assertThat(payload.get("previous_location_qty")).isEqualTo(20);
    }

    /**
     * Create a Kafka consumer for reading messages from the integration test topic.
     * This consumer is test-only -- TRUSTED_PACKAGES="*" must never be used in production.
     */
    @SuppressWarnings("unchecked")
    private KafkaConsumer<String, Map<String, Object>> createKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "integration-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Test-only: trust all packages. Never use "*" in production code.
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        return new KafkaConsumer<>(props);
    }

    /**
     * Poll Kafka with retries to handle consumer group rebalancing delays.
     */
    private ConsumerRecords<String, Map<String, Object>> pollWithRetry(
            KafkaConsumer<String, Map<String, Object>> consumer, int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            ConsumerRecords<String, Map<String, Object>> records = consumer.poll(Duration.ofSeconds(5));
            if (records.count() > 0) {
                return records;
            }
        }
        return ConsumerRecords.empty();
    }
}
