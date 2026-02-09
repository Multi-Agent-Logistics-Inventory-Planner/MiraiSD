package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for ProductController.
 * Authorization matrix:
 * - GET: ALL authenticated users
 * - POST/PUT/PATCH/DELETE: ADMIN only
 */
@DisplayName("ProductController Security Tests")
class ProductControllerSecurityIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/products";

    @Nested
    @DisplayName("GET /api/products (getAllProducts)")
    class GetAllProductsTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getAllProducts_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should allow USER role to read products")
        void getAllProducts_userRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to read products")
        void getAllProducts_employeeRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow ADMIN role to read products")
        void getAllProducts_adminRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/products/{id} (getProductById)")
    class GetProductByIdTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getProductById_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should allow USER role to read product by ID")
        void getProductById_userRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
        }
    }

    @Nested
    @DisplayName("POST /api/products (createProduct)")
    class CreateProductTests {

        private static final String VALID_PRODUCT_JSON = """
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

        @Test
        @DisplayName("Should return 401 when no token provided")
        void createProduct_noAuth_returns401() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType("application/json")
                            .content(VALID_PRODUCT_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to create")
        void createProduct_userRole_returns403() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + userToken())
                            .contentType("application/json")
                            .content(VALID_PRODUCT_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to create")
        void createProduct_employeeRole_returns403() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(VALID_PRODUCT_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to create product")
        void createProduct_adminRole_notForbidden() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType("application/json")
                            .content(VALID_PRODUCT_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("PUT /api/products/{id} (updateProduct)")
    class UpdateProductTests {

        private static final String VALID_PRODUCT_JSON = """
                {
                    "sku": "TEST-001",
                    "name": "Updated Product",
                    "category": "PLUSH",
                    "reorderPoint": 10,
                    "targetStockLevel": 50,
                    "leadTimeDays": 7,
                    "unitCost": 9.99
                }
                """;

        @Test
        @DisplayName("Should return 401 when no token provided")
        void updateProduct_noAuth_returns401() throws Exception {
            mockMvc.perform(put(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .contentType("application/json")
                            .content(VALID_PRODUCT_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to update")
        void updateProduct_userRole_returns403() throws Exception {
            mockMvc.perform(put(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + userToken())
                            .contentType("application/json")
                            .content(VALID_PRODUCT_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to update")
        void updateProduct_employeeRole_returns403() throws Exception {
            mockMvc.perform(put(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + employeeToken())
                            .contentType("application/json")
                            .content(VALID_PRODUCT_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to update product")
        void updateProduct_adminRole_notForbidden() throws Exception {
            mockMvc.perform(put(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType("application/json")
                            .content(VALID_PRODUCT_JSON))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("DELETE /api/products/{id} (deleteProduct)")
    class DeleteProductTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void deleteProduct_noAuth_returns401() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to delete")
        void deleteProduct_userRole_returns403() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to delete")
        void deleteProduct_employeeRole_returns403() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to delete product")
        void deleteProduct_adminRole_notForbidden() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("PATCH /api/products/{id}/deactivate")
    class DeactivateProductTests {

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to deactivate")
        void deactivateProduct_employeeRole_returns403() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000/deactivate")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to deactivate product")
        void deactivateProduct_adminRole_notForbidden() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000/deactivate")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("PATCH /api/products/{id}/activate")
    class ActivateProductTests {

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to activate")
        void activateProduct_employeeRole_returns403() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000/activate")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to activate product")
        void activateProduct_adminRole_notForbidden() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000/activate")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }
}
