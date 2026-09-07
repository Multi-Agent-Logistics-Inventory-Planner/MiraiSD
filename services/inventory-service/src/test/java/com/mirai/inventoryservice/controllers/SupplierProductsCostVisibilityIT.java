package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.Supplier;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SupplierRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for a follow-up review finding: GET /api/suppliers/{id}/products returned
 * ProductResponseDTOs straight from SupplierService with no redaction, bypassing the
 * ProductController-only cost/MSRP visibility fix even though this endpoint is reachable by
 * EMPLOYEE (it carries no @PreAuthorize, same as ProductController's read endpoints).
 */
class SupplierProductsCostVisibilityIT extends BaseIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID createSupplierWithProduct() {
        Category category = categoryRepository.save(Category.builder()
                .name("Supplier Cost Visibility Category")
                .slug("supplier-cost-visibility-category")
                .build());

        // Supplier.canonicalName is insertable=false/updatable=false - production fills it via
        // a Postgres trigger (V22__add_supplier_canonical_name_trigger.sql) that doesn't exist
        // in the H2 test schema (Hibernate-generated via ddl-auto=create-drop, not Flyway).
        // Insert directly so the NOT NULL column has a value, matching the pattern used
        // elsewhere in this suite for JPA-restricted columns.
        UUID supplierId = UUID.randomUUID();
        entityManager.createNativeQuery(
                        "INSERT INTO suppliers (id, display_name, canonical_name, is_active) "
                                + "VALUES (:id, :displayName, :canonicalName, true)")
                .setParameter("id", supplierId)
                .setParameter("displayName", "Supplier Cost Visibility Vendor")
                .setParameter("canonicalName", "supplier cost visibility vendor")
                .executeUpdate();
        entityManager.clear();
        Supplier supplier = supplierRepository.findById(supplierId).orElseThrow();

        Product product = Product.builder()
                .sku("SUPPLIER-COST-VIS-001")
                .name("Supplier Cost Visibility Product")
                .category(category)
                .reorderPoint(5)
                .targetStockLevel(20)
                .leadTimeDays(7)
                .unitCost(BigDecimal.valueOf(6.5))
                .msrp(BigDecimal.valueOf(15))
                .isActive(true)
                .quantity(0)
                .preferredSupplier(supplier)
                .preferredSupplierAuto(false)
                .build();
        productRepository.save(product);

        return supplier.getId();
    }

    @Test
    void adminSeesUnitCostAndMsrp() throws Exception {
        UUID supplierId = createSupplierWithProduct();

        mockMvc.perform(get("/api/suppliers/" + supplierId + "/products")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unitCost").value(6.5))
                .andExpect(jsonPath("$[0].msrp").value(15));
    }

    @Test
    void employeeDoesNotSeeUnitCostOrMsrp() throws Exception {
        UUID supplierId = createSupplierWithProduct();

        String body = mockMvc.perform(get("/api/suppliers/" + supplierId + "/products")
                        .header("Authorization", "Bearer " + employeeToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unitCost").doesNotExist())
                .andExpect(jsonPath("$[0].msrp").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Absence, not null - see RedactedFieldsAreOmittedFromJsonIT for why.
        com.fasterxml.jackson.databind.JsonNode first = objectMapper.readTree(body).get(0);
        org.assertj.core.api.Assertions.assertThat(first.has("unitCost")).isFalse();
        org.assertj.core.api.Assertions.assertThat(first.has("msrp")).isFalse();
    }

    @Test
    void assistantManagerSeesMsrpButNotUnitCost() throws Exception {
        UUID supplierId = createSupplierWithProduct();

        mockMvc.perform(get("/api/suppliers/" + supplierId + "/products")
                        .header("Authorization", "Bearer " + assistantManagerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unitCost").doesNotExist())
                .andExpect(jsonPath("$[0].msrp").value(15));
    }
}
