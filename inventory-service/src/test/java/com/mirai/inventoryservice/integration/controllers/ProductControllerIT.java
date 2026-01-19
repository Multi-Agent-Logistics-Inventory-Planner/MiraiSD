package com.mirai.inventoryservice.integration.controllers;

import com.mirai.inventoryservice.dtos.requests.ProductRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ProductResponseDTO;
import com.mirai.inventoryservice.integration.BaseIntegrationTest;
import com.mirai.inventoryservice.integration.TestDataFactory;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProductControllerIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/products";

    @Test
    void getAllProducts_empty_returns200() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAllProducts_withData_returns200() throws Exception {
        createProduct("Product 1");
        createProduct("Product 2");

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAllProducts_filterByCategory_returns200() throws Exception {
        createProductWithCategory(ProductCategory.PLUSHIE);
        createProductWithCategory(ProductCategory.FIGURINE);

        mockMvc.perform(get(BASE_URL).param("category", "PLUSHIE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].category").value("PLUSHIE"));
    }

    @Test
    void getAllProducts_filterByActiveOnly_returns200() throws Exception {
        UUID id = createProduct("Active Product");
        createProduct("Another Product");

        mockMvc.perform(patch(BASE_URL + "/" + id + "/deactivate"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL).param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getAllProducts_search_returns200() throws Exception {
        createProduct("Test Plushie");
        createProduct("Figurine Item");

        mockMvc.perform(get(BASE_URL).param("search", "Plushie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Plushie"));
    }

    @Test
    void getProductById_exists_returns200() throws Exception {
        UUID id = createProduct("Test Product");

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Test Product"));
    }

    @Test
    void getProductById_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProductBySku_exists_returns200() throws Exception {
        createProductWithSku("SKU-001");

        mockMvc.perform(get(BASE_URL + "/sku/SKU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-001"));
    }

    @Test
    void getProductBySku_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/sku/NONEXISTENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProduct_valid_returns201() throws Exception {
        ProductRequestDTO request = TestDataFactory.validProductRequest();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Plushie"))
                .andExpect(jsonPath("$.category").value("PLUSHIE"));
    }

    @Test
    void createProduct_nullCategory_returns400() throws Exception {
        ProductRequestDTO request = TestDataFactory.productWithNullCategory();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_blankName_returns400() throws Exception {
        ProductRequestDTO request = TestDataFactory.productWithBlankName();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_duplicateSku_returns409() throws Exception {
        createProductWithSku("SKU-DUP");
        ProductRequestDTO request = TestDataFactory.productWithSku("SKU-DUP");

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateProduct_valid_returns200() throws Exception {
        UUID id = createProduct("Original Name");
        ProductRequestDTO updateRequest = TestDataFactory.productWithName("Updated Name");

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void updateProduct_notFound_returns404() throws Exception {
        ProductRequestDTO request = TestDataFactory.validProductRequest();

        mockMvc.perform(put(BASE_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProduct_duplicateSku_returns409() throws Exception {
        createProductWithSku("SKU-FIRST");
        UUID secondId = createProductWithSku("SKU-SECOND");
        ProductRequestDTO updateRequest = TestDataFactory.productWithSku("SKU-FIRST");

        mockMvc.perform(put(BASE_URL + "/" + secondId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(updateRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivateProduct_exists_returns204() throws Exception {
        UUID id = createProduct("Test Product");

        mockMvc.perform(patch(BASE_URL + "/" + id + "/deactivate"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void activateProduct_exists_returns204() throws Exception {
        UUID id = createProduct("Test Product");
        mockMvc.perform(patch(BASE_URL + "/" + id + "/deactivate"));

        mockMvc.perform(patch(BASE_URL + "/" + id + "/activate"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void deleteProduct_exists_returns204() throws Exception {
        UUID id = createProduct("Test Product");

        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProduct_notFound_returns404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createProduct(String name) throws Exception {
        ProductRequestDTO request = TestDataFactory.productWithName(name);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, ProductResponseDTO.class).getId();
    }

    private UUID createProductWithCategory(ProductCategory category) throws Exception {
        ProductRequestDTO request = TestDataFactory.productWithCategory(category);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, ProductResponseDTO.class).getId();
    }

    private UUID createProductWithSku(String sku) throws Exception {
        ProductRequestDTO request = TestDataFactory.productWithSku(sku);
        String response = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return fromJson(response, ProductResponseDTO.class).getId();
    }
}
