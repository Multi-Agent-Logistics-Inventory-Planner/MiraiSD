package com.mirai.inventoryservice.dtos.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.catalog.api.ProductResponseDTO;
import com.mirai.inventoryservice.catalog.application.ProductListItemDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiBoxTierResponseDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiDailyPayoutsResponseDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the serialization half of field redaction.
 *
 * The controller-level tests only prove the DTO field is set to null; they pass either way
 * because both {@code jsonPath(...).doesNotExist()} and {@code JsonNode.hasNonNull(...)}
 * also accept an explicit JSON null. But the contract-compatibility argument for this whole
 * approach rests on the field being ABSENT: every redacted property is declared
 * non-nullable and optional in packages/contracts/openapi.json, so emitting {@code null}
 * would silently violate the published contract (and oasdiff's breaking-change gate exists
 * precisely to stop that).
 *
 * These assertions therefore check key absence, not null-ness, using the Spring-configured
 * ObjectMapper - the same one the HTTP message converters use - so a regression in
 * {@code @JsonInclude} placement (e.g. moved to the wrong target, or dropped from a record
 * component) or a global ObjectMapper inclusion override fails here.
 */
class RedactedFieldsAreOmittedFromJsonIT extends BaseIntegrationTest {

    private JsonNode serialize(Object dto) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsString(dto));
    }

    @Test
    void kujiDailyPayoutsOmitsValueWonInSeriesAndTotal() throws Exception {
        // Exactly what KujiBoxController.applyPriceVisibility produces for a caller
        // without kuji_prices:view.
        KujiDailyPayoutsResponseDTO redacted = new KujiDailyPayoutsResponseDTO(
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2),
                "UTC",
                List.of(new KujiDailyPayoutsResponseDTO.DailyPoint(LocalDate.of(2026, 1, 1), null, 3)),
                new KujiDailyPayoutsResponseDTO.Totals(null, 3));

        JsonNode json = serialize(redacted);

        assertThat(json.path("series").get(0).has("valueWon"))
                .as("series[0].valueWon must be omitted, not null")
                .isFalse();
        assertThat(json.path("total").has("valueWon"))
                .as("total.valueWon must be omitted, not null")
                .isFalse();
        // Non-redacted siblings must survive - omission must be field-scoped.
        assertThat(json.path("series").get(0).path("slipCount").asInt()).isEqualTo(3);
        assertThat(json.path("total").path("slipCount").asInt()).isEqualTo(3);
    }

    @Test
    void kujiDailyPayoutsKeepsValueWonWhenPresent() throws Exception {
        KujiDailyPayoutsResponseDTO visible = new KujiDailyPayoutsResponseDTO(
                UUID.randomUUID(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), "UTC",
                List.of(new KujiDailyPayoutsResponseDTO.DailyPoint(
                        LocalDate.of(2026, 1, 1), java.math.BigDecimal.valueOf(50), 3)),
                new KujiDailyPayoutsResponseDTO.Totals(java.math.BigDecimal.valueOf(50), 3));

        JsonNode json = serialize(visible);

        assertThat(json.path("series").get(0).path("valueWon").decimalValue())
                .isEqualByComparingTo("50");
        assertThat(json.path("total").path("valueWon").decimalValue()).isEqualByComparingTo("50");
    }

    @Test
    void productResponseOmitsUnitCostAndMsrp() throws Exception {
        ProductResponseDTO dto = ProductResponseDTO.builder()
                .id(UUID.randomUUID()).sku("SKU-1").name("Product").unitCost(null).msrp(null)
                .build();

        JsonNode json = serialize(dto);

        assertThat(json.has("unitCost")).isFalse();
        assertThat(json.has("msrp")).isFalse();
        assertThat(json.path("sku").asText()).isEqualTo("SKU-1");
    }

    @Test
    void productListItemOmitsUnitCostAndMsrp() throws Exception {
        ProductListItemDTO dto = new ProductListItemDTO();
        dto.setId(UUID.randomUUID());
        dto.setSku("SKU-2");
        dto.setUnitCost(null);
        dto.setMsrp(null);

        JsonNode json = serialize(dto);

        assertThat(json.has("unitCost")).isFalse();
        assertThat(json.has("msrp")).isFalse();
    }

    @Test
    void shipmentResponseOmitsTotalCostAndItemUnitCost() throws Exception {
        ShipmentItemResponseDTO item = ShipmentItemResponseDTO.builder()
                .id(UUID.randomUUID()).unitCost(null).build();
        ShipmentResponseDTO dto = ShipmentResponseDTO.builder()
                .id(UUID.randomUUID()).totalCost(null).items(List.of(item)).build();

        JsonNode json = serialize(dto);

        assertThat(json.has("totalCost")).isFalse();
        assertThat(json.path("items").get(0).has("unitCost")).isFalse();
    }

    @Test
    void inventoryTotalOmitsUnitCost() throws Exception {
        InventoryTotalDTO dto = InventoryTotalDTO.builder()
                .itemId(UUID.randomUUID()).sku("SKU-3").unitCost(null).totalQuantity(2).build();

        JsonNode json = serialize(dto);

        assertThat(json.has("unitCost")).isFalse();
        assertThat(json.path("totalQuantity").asInt()).isEqualTo(2);
    }

    @Test
    void kujiTierOmitsPriceAndLinkedProductPrice() throws Exception {
        KujiBoxTierResponseDTO dto = KujiBoxTierResponseDTO.builder()
                .id(UUID.randomUUID()).label("A").price(null).linkedProductPrice(null)
                .activeCount(5).build();

        JsonNode json = serialize(dto);

        assertThat(json.has("price")).isFalse();
        assertThat(json.has("linkedProductPrice")).isFalse();
        assertThat(json.path("activeCount").asInt()).isEqualTo(5);
    }
}
