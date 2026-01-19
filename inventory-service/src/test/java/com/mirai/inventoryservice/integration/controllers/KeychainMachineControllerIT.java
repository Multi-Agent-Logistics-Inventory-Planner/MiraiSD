package com.mirai.inventoryservice.integration.controllers;

import com.mirai.inventoryservice.dtos.requests.KeychainMachineRequestDTO;
import com.mirai.inventoryservice.dtos.responses.KeychainMachineResponseDTO;
import com.mirai.inventoryservice.integration.BaseIntegrationTest;
import com.mirai.inventoryservice.integration.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class KeychainMachineControllerIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/keychain-machines";

    @Test
    void getAllKeychainMachines_empty_returns200() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAllKeychainMachines_withData_returns200() throws Exception {
        createKeychainMachine("M1");
        createKeychainMachine("M2");

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getKeychainMachineById_exists_returns200() throws Exception {
        UUID id = createKeychainMachine("M1");

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.keychainMachineCode").value("M1"));
    }

    @Test
    void getKeychainMachineById_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createKeychainMachine_valid_returns201() throws Exception {
        KeychainMachineRequestDTO request = TestDataFactory.validKeychainMachineRequest();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.keychainMachineCode").value("M1"));
    }

    @Test
    void createKeychainMachine_blankCode_returns400() throws Exception {
        KeychainMachineRequestDTO request = KeychainMachineRequestDTO.builder().keychainMachineCode("").build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createKeychainMachine_invalidPattern_returns400() throws Exception {
        KeychainMachineRequestDTO request = KeychainMachineRequestDTO.builder().keychainMachineCode("INVALID").build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateKeychainMachine_valid_returns200() throws Exception {
        UUID id = createKeychainMachine("M1");
        KeychainMachineRequestDTO updateRequest = TestDataFactory.keychainMachineWithCode("M2");

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keychainMachineCode").value("M2"));
    }

    @Test
    void updateKeychainMachine_notFound_returns404() throws Exception {
        KeychainMachineRequestDTO request = TestDataFactory.validKeychainMachineRequest();

        mockMvc.perform(put(BASE_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateKeychainMachine_invalidPattern_returns400() throws Exception {
        UUID id = createKeychainMachine("M1");
        KeychainMachineRequestDTO request = KeychainMachineRequestDTO.builder().keychainMachineCode("INVALID").build();

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteKeychainMachine_exists_returns204() throws Exception {
        UUID id = createKeychainMachine("M1");

        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteKeychainMachine_notFound_returns404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createKeychainMachine(String code) throws Exception {
        KeychainMachineRequestDTO request = TestDataFactory.keychainMachineWithCode(code);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, KeychainMachineResponseDTO.class).getId();
    }
}
