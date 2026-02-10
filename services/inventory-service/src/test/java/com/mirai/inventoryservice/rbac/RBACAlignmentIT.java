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
     * Dashboard Feature: ADMIN only
     * Frontend: dashboard:view permission required for "/"
     * Backend: AnalyticsController methods require ADMIN role
     */
    @Nested
    @DisplayName("Dashboard (ADMIN only)")
    class DashboardPermissions {

        @Test
        @DisplayName("Employee denied access to analytics")
        void employee_cannotAccessAnalytics() throws Exception {
            mockMvc.perform(get("/api/analytics")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin can access analytics")
        void admin_canAccessAnalytics() throws Exception {
            mockMvc.perform(get("/api/analytics")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
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
                        "leadTimeDays": 7,
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
        @DisplayName("Admin can create products")
        void admin_canCreateProduct() throws Exception {
            String json = """
                    {
                        "sku": "TEST-001",
                        "name": "Test Product",
                        "category": "PLUSH",
                        "reorderPoint": 10,
                        "targetStockLevel": 50,
                        "leadTimeDays": 7,
                        "unitCost": 9.99
                    }
                    """;
            mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());
        }
    }

    /**
     * Storage Feature: View (ALL), Modify (ADMIN only)
     * Frontend: storage:view (all), storage:create/update/delete (ADMIN)
     * Backend: Location controllers GET (all authenticated), POST/PUT/DELETE (ADMIN)
     */
    @Nested
    @DisplayName("Storage (View: ALL, Modify: ADMIN)")
    class StoragePermissions {

        @Test
        @DisplayName("Employee can view racks")
        void employee_canViewRacks() throws Exception {
            mockMvc.perform(get("/api/racks")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Employee denied rack creation")
        void employee_cannotCreateRack() throws Exception {
            String json = """
                    {
                        "name": "Rack 1",
                        "capacity": 100
                    }
                    """;
            mockMvc.perform(post("/api/racks")
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin can create racks")
        void admin_canCreateRack() throws Exception {
            String json = """
                    {
                        "name": "Rack 1",
                        "capacity": 100
                    }
                    """;
            mockMvc.perform(post("/api/racks")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());
        }
    }

    /**
     * Inventory Operations: EMPLOYEE + ADMIN
     * Frontend: inventory:adjust, inventory:transfer (EMPLOYEE + ADMIN)
     * Backend: Inventory controllers adjust/transfer (EMPLOYEE + ADMIN)
     */
    @Nested
    @DisplayName("Inventory Operations (EMPLOYEE + ADMIN)")
    class InventoryPermissions {

        @Test
        @DisplayName("Employee can adjust inventory")
        void employee_canAdjustInventory() throws Exception {
            String json = """
                    {
                        "productId": "550e8400-e29b-41d4-a716-446655440000",
                        "quantityChange": 10,
                        "reason": "RECEIVED"
                    }
                    """;
            mockMvc.perform(post("/api/rack-inventory/adjust")
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        // Should not be forbidden (403) or unauthorized (401)
                        // May return 400 or 404 if entities don't exist, but authorization should pass
                        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("Admin can adjust inventory")
        void admin_canAdjustInventory() throws Exception {
            String json = """
                    {
                        "productId": "550e8400-e29b-41d4-a716-446655440000",
                        "quantityChange": 10,
                        "reason": "RECEIVED"
                    }
                    """;
            mockMvc.perform(post("/api/rack-inventory/adjust")
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
     * Analytics Feature: ADMIN only
     * Frontend: analytics:view (ADMIN)
     * Backend: AnalyticsController (ADMIN)
     */
    @Nested
    @DisplayName("Analytics (ADMIN only)")
    class AnalyticsPermissions {

        @Test
        @DisplayName("Employee denied analytics access")
        void employee_cannotViewAnalytics() throws Exception {
            mockMvc.perform(get("/api/analytics")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin can view analytics")
        void admin_canViewAnalytics() throws Exception {
            mockMvc.perform(get("/api/analytics")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Forecasts Feature: ADMIN only (implicit in frontend)
     * Frontend: Only accessible via analytics page (ADMIN only)
     * Backend: ForecastController (ADMIN)
     */
    @Nested
    @DisplayName("Forecasts (ADMIN only)")
    class ForecastPermissions {

        @Test
        @DisplayName("Employee denied forecast access")
        void employee_cannotViewForecasts() throws Exception {
            mockMvc.perform(get("/api/forecasts")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin can view forecasts")
        void admin_canViewForecasts() throws Exception {
            mockMvc.perform(get("/api/forecasts")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Notifications Feature: ADMIN only
     * Frontend: notifications:view, notifications:manage (ADMIN)
     * Backend: NotificationController (ADMIN)
     */
    @Nested
    @DisplayName("Notifications (ADMIN only)")
    class NotificationPermissions {

        @Test
        @DisplayName("Employee denied notification access")
        void employee_cannotViewNotifications() throws Exception {
            mockMvc.perform(get("/api/notifications")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
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
     * Audit Log Feature: ALL authenticated users
     * Frontend: audit_log:view (EMPLOYEE + ADMIN)
     * Backend: StockMovementController (EMPLOYEE + ADMIN)
     */
    @Nested
    @DisplayName("Audit Log (ALL authenticated)")
    class AuditLogPermissions {

        @Test
        @DisplayName("Employee can view audit log")
        void employee_canViewAuditLog() throws Exception {
            mockMvc.perform(get("/api/stock-movements")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Admin can view audit log")
        void admin_canViewAuditLog() throws Exception {
            mockMvc.perform(get("/api/stock-movements")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
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
     * Settings Feature: ADMIN only
     * Frontend: settings:view, settings:manage (ADMIN)
     * Backend: UserController current user methods (all), management (ADMIN)
     */
    @Nested
    @DisplayName("Settings (Own: ALL, Manage: ADMIN)")
    class SettingsPermissions {

        @Test
        @DisplayName("Employee can view own settings")
        void employee_canViewOwnSettings() throws Exception {
            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Admin can view own settings")
        void admin_canViewOwnSettings() throws Exception {
            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
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
            mockMvc.perform(get("/api/analytics"))
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
