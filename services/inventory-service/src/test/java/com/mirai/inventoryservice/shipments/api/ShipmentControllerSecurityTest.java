package com.mirai.inventoryservice.shipments.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.shipments.api.ShipmentItemRequestDTO;
import com.mirai.inventoryservice.shipments.api.ShipmentRequestDTO;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for ShipmentController to verify @PreAuthorize annotations are correctly
 * enforcing role-based access control.
 * <p>
 * Rewritten onto {@link BaseIntegrationTest}'s persona/membership helpers
 * (.specs/phase-7-shipments T-4): every legacy shipment route now resolves site through
 * {@code LegacyMainSiteContextResolver.requireMain()}, which requires a real, DB-backed
 * {@code AuthenticatedPrincipal} with an active MAIN membership -- the previous mocked-JwtService
 * + fabricated-User approach left {@code backendUserId} null, so every "allowed" assertion below
 * would now spuriously see a 403 from the site-authorization step. "Allowed" cases use
 * {@code *TokenWithMainMembership()}; "should be forbidden purely by role" cases use the plain
 * token, since @PreAuthorize's role check runs before site resolution and rejects first.
 */
class ShipmentControllerSecurityTest extends BaseIntegrationTest {

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
            String token = employeeTokenWithMainMembership();
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
            String token = adminTokenWithMainMembership();
            ShipmentRequestDTO request = createValidShipmentRequest();

            // Will return 400/500 due to a nonexistent product id, but not 401/403
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
            String token = employeeTokenWithMainMembership();
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
            String token = adminTokenWithMainMembership();
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
            String token = employeeTokenWithMainMembership();
            UUID shipmentId = UUID.randomUUID();

            mockMvc.perform(delete("/api/shipments/" + shipmentId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to delete shipment (not return 401/403)")
        void deleteShipment_AdminRole_Allowed() throws Exception {
            String token = adminTokenWithMainMembership();
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
        @DisplayName("Should allow EMPLOYEE role to receive shipment (EMPLOYEE+ required)")
        void receiveShipment_EmployeeRole_Allowed() throws Exception {
            String token = employeeTokenWithMainMembership();
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
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        // Auth should pass (not 401/403). May 404/500 on empty test DB.
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("Should allow ADMIN role to receive shipment (not return 401/403)")
        void receiveShipment_AdminRole_Allowed() throws Exception {
            String token = adminTokenWithMainMembership();
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
    @DisplayName("GET endpoints (read-only, should allow any authenticated user with MAIN membership)")
    class ReadOnlyEndpointTests {

        @Test
        @DisplayName("EMPLOYEE should be able to list shipments (not return 401/403)")
        void listShipments_EmployeeRole_Allowed() throws Exception {
            String token = employeeTokenWithMainMembership();

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
            String token = employeeTokenWithMainMembership();

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
            String token = employeeTokenWithMainMembership();

            UUID productId = UUID.randomUUID();

            MvcResult result = mockMvc.perform(get("/api/shipments/by-product/" + productId)
                            .header("Authorization", "Bearer " + token))
                    .andReturn();

            int statusCode = result.getResponse().getStatus();
            assertNotEquals(401, statusCode, "Should not be Unauthorized");
            assertNotEquals(403, statusCode, "Should not be Forbidden");
        }
    }

    /**
     * Review finding (.specs/phase-7-shipments T-4): these three GET routes previously carried no
     * {@code @PreAuthorize} and, before {@code requireMain()} was added, no site check either --
     * any authenticated user, even one with zero site memberships, could read shipment data
     * cross-tenant. {@link ReadOnlyEndpointTests} only proves an authorized (membership-granted)
     * caller is let through; these prove an authenticated-but-unauthorized caller is rejected.
     */
    @Nested
    @DisplayName("GET endpoints reject an authenticated user with no MAIN membership")
    class ReadOnlyEndpointMembershipTests {

        @Test
        @DisplayName("listShipments denies a user with no site membership")
        void listShipments_NoMainMembership_ReturnsForbidden() throws Exception {
            mockMvc.perform(get("/api/shipments")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("getDisplayStatusCounts denies a user with no site membership")
        void getDisplayStatusCounts_NoMainMembership_ReturnsForbidden() throws Exception {
            mockMvc.perform(get("/api/shipments/display-status-counts")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("getShipmentsByProduct denies a user with no site membership")
        void getShipmentsByProduct_NoMainMembership_ReturnsForbidden() throws Exception {
            mockMvc.perform(get("/api/shipments/by-product/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }
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
