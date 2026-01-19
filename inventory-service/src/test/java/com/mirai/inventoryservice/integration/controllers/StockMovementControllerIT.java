package com.mirai.inventoryservice.integration.controllers;

import com.mirai.inventoryservice.dtos.requests.*;
import com.mirai.inventoryservice.dtos.responses.*;
import com.mirai.inventoryservice.integration.BaseIntegrationTest;
import com.mirai.inventoryservice.integration.TestDataFactory;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StockMovementControllerIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/stock-movements";
    private UUID boxBinId;
    private UUID productId;
    private UUID inventoryId;

    @BeforeEach
    void setup() throws Exception {
        boxBinId = createBoxBin();
        productId = createProduct();
        inventoryId = createInventory(boxBinId, productId);
    }

    @Test
    void adjustInventory_restock_returns201() throws Exception {
        AdjustStockRequestDTO request = TestDataFactory.validRestockRequest(5);

        mockMvc.perform(post(BASE_URL + "/" + LocationType.BOX_BIN + "/" + inventoryId + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityChange").value(5))
                .andExpect(jsonPath("$.reason").value("RESTOCK"));
    }

    @Test
    void adjustInventory_sale_returns201() throws Exception {
        AdjustStockRequestDTO request = TestDataFactory.validSaleRequest(5);

        mockMvc.perform(post(BASE_URL + "/" + LocationType.BOX_BIN + "/" + inventoryId + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityChange").value(-5))
                .andExpect(jsonPath("$.reason").value("SALE"));
    }

    @Test
    void adjustInventory_nullQuantity_returns400() throws Exception {
        AdjustStockRequestDTO request = TestDataFactory.adjustWithNullQuantity();

        mockMvc.perform(post(BASE_URL + "/" + LocationType.BOX_BIN + "/" + inventoryId + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adjustInventory_nullReason_returns400() throws Exception {
        AdjustStockRequestDTO request = TestDataFactory.adjustWithNullReason();

        mockMvc.perform(post(BASE_URL + "/" + LocationType.BOX_BIN + "/" + inventoryId + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adjustInventory_insufficientStock_returns400() throws Exception {
        AdjustStockRequestDTO request = TestDataFactory.validSaleRequest(100);

        mockMvc.perform(post(BASE_URL + "/" + LocationType.BOX_BIN + "/" + inventoryId + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adjustInventory_inventoryNotFound_returns404() throws Exception {
        AdjustStockRequestDTO request = TestDataFactory.validRestockRequest(5);

        mockMvc.perform(post(BASE_URL + "/" + LocationType.BOX_BIN + "/" + UUID.randomUUID() + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void transferInventory_valid_returns201() throws Exception {
        UUID rackId = createRack();
        UUID destInventoryId = createRackInventory(rackId, productId);

        TransferInventoryRequestDTO request = TestDataFactory.validTransferRequest(
                LocationType.BOX_BIN, inventoryId,
                LocationType.RACK, destInventoryId,
                5);

        mockMvc.perform(post(BASE_URL + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void transferInventory_nullSourceType_returns400() throws Exception {
        UUID rackId = createRack();
        UUID destInventoryId = createRackInventory(rackId, productId);

        TransferInventoryRequestDTO request = TestDataFactory.transferWithNullSourceType(inventoryId, destInventoryId);

        mockMvc.perform(post(BASE_URL + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferInventory_zeroQuantity_returns400() throws Exception {
        UUID rackId = createRack();
        UUID destInventoryId = createRackInventory(rackId, productId);

        TransferInventoryRequestDTO request = TestDataFactory.transferWithZeroQuantity(
                LocationType.BOX_BIN, inventoryId,
                LocationType.RACK, destInventoryId);

        mockMvc.perform(post(BASE_URL + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferInventory_insufficientStock_returns400() throws Exception {
        UUID rackId = createRack();
        UUID destInventoryId = createRackInventory(rackId, productId);

        TransferInventoryRequestDTO request = TestDataFactory.validTransferRequest(
                LocationType.BOX_BIN, inventoryId,
                LocationType.RACK, destInventoryId,
                100);

        mockMvc.perform(post(BASE_URL + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferInventory_sourceNotFound_returns404() throws Exception {
        UUID rackId = createRack();
        UUID destInventoryId = createRackInventory(rackId, productId);

        TransferInventoryRequestDTO request = TestDataFactory.validTransferRequest(
                LocationType.BOX_BIN, UUID.randomUUID(),
                LocationType.RACK, destInventoryId,
                5);

        mockMvc.perform(post(BASE_URL + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void transferInventory_destNotFound_returns404() throws Exception {
        TransferInventoryRequestDTO request = TestDataFactory.validTransferRequest(
                LocationType.BOX_BIN, inventoryId,
                LocationType.RACK, UUID.randomUUID(),
                5);

        mockMvc.perform(post(BASE_URL + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMovementHistory_paginated_returns200() throws Exception {
        AdjustStockRequestDTO request = TestDataFactory.validRestockRequest(5);
        mockMvc.perform(post(BASE_URL + "/" + LocationType.BOX_BIN + "/" + inventoryId + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BASE_URL + "/history/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void getMovementHistory_all_returns200() throws Exception {
        AdjustStockRequestDTO request = TestDataFactory.validRestockRequest(5);
        mockMvc.perform(post(BASE_URL + "/" + LocationType.BOX_BIN + "/" + inventoryId + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BASE_URL + "/history/" + productId + "/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private UUID createBoxBin() throws Exception {
        BoxBinRequestDTO request = TestDataFactory.validBoxBinRequest();
        String response = mockMvc.perform(post("/api/box-bins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, BoxBinResponseDTO.class).getId();
    }

    private UUID createRack() throws Exception {
        RackRequestDTO request = TestDataFactory.validRackRequest();
        String response = mockMvc.perform(post("/api/racks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, RackResponseDTO.class).getId();
    }

    private UUID createProduct() throws Exception {
        ProductRequestDTO request = TestDataFactory.validProductRequest();
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, ProductResponseDTO.class).getId();
    }

    private UUID createInventory(UUID boxBinId, UUID productId) throws Exception {
        InventoryRequestDTO request = TestDataFactory.validInventoryRequest(productId);
        String response = mockMvc.perform(post("/api/box-bins/" + boxBinId + "/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, BoxBinInventoryResponseDTO.class).getId();
    }

    private UUID createRackInventory(UUID rackId, UUID productId) throws Exception {
        InventoryRequestDTO request = TestDataFactory.inventoryWithQuantity(productId, 0);
        String response = mockMvc.perform(post("/api/racks/" + rackId + "/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, RackInventoryResponseDTO.class).getId();
    }
}
