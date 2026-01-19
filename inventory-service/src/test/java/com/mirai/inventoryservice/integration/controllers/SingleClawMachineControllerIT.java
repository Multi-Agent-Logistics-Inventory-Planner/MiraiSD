package com.mirai.inventoryservice.integration.controllers;

import com.mirai.inventoryservice.dtos.requests.SingleClawMachineRequestDTO;
import com.mirai.inventoryservice.dtos.responses.SingleClawMachineResponseDTO;
import com.mirai.inventoryservice.integration.BaseIntegrationTest;
import com.mirai.inventoryservice.integration.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SingleClawMachineControllerIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/single-claw-machines";

    @Test
    void getAllSingleClawMachines_empty_returns200() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAllSingleClawMachines_withData_returns200() throws Exception {
        createSingleClawMachine("S1");
        createSingleClawMachine("S2");

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getSingleClawMachineById_exists_returns200() throws Exception {
        UUID id = createSingleClawMachine("S1");

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.singleClawMachineCode").value("S1"));
    }

    @Test
    void getSingleClawMachineById_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createSingleClawMachine_valid_returns201() throws Exception {
        SingleClawMachineRequestDTO request = TestDataFactory.validSingleClawMachineRequest();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.singleClawMachineCode").value("S1"));
    }

    @Test
    void createSingleClawMachine_blankCode_returns400() throws Exception {
        SingleClawMachineRequestDTO request = SingleClawMachineRequestDTO.builder().singleClawMachineCode("").build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSingleClawMachine_invalidPattern_returns400() throws Exception {
        SingleClawMachineRequestDTO request = SingleClawMachineRequestDTO.builder().singleClawMachineCode("INVALID").build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSingleClawMachine_valid_returns200() throws Exception {
        UUID id = createSingleClawMachine("S1");
        SingleClawMachineRequestDTO updateRequest = TestDataFactory.singleClawMachineWithCode("S2");

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.singleClawMachineCode").value("S2"));
    }

    @Test
    void updateSingleClawMachine_notFound_returns404() throws Exception {
        SingleClawMachineRequestDTO request = TestDataFactory.validSingleClawMachineRequest();

        mockMvc.perform(put(BASE_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateSingleClawMachine_invalidPattern_returns400() throws Exception {
        UUID id = createSingleClawMachine("S1");
        SingleClawMachineRequestDTO request = SingleClawMachineRequestDTO.builder().singleClawMachineCode("INVALID").build();

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSingleClawMachine_exists_returns204() throws Exception {
        UUID id = createSingleClawMachine("S1");

        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSingleClawMachine_notFound_returns404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createSingleClawMachine(String code) throws Exception {
        SingleClawMachineRequestDTO request = TestDataFactory.singleClawMachineWithCode(code);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, SingleClawMachineResponseDTO.class).getId();
    }
}
