package com.mirai.inventoryservice.integration.controllers;

import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ProductRequestDTO;
import com.mirai.inventoryservice.dtos.requests.RackRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ProductResponseDTO;
import com.mirai.inventoryservice.dtos.responses.RackInventoryResponseDTO;
import com.mirai.inventoryservice.dtos.responses.RackResponseDTO;
import com.mirai.inventoryservice.integration.BaseIntegrationTest;
import com.mirai.inventoryservice.integration.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RackInventoryControllerIT extends BaseIntegrationTest {

    private UUID rackId;
    private UUID productId;

    @BeforeEach
    void setup() throws Exception {
        rackId = createRack();
        productId = createProduct();
    }

    private String baseUrl() {
        return "/api/racks/" + rackId + "/inventory";
    }

    @Test
    void listInventory_empty_returns200() throws Exception {
        mockMvc.perform(get(baseUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listInventory_withData_returns200() throws Exception {
        createInventory(productId);

        mockMvc.perform(get(baseUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listInventory_rackNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/racks/" + UUID.randomUUID() + "/inventory"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInventoryById_exists_returns200() throws Exception {
        UUID inventoryId = createInventory(productId);

        mockMvc.perform(get(baseUrl() + "/" + inventoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inventoryId.toString()))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    void getInventoryById_notFound_returns404() throws Exception {
        mockMvc.perform(get(baseUrl() + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void addInventory_valid_returns201() throws Exception {
        InventoryRequestDTO request = TestDataFactory.validInventoryRequest(productId);

        mockMvc.perform(post(baseUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.rackId").value(rackId.toString()));
    }

    @Test
    void addInventory_nullProductId_returns400() throws Exception {
        InventoryRequestDTO request = TestDataFactory.inventoryWithNullProduct();

        mockMvc.perform(post(baseUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addInventory_negativeQuantity_returns400() throws Exception {
        InventoryRequestDTO request = TestDataFactory.inventoryWithNegativeQuantity(productId);

        mockMvc.perform(post(baseUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addInventory_productNotFound_returns404() throws Exception {
        InventoryRequestDTO request = TestDataFactory.validInventoryRequest(UUID.randomUUID());

        mockMvc.perform(post(baseUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addInventory_duplicateProduct_returns400() throws Exception {
        createInventory(productId);
        InventoryRequestDTO request = TestDataFactory.validInventoryRequest(productId);

        mockMvc.perform(post(baseUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateInventory_valid_returns200() throws Exception {
        UUID inventoryId = createInventory(productId);
        InventoryRequestDTO request = TestDataFactory.inventoryWithQuantity(productId, 25);

        mockMvc.perform(put(baseUrl() + "/" + inventoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(25));
    }

    @Test
    void updateInventory_notFound_returns404() throws Exception {
        InventoryRequestDTO request = TestDataFactory.validInventoryRequest(productId);

        mockMvc.perform(put(baseUrl() + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteInventory_exists_returns204() throws Exception {
        UUID inventoryId = createInventory(productId);

        mockMvc.perform(delete(baseUrl() + "/" + inventoryId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(baseUrl() + "/" + inventoryId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteInventory_notFound_returns404() throws Exception {
        mockMvc.perform(delete(baseUrl() + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
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

    private UUID createInventory(UUID itemId) throws Exception {
        InventoryRequestDTO request = TestDataFactory.validInventoryRequest(itemId);
        String response = mockMvc.perform(post(baseUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, RackInventoryResponseDTO.class).getId();
    }
}
