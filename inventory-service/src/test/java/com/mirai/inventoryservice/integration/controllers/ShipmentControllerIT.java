package com.mirai.inventoryservice.integration.controllers;

import com.mirai.inventoryservice.dtos.requests.ProductRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ReceiveShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ProductResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ShipmentResponseDTO;
import com.mirai.inventoryservice.integration.BaseIntegrationTest;
import com.mirai.inventoryservice.integration.TestDataFactory;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ShipmentControllerIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/shipments";
    private UUID productId;

    @BeforeEach
    void setup() throws Exception {
        productId = createProduct();
    }

    @Test
    void listShipments_empty_returns200() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listShipments_withData_returns200() throws Exception {
        createShipment(productId);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listShipments_filterByStatus_returns200() throws Exception {
        createShipmentWithStatus(productId, ShipmentStatus.PENDING);
        createShipmentWithStatus(productId, ShipmentStatus.IN_TRANSIT);

        mockMvc.perform(get(BASE_URL).param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getShipmentById_exists_returns200() throws Exception {
        UUID shipmentId = createShipment(productId);

        mockMvc.perform(get(BASE_URL + "/" + shipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipmentId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getShipmentById_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createShipment_valid_returns201() throws Exception {
        ShipmentRequestDTO request = TestDataFactory.validShipmentRequest(productId);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void createShipment_nullStatus_returns400() throws Exception {
        ShipmentRequestDTO request = TestDataFactory.shipmentWithNullStatus(productId);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShipment_nullOrderDate_returns400() throws Exception {
        ShipmentRequestDTO request = TestDataFactory.shipmentWithNullOrderDate(productId);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShipment_emptyItems_returns400() throws Exception {
        ShipmentRequestDTO request = TestDataFactory.shipmentWithEmptyItems();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateShipment_valid_returns200() throws Exception {
        UUID shipmentId = createShipment(productId);
        ShipmentRequestDTO updateRequest = TestDataFactory.shipmentWithStatus(productId, ShipmentStatus.IN_TRANSIT);

        mockMvc.perform(put(BASE_URL + "/" + shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    @Test
    void updateShipment_notFound_returns404() throws Exception {
        ShipmentRequestDTO request = TestDataFactory.validShipmentRequest(productId);

        mockMvc.perform(put(BASE_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteShipment_exists_returns204() throws Exception {
        UUID shipmentId = createShipment(productId);

        mockMvc.perform(delete(BASE_URL + "/" + shipmentId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + shipmentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteShipment_notFound_returns404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void receiveShipment_valid_returns200() throws Exception {
        UUID shipmentId = createShipmentWithStatus(productId, ShipmentStatus.IN_TRANSIT);
        ShipmentResponseDTO shipment = getShipment(shipmentId);
        UUID shipmentItemId = shipment.getItems().get(0).getId();

        ReceiveShipmentRequestDTO request = TestDataFactory.validReceiveShipmentRequest(shipmentItemId);

        mockMvc.perform(post(BASE_URL + "/" + shipmentId + "/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void receiveShipment_notFound_returns404() throws Exception {
        ReceiveShipmentRequestDTO request = TestDataFactory.validReceiveShipmentRequest(UUID.randomUUID());

        mockMvc.perform(post(BASE_URL + "/" + UUID.randomUUID() + "/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void receiveShipment_invalidStatus_returns400() throws Exception {
        UUID shipmentId = createShipmentWithStatus(productId, ShipmentStatus.DELIVERED);
        ShipmentResponseDTO shipment = getShipment(shipmentId);
        UUID shipmentItemId = shipment.getItems().get(0).getId();

        ReceiveShipmentRequestDTO request = TestDataFactory.validReceiveShipmentRequest(shipmentItemId);

        mockMvc.perform(post(BASE_URL + "/" + shipmentId + "/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
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

    private UUID createShipment(UUID productId) throws Exception {
        ShipmentRequestDTO request = TestDataFactory.validShipmentRequest(productId);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, ShipmentResponseDTO.class).getId();
    }

    private UUID createShipmentWithStatus(UUID productId, ShipmentStatus status) throws Exception {
        ShipmentRequestDTO request = TestDataFactory.shipmentWithStatus(productId, status);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, ShipmentResponseDTO.class).getId();
    }

    private ShipmentResponseDTO getShipment(UUID shipmentId) throws Exception {
        String response = mockMvc.perform(get(BASE_URL + "/" + shipmentId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, ShipmentResponseDTO.class);
    }
}
