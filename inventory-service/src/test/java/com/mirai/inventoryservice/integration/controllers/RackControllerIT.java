package com.mirai.inventoryservice.integration.controllers;

import com.mirai.inventoryservice.dtos.requests.RackRequestDTO;
import com.mirai.inventoryservice.dtos.responses.RackResponseDTO;
import com.mirai.inventoryservice.integration.BaseIntegrationTest;
import com.mirai.inventoryservice.integration.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RackControllerIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/racks";

    @Test
    void getAllRacks_empty_returns200() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAllRacks_withData_returns200() throws Exception {
        createRack("R1");
        createRack("R2");

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getRackById_exists_returns200() throws Exception {
        UUID id = createRack("R1");

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.rackCode").value("R1"));
    }

    @Test
    void getRackById_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRack_valid_returns201() throws Exception {
        RackRequestDTO request = TestDataFactory.validRackRequest();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.rackCode").value("R1"));
    }

    @Test
    void createRack_blankCode_returns400() throws Exception {
        RackRequestDTO request = RackRequestDTO.builder().rackCode("").build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRack_invalidPattern_returns400() throws Exception {
        RackRequestDTO request = RackRequestDTO.builder().rackCode("INVALID").build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRack_valid_returns200() throws Exception {
        UUID id = createRack("R1");
        RackRequestDTO updateRequest = TestDataFactory.rackWithCode("R2");

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rackCode").value("R2"));
    }

    @Test
    void updateRack_notFound_returns404() throws Exception {
        RackRequestDTO request = TestDataFactory.validRackRequest();

        mockMvc.perform(put(BASE_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRack_invalidPattern_returns400() throws Exception {
        UUID id = createRack("R1");
        RackRequestDTO request = RackRequestDTO.builder().rackCode("INVALID").build();

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRack_exists_returns204() throws Exception {
        UUID id = createRack("R1");

        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRack_notFound_returns404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createRack(String code) throws Exception {
        RackRequestDTO request = TestDataFactory.rackWithCode(code);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, RackResponseDTO.class).getId();
    }
}
