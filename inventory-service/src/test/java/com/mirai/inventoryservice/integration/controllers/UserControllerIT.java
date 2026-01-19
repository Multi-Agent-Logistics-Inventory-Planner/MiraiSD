package com.mirai.inventoryservice.integration.controllers;

import com.mirai.inventoryservice.dtos.requests.UserRequestDTO;
import com.mirai.inventoryservice.dtos.responses.UserResponseDTO;
import com.mirai.inventoryservice.integration.BaseIntegrationTest;
import com.mirai.inventoryservice.integration.TestDataFactory;
import com.mirai.inventoryservice.models.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserControllerIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/users";

    @Test
    void getAllUsers_empty_returns200() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAllUsers_withData_returns200() throws Exception {
        createUser("user1@test.com");
        createUser("user2@test.com");

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getUserById_exists_returns200() throws Exception {
        UUID id = createUser("test@example.com");

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void getUserById_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserByEmail_exists_returns200() throws Exception {
        createUser("findme@example.com");

        mockMvc.perform(get(BASE_URL + "/email/findme@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("findme@example.com"));
    }

    @Test
    void getUserByEmail_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/email/nonexistent@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserByFullName_exists_returns200() throws Exception {
        createUserWithName("John Doe");

        mockMvc.perform(get(BASE_URL + "/name/John Doe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("John Doe"));
    }

    @Test
    void getUserByFullName_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/name/Nobody"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createUser_valid_returns201() throws Exception {
        UserRequestDTO request = TestDataFactory.validUserRequest();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void createUser_blankName_returns400() throws Exception {
        UserRequestDTO request = UserRequestDTO.builder()
                .fullName("")
                .email("test@example.com")
                .role(UserRole.EMPLOYEE)
                .build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_invalidEmail_returns400() throws Exception {
        UserRequestDTO request = TestDataFactory.userWithInvalidEmail();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_nullRole_returns400() throws Exception {
        UserRequestDTO request = UserRequestDTO.builder()
                .fullName("Test User")
                .email("test@example.com")
                .role(null)
                .build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUser_valid_returns200() throws Exception {
        UUID id = createUser("original@example.com");
        UserRequestDTO updateRequest = UserRequestDTO.builder()
                .fullName("Updated Name")
                .email("updated@example.com")
                .role(UserRole.MANAGER)
                .build();

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void updateUser_notFound_returns404() throws Exception {
        UserRequestDTO request = TestDataFactory.validUserRequest();

        mockMvc.perform(put(BASE_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUser_exists_returns204() throws Exception {
        UUID id = createUser("todelete@example.com");

        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUser_notFound_returns404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createUser(String email) throws Exception {
        UserRequestDTO request = TestDataFactory.userWithEmail(email);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, UserResponseDTO.class).getId();
    }

    private UUID createUserWithName(String name) throws Exception {
        UserRequestDTO request = TestDataFactory.userWithName(name);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, UserResponseDTO.class).getId();
    }
}
