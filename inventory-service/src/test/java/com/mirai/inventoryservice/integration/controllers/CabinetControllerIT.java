package com.mirai.inventoryservice.integration.controllers;

import com.mirai.inventoryservice.dtos.requests.CabinetRequestDTO;
import com.mirai.inventoryservice.dtos.responses.CabinetResponseDTO;
import com.mirai.inventoryservice.integration.BaseIntegrationTest;
import com.mirai.inventoryservice.integration.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CabinetControllerIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/cabinets";

    @Test
    void getAllCabinets_empty_returns200() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAllCabinets_withData_returns200() throws Exception {
        createCabinet("C1");
        createCabinet("C2");

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getCabinetById_exists_returns200() throws Exception {
        UUID id = createCabinet("C1");

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.cabinetCode").value("C1"));
    }

    @Test
    void getCabinetById_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createCabinet_valid_returns201() throws Exception {
        CabinetRequestDTO request = TestDataFactory.validCabinetRequest();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.cabinetCode").value("C1"));
    }

    @Test
    void createCabinet_blankCode_returns400() throws Exception {
        CabinetRequestDTO request = CabinetRequestDTO.builder().cabinetCode("").build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCabinet_invalidPattern_returns400() throws Exception {
        CabinetRequestDTO request = CabinetRequestDTO.builder().cabinetCode("INVALID").build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCabinet_valid_returns200() throws Exception {
        UUID id = createCabinet("C1");
        CabinetRequestDTO updateRequest = TestDataFactory.cabinetWithCode("C2");

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cabinetCode").value("C2"));
    }

    @Test
    void updateCabinet_notFound_returns404() throws Exception {
        CabinetRequestDTO request = TestDataFactory.validCabinetRequest();

        mockMvc.perform(put(BASE_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCabinet_invalidPattern_returns400() throws Exception {
        UUID id = createCabinet("C1");
        CabinetRequestDTO request = CabinetRequestDTO.builder().cabinetCode("INVALID").build();

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteCabinet_exists_returns204() throws Exception {
        UUID id = createCabinet("C1");

        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCabinet_notFound_returns404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createCabinet(String code) throws Exception {
        CabinetRequestDTO request = TestDataFactory.cabinetWithCode(code);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, CabinetResponseDTO.class).getId();
    }
}
