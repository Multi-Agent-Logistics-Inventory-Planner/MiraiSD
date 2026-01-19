package com.mirai.inventoryservice.integration.controllers;

import com.mirai.inventoryservice.dtos.requests.DoubleClawMachineRequestDTO;
import com.mirai.inventoryservice.dtos.responses.DoubleClawMachineResponseDTO;
import com.mirai.inventoryservice.integration.BaseIntegrationTest;
import com.mirai.inventoryservice.integration.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DoubleClawMachineControllerIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/double-claw-machines";

    @Test
    void getAllDoubleClawMachines_empty_returns200() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAllDoubleClawMachines_withData_returns200() throws Exception {
        createDoubleClawMachine("D1");
        createDoubleClawMachine("D2");

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getDoubleClawMachineById_exists_returns200() throws Exception {
        UUID id = createDoubleClawMachine("D1");

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.doubleClawMachineCode").value("D1"));
    }

    @Test
    void getDoubleClawMachineById_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createDoubleClawMachine_valid_returns201() throws Exception {
        DoubleClawMachineRequestDTO request = TestDataFactory.validDoubleClawMachineRequest();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.doubleClawMachineCode").value("D1"));
    }

    @Test
    void createDoubleClawMachine_blankCode_returns400() throws Exception {
        DoubleClawMachineRequestDTO request = DoubleClawMachineRequestDTO.builder().doubleClawMachineCode("").build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDoubleClawMachine_invalidPattern_returns400() throws Exception {
        DoubleClawMachineRequestDTO request = DoubleClawMachineRequestDTO.builder().doubleClawMachineCode("INVALID").build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDoubleClawMachine_valid_returns200() throws Exception {
        UUID id = createDoubleClawMachine("D1");
        DoubleClawMachineRequestDTO updateRequest = TestDataFactory.doubleClawMachineWithCode("D2");

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doubleClawMachineCode").value("D2"));
    }

    @Test
    void updateDoubleClawMachine_notFound_returns404() throws Exception {
        DoubleClawMachineRequestDTO request = TestDataFactory.validDoubleClawMachineRequest();

        mockMvc.perform(put(BASE_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateDoubleClawMachine_invalidPattern_returns400() throws Exception {
        UUID id = createDoubleClawMachine("D1");
        DoubleClawMachineRequestDTO request = DoubleClawMachineRequestDTO.builder().doubleClawMachineCode("INVALID").build();

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteDoubleClawMachine_exists_returns204() throws Exception {
        UUID id = createDoubleClawMachine("D1");

        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDoubleClawMachine_notFound_returns404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createDoubleClawMachine(String code) throws Exception {
        DoubleClawMachineRequestDTO request = TestDataFactory.doubleClawMachineWithCode(code);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, DoubleClawMachineResponseDTO.class).getId();
    }
}
