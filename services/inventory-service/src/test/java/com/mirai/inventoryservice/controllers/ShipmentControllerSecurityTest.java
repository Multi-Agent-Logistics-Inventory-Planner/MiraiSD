package com.mirai.inventoryservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.auth.JwtService;
import com.mirai.inventoryservice.dtos.requests.ShipmentItemRequestDTO;
import com.mirai.inventoryservice.dtos.requests.ShipmentRequestDTO;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for ShipmentController to verify @PreAuthorize annotations
 * are correctly enforcing role-based access control.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShipmentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    private final String testSecret = "test-secret-key-for-jwt-validation-that-is-long-enough-for-hmac-sha";
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("POST /api/shipments (createShipment)")
    class CreateShipmentSecurityTests {

        @Test
        @DisplayName("Should return 401 Unauthorized when no token provided")
        void createShipment_NoAuth_ReturnsUnauthorized() throws Exception {
            ShipmentRequestDTO request = createValidShipmentRequest();

            mockMvc.perform(post("/api/shipments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when EMPLOYEE role attempts to create")
        void createShipment_EmployeeRole_ReturnsForbidden() throws Exception {
            String token = createValidToken("employee-123", "Employee User", "EMPLOYEE");
            setupMockJwtService(token, "employee-123", "Employee User", "EMPLOYEE");

            ShipmentRequestDTO request = createValidShipmentRequest();

            mockMvc.perform(post("/api/shipments")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to create shipment (not return 401/403)")
        void createShipment_AdminRole_Allowed() throws Exception {
            String token = createValidToken("admin-123", "Admin User", "ADMIN");
            setupMockJwtService(token, "admin-123", "Admin User", "ADMIN");

            ShipmentRequestDTO request = createValidShipmentRequest();

            // Will return 400/500 due to missing service mocks, but not 401/403
            MvcResult result = mockMvc.perform(post("/api/shipments")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            int statusCode = result.getResponse().getStatus();
            assertNotEquals(401, statusCode, "Should not be Unauthorized");
            assertNotEquals(403, statusCode, "Should not be Forbidden");
        }
    }

    @Nested
    @DisplayName("PUT /api/shipments/{id} (updateShipment)")
    class UpdateShipmentSecurityTests {

        @Test
        @DisplayName("Should return 401 Unauthorized when no token provided")
        void updateShipment_NoAuth_ReturnsUnauthorized() throws Exception {
            UUID shipmentId = UUID.randomUUID();
            ShipmentRequestDTO request = createValidShipmentRequest();

            mockMvc.perform(put("/api/shipments/" + shipmentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when EMPLOYEE role attempts to update")
        void updateShipment_EmployeeRole_ReturnsForbidden() throws Exception {
            String token = createValidToken("employee-123", "Employee User", "EMPLOYEE");
            setupMockJwtService(token, "employee-123", "Employee User", "EMPLOYEE");

            UUID shipmentId = UUID.randomUUID();
            ShipmentRequestDTO request = createValidShipmentRequest();

            mockMvc.perform(put("/api/shipments/" + shipmentId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to update shipment (not return 401/403)")
        void updateShipment_AdminRole_Allowed() throws Exception {
            String token = createValidToken("admin-123", "Admin User", "ADMIN");
            setupMockJwtService(token, "admin-123", "Admin User", "ADMIN");

            UUID shipmentId = UUID.randomUUID();
            ShipmentRequestDTO request = createValidShipmentRequest();

            MvcResult result = mockMvc.perform(put("/api/shipments/" + shipmentId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            int statusCode = result.getResponse().getStatus();
            assertNotEquals(401, statusCode, "Should not be Unauthorized");
            assertNotEquals(403, statusCode, "Should not be Forbidden");
        }
    }

    @Nested
    @DisplayName("DELETE /api/shipments/{id} (deleteShipment)")
    class DeleteShipmentSecurityTests {

        @Test
        @DisplayName("Should return 401 Unauthorized when no token provided")
        void deleteShipment_NoAuth_ReturnsUnauthorized() throws Exception {
            UUID shipmentId = UUID.randomUUID();

            mockMvc.perform(delete("/api/shipments/" + shipmentId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when EMPLOYEE role attempts to delete")
        void deleteShipment_EmployeeRole_ReturnsForbidden() throws Exception {
            String token = createValidToken("employee-123", "Employee User", "EMPLOYEE");
            setupMockJwtService(token, "employee-123", "Employee User", "EMPLOYEE");

            UUID shipmentId = UUID.randomUUID();

            mockMvc.perform(delete("/api/shipments/" + shipmentId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to delete shipment (not return 401/403)")
        void deleteShipment_AdminRole_Allowed() throws Exception {
            String token = createValidToken("admin-123", "Admin User", "ADMIN");
            setupMockJwtService(token, "admin-123", "Admin User", "ADMIN");

            UUID shipmentId = UUID.randomUUID();

            MvcResult result = mockMvc.perform(delete("/api/shipments/" + shipmentId)
                            .header("Authorization", "Bearer " + token))
                    .andReturn();

            int statusCode = result.getResponse().getStatus();
            assertNotEquals(401, statusCode, "Should not be Unauthorized");
            assertNotEquals(403, statusCode, "Should not be Forbidden");
        }
    }

    @Nested
    @DisplayName("POST /api/shipments/{id}/receive (receiveShipment)")
    class ReceiveShipmentSecurityTests {

        @Test
        @DisplayName("Should return 401 Unauthorized when no token provided")
        void receiveShipment_NoAuth_ReturnsUnauthorized() throws Exception {
            UUID shipmentId = UUID.randomUUID();
            String requestBody = """
                {
                    "actualDeliveryDate": "2024-01-26",
                    "receivedBy": "550e8400-e29b-41d4-a716-446655440000",
                    "itemReceipts": []
                }
                """;

            mockMvc.perform(post("/api/shipments/" + shipmentId + "/receive")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when EMPLOYEE role attempts to receive")
        void receiveShipment_EmployeeRole_ReturnsForbidden() throws Exception {
            String token = createValidToken("employee-123", "Employee User", "EMPLOYEE");
            setupMockJwtService(token, "employee-123", "Employee User", "EMPLOYEE");

            UUID shipmentId = UUID.randomUUID();
            String requestBody = """
                {
                    "actualDeliveryDate": "2024-01-26",
                    "receivedBy": "550e8400-e29b-41d4-a716-446655440000",
                    "itemReceipts": []
                }
                """;

            mockMvc.perform(post("/api/shipments/" + shipmentId + "/receive")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to receive shipment (not return 401/403)")
        void receiveShipment_AdminRole_Allowed() throws Exception {
            String token = createValidToken("admin-123", "Admin User", "ADMIN");
            setupMockJwtService(token, "admin-123", "Admin User", "ADMIN");

            UUID shipmentId = UUID.randomUUID();
            String requestBody = """
                {
                    "actualDeliveryDate": "2024-01-26",
                    "receivedBy": "550e8400-e29b-41d4-a716-446655440000",
                    "itemReceipts": []
                }
                """;

            MvcResult result = mockMvc.perform(post("/api/shipments/" + shipmentId + "/receive")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andReturn();

            int statusCode = result.getResponse().getStatus();
            assertNotEquals(401, statusCode, "Should not be Unauthorized");
            assertNotEquals(403, statusCode, "Should not be Forbidden");
        }
    }

    @Nested
    @DisplayName("GET endpoints (read-only, should allow any authenticated user)")
    class ReadOnlyEndpointTests {

        @Test
        @DisplayName("EMPLOYEE should be able to list shipments (not return 401/403)")
        void listShipments_EmployeeRole_Allowed() throws Exception {
            String token = createValidToken("employee-123", "Employee User", "EMPLOYEE");
            setupMockJwtService(token, "employee-123", "Employee User", "EMPLOYEE");

            MvcResult result = mockMvc.perform(get("/api/shipments")
                            .header("Authorization", "Bearer " + token))
                    .andReturn();

            int statusCode = result.getResponse().getStatus();
            assertNotEquals(401, statusCode, "Should not be Unauthorized");
            assertNotEquals(403, statusCode, "Should not be Forbidden");
        }

        @Test
        @DisplayName("EMPLOYEE should be able to get shipment by ID (not return 401/403)")
        void getShipmentById_EmployeeRole_Allowed() throws Exception {
            String token = createValidToken("employee-123", "Employee User", "EMPLOYEE");
            setupMockJwtService(token, "employee-123", "Employee User", "EMPLOYEE");

            UUID shipmentId = UUID.randomUUID();

            MvcResult result = mockMvc.perform(get("/api/shipments/" + shipmentId)
                            .header("Authorization", "Bearer " + token))
                    .andReturn();

            int statusCode = result.getResponse().getStatus();
            assertNotEquals(401, statusCode, "Should not be Unauthorized");
            assertNotEquals(403, statusCode, "Should not be Forbidden");
        }

        @Test
        @DisplayName("EMPLOYEE should be able to get shipments by product (not return 401/403)")
        void getShipmentsByProduct_EmployeeRole_Allowed() throws Exception {
            String token = createValidToken("employee-123", "Employee User", "EMPLOYEE");
            setupMockJwtService(token, "employee-123", "Employee User", "EMPLOYEE");

            UUID productId = UUID.randomUUID();

            MvcResult result = mockMvc.perform(get("/api/shipments/by-product/" + productId)
                            .header("Authorization", "Bearer " + token))
                    .andReturn();

            int statusCode = result.getResponse().getStatus();
            assertNotEquals(401, statusCode, "Should not be Unauthorized");
            assertNotEquals(403, statusCode, "Should not be Forbidden");
        }
    }

    // Helper methods

    private void setupMockJwtService(String token, String personId, String name, String role) {
        when(jwtService.extractPersonId(token)).thenReturn(personId);
        when(jwtService.extractName(token)).thenReturn(name);
        when(jwtService.extractRole(token)).thenReturn(role);
        when(jwtService.validateToken(token)).thenReturn(true);
    }

    private String createValidToken(String personId, String name, String role) {
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("name", name);
        userMetadata.put("role", role);

        return Jwts.builder()
                .setSubject(personId)
                .claim("user_metadata", userMetadata)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
                .signWith(signingKey)
                .compact();
    }

    private ShipmentRequestDTO createValidShipmentRequest() {
        ShipmentRequestDTO request = new ShipmentRequestDTO();
        request.setShipmentNumber("SHIP-001");
        request.setSupplierName("Test Supplier");
        request.setStatus(ShipmentStatus.PENDING);
        request.setOrderDate(LocalDate.now());
        request.setCreatedBy(UUID.randomUUID());

        ShipmentItemRequestDTO item = new ShipmentItemRequestDTO();
        item.setItemId(UUID.randomUUID());
        item.setOrderedQuantity(10);
        request.setItems(List.of(item));

        return request;
    }
}
