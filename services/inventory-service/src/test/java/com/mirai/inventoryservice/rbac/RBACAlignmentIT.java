package com.mirai.inventoryservice.rbac;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive RBAC Alignment Integration Tests.
 *
 * Purpose: Verify that frontend RBAC permissions align with backend @PreAuthorize enforcement.
 * This test serves as the single source of truth for the permission matrix documented in
 * /docs/codemaps/architecture.md
 *
 * Permission Matrix:
 * - ADMIN: Full access to all operations
 * - EMPLOYEE: Limited access (inventory operations, viewing data)
 * - Unauthenticated: Denied access (401)
 *
 * Security Principle: Backend is the source of truth. Frontend checks are UX only.
 */
@DisplayName("RBAC Alignment - Frontend Permissions vs Backend Enforcement")
class RBACAlignmentIT extends BaseIntegrationTest {

    /**
     * Dashboard/Analytics Feature: EMPLOYEE + ADMIN
     * Frontend: analytics pages accessible to authenticated users
     * Backend: AnalyticsController requires EMPLOYEE or ADMIN role
     */
    @Nested
    @DisplayName("Analytics (EMPLOYEE + ADMIN)")
    class AnalyticsPermissions {

        @Test
        @DisplayName("Employee can access analytics")
        void employee_canAccessAnalytics() throws Exception {
            mockMvc.perform(get("/api/analytics/inventory-by-category")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("Admin can access analytics")
        void admin_canAccessAnalytics() throws Exception {
            mockMvc.perform(get("/api/analytics/inventory-by-category")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }
    }

    /**
     * Products Feature: View (ALL), Modify (ADMIN only)
     * Frontend: products:view (all), products:create/update/delete (ADMIN)
     * Backend: ProductController GET (all authenticated), POST/PUT/DELETE (ADMIN)
     */
    @Nested
    @DisplayName("Products (View: ALL, Modify: ADMIN)")
    class ProductPermissions {

        @Test
        @DisplayName("Employee can view products")
        void employee_canViewProducts() throws Exception {
            mockMvc.perform(get("/api/products")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Employee denied product creation")
        void employee_cannotCreateProduct() throws Exception {
            String json = """
                    {
                        "sku": "TEST-001",
                        "name": "Test Product",
                        "category": "PLUSH",
                        "reorderPoint": 10,
                        "targetStockLevel": 50,
                        "leadTimeDays": 14,
                        "unitCost": 9.99
                    }
                    """;
            mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin can create products (auth passes)")
        void admin_canCreateProduct() throws Exception {
            String json = """
                    {
                        "sku": "TEST-001",
                        "name": "Test Product",
                        "category": "PLUSH",
                        "reorderPoint": 10,
                        "targetStockLevel": 50,
                        "leadTimeDays": 14,
                        "unitCost": 9.99
                    }
                    """;
            mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        // Auth should pass (not 401/403). May 500 on empty test DB.
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }
    }

    /**
     * Storage Feature: View (ALL), Create/Update (EMPLOYEE+), Delete (ADMIN only)
     * Frontend: storage:view (all), storage:create/update (EMPLOYEE+), storage:delete (ADMIN)
     * Backend: LocationController GET (all authenticated), POST/PUT (EMPLOYEE+), DELETE (ADMIN)
     */
    @Nested
    @DisplayName("Storage (View: ALL, Create/Update: EMPLOYEE+, Delete: ADMIN)")
    class StoragePermissions {

        @Test
        @DisplayName("Employee can view locations")
        void employee_canViewLocations() throws Exception {
            mockMvc.perform(get("/api/locations")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("Employee can create locations")
        void employee_canCreateLocation() throws Exception {
            String json = """
                    {
                        "locationCode": "T99",
                        "storageLocationId": "550e8400-e29b-41d4-a716-446655440000"
                    }
                    """;
            mockMvc.perform(post("/api/locations")
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("Employee denied location deletion")
        void employee_cannotDeleteLocation() throws Exception {
            mockMvc.perform(delete("/api/locations/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin can delete locations")
        void admin_canDeleteLocation() throws Exception {
            mockMvc.perform(delete("/api/locations/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }
    }

    /**
     * Inventory Operations: EMPLOYEE + ADMIN
     * Frontend: inventory:adjust, inventory:add (EMPLOYEE + ADMIN)
     * Backend: LocationInventoryController POST/PUT (EMPLOYEE+), StockMovementController adjust (EMPLOYEE+)
     */
    @Nested
    @DisplayName("Inventory Operations (EMPLOYEE + ADMIN)")
    class InventoryPermissions {

        @Test
        @DisplayName("Employee can add inventory to a location")
        void employee_canAddInventory() throws Exception {
            String json = """
                    {
                        "itemId": "550e8400-e29b-41d4-a716-446655440000",
                        "quantity": 10
                    }
                    """;
            mockMvc.perform(post("/api/locations/550e8400-e29b-41d4-a716-446655440000/inventory")
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("Admin can add inventory to a location")
        void admin_canAddInventory() throws Exception {
            String json = """
                    {
                        "itemId": "550e8400-e29b-41d4-a716-446655440000",
                        "quantity": 10
                    }
                    """;
            mockMvc.perform(post("/api/locations/550e8400-e29b-41d4-a716-446655440000/inventory")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }
    }

    /**
     * Shipments Feature: Create/Update (EMPLOYEE + ADMIN), Delete (ADMIN only)
     * Frontend: shipments:view/create/update (EMPLOYEE + ADMIN), shipments:delete (ADMIN)
     * Backend: ShipmentController GET/POST/PUT (EMPLOYEE + ADMIN), DELETE (ADMIN)
     */
    @Nested
    @DisplayName("Shipments (Manage: EMPLOYEE+ADMIN, Delete: ADMIN)")
    class ShipmentPermissions {

        @Test
        @DisplayName("Employee can view shipments")
        void employee_canViewShipments() throws Exception {
            mockMvc.perform(get("/api/shipments")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Employee can create shipments")
        void employee_canCreateShipment() throws Exception {
            String json = """
                    {
                        "shipmentNumber": "SHIP-001",
                        "supplierName": "Test Supplier",
                        "orderDate": "2026-02-01"
                    }
                    """;
            mockMvc.perform(post("/api/shipments")
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("Employee denied shipment deletion")
        void employee_cannotDeleteShipment() throws Exception {
            mockMvc.perform(delete("/api/shipments/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin can delete shipments")
        void admin_canDeleteShipment() throws Exception {
            mockMvc.perform(delete("/api/shipments/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }
    }

    /**
     * Forecasts Feature: EMPLOYEE + ADMIN
     * Backend: ForecastController requires EMPLOYEE or ADMIN role
     */
    @Nested
    @DisplayName("Forecasts (EMPLOYEE + ADMIN)")
    class ForecastPermissions {

        @Test
        @DisplayName("Employee can view forecasts")
        void employee_canViewForecasts() throws Exception {
            mockMvc.perform(get("/api/forecasts")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("Admin can view forecasts")
        void admin_canViewForecasts() throws Exception {
            mockMvc.perform(get("/api/forecasts")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }
    }

    /**
     * Notifications Feature: EMPLOYEE + ADMIN
     * Backend: NotificationController requires EMPLOYEE or ADMIN role
     */
    @Nested
    @DisplayName("Notifications (EMPLOYEE + ADMIN)")
    class NotificationPermissions {

        @Test
        @DisplayName("Employee can view notifications")
        void employee_canViewNotifications() throws Exception {
            mockMvc.perform(get("/api/notifications")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("Admin can view notifications")
        void admin_canViewNotifications() throws Exception {
            mockMvc.perform(get("/api/notifications")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Audit Log Feature: EMPLOYEE + ADMIN
     * Backend: StockMovementController requires EMPLOYEE or ADMIN role
     */
    @Nested
    @DisplayName("Audit Log (EMPLOYEE + ADMIN)")
    class AuditLogPermissions {

        @Test
        @DisplayName("Employee can view audit log")
        void employee_canViewAuditLog() throws Exception {
            mockMvc.perform(get("/api/stock-movements/audit-log")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("Admin can view audit log")
        void admin_canViewAuditLog() throws Exception {
            mockMvc.perform(get("/api/stock-movements/audit-log")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }
    }

    /**
     * Team Management: ADMIN only
     * Frontend: team:view, team:manage (ADMIN)
     * Backend: UserController (ADMIN)
     */
    @Nested
    @DisplayName("Team Management (ADMIN only)")
    class TeamPermissions {

        @Test
        @DisplayName("Employee denied user list access")
        void employee_cannotViewUsers() throws Exception {
            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin can view users")
        void admin_canViewUsers() throws Exception {
            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Settings Feature: Current user info accessible to all authenticated users
     * Backend: AuthController /api/auth/me (all authenticated)
     */
    @Nested
    @DisplayName("Settings (Own: ALL authenticated)")
    class SettingsPermissions {

        @Test
        @DisplayName("Employee can view own settings")
        void employee_canViewOwnSettings() throws Exception {
            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("Admin can view own settings")
        void admin_canViewOwnSettings() throws Exception {
            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }
    }

    /**
     * Unauthenticated Access: All endpoints should return 401
     */
    @Nested
    @DisplayName("Unauthenticated Access (All Denied)")
    class UnauthenticatedPermissions {

        @Test
        @DisplayName("Unauthenticated denied products access")
        void noAuth_cannotAccessProducts() throws Exception {
            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Unauthenticated denied analytics access")
        void noAuth_cannotAccessAnalytics() throws Exception {
            mockMvc.perform(get("/api/analytics/inventory-by-category"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Unauthenticated denied shipments access")
        void noAuth_cannotAccessShipments() throws Exception {
            mockMvc.perform(get("/api/shipments"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
