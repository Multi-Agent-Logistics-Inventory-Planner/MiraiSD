package com.mirai.inventoryservice.integration.controllers;

import com.mirai.inventoryservice.dtos.requests.BoxBinRequestDTO;
import com.mirai.inventoryservice.dtos.responses.BoxBinResponseDTO;
import com.mirai.inventoryservice.integration.BaseIntegrationTest;
import com.mirai.inventoryservice.integration.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BoxBinControllerIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/box-bins";

    @Test
    void getAllBoxBins_empty_returns200() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAllBoxBins_withData_returns200() throws Exception {
        createBoxBin("B1");
        createBoxBin("B2");

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getBoxBinById_exists_returns200() throws Exception {
        UUID id = createBoxBin("B1");

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.boxBinCode").value("B1"));
    }

    @Test
    void getBoxBinById_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createBoxBin_valid_returns201() throws Exception {
        BoxBinRequestDTO request = TestDataFactory.validBoxBinRequest();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.boxBinCode").value("B1"));
    }

    @Test
    void createBoxBin_blankCode_returns400() throws Exception {
        BoxBinRequestDTO request = TestDataFactory.blankBoxBinRequest();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBoxBin_invalidPattern_returns400() throws Exception {
        BoxBinRequestDTO request = TestDataFactory.invalidBoxBinRequest();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateBoxBin_valid_returns200() throws Exception {
        UUID id = createBoxBin("B1");
        BoxBinRequestDTO updateRequest = TestDataFactory.boxBinWithCode("B2");

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boxBinCode").value("B2"));
    }

    @Test
    void updateBoxBin_notFound_returns404() throws Exception {
        BoxBinRequestDTO request = TestDataFactory.validBoxBinRequest();

        mockMvc.perform(put(BASE_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateBoxBin_invalidPattern_returns400() throws Exception {
        UUID id = createBoxBin("B1");
        BoxBinRequestDTO request = TestDataFactory.invalidBoxBinRequest();

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteBoxBin_exists_returns204() throws Exception {
        UUID id = createBoxBin("B1");

        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBoxBin_notFound_returns404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createBoxBin(String code) throws Exception {
        BoxBinRequestDTO request = TestDataFactory.boxBinWithCode(code);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, BoxBinResponseDTO.class).getId();
    }
}
