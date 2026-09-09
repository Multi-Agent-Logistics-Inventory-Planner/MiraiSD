package com.mirai.inventoryservice.catalog.api;

import com.mirai.inventoryservice.catalog.application.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Global catalog v1 supplier routes. Suppliers are wholly global (no site scope), so this
 * mirrors {@link SupplierController} 1:1 under the {@code /api/v1/catalog} prefix, with distinct
 * handler and operation names so springdoc doesn't collide the legacy operation IDs. The legacy
 * {@code /api/suppliers} routes are untouched.
 */
@RestController
@RequestMapping("/api/v1/catalog/suppliers")
public class CatalogSupplierController {
    private final SupplierService supplierService;

    public CatalogSupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public ResponseEntity<List<SupplierResponseDTO>> getCatalogSuppliers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active) {
        List<SupplierResponseDTO> suppliers;
        if (q != null && !q.isBlank()) {
            suppliers = supplierService.searchSuppliers(q, active);
        } else if (active != null) {
            suppliers = supplierService.getSuppliersByActiveWithStats(active);
        } else {
            suppliers = supplierService.getAllSuppliersWithStats();
        }
        return ResponseEntity.ok(suppliers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponseDTO> getCatalogSupplierById(@PathVariable UUID id) {
        return ResponseEntity.ok(supplierService.getSupplierWithStats(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<SupplierResponseDTO> createCatalogSupplier(@Valid @RequestBody SupplierRequestDTO requestDTO) {
        SupplierResponseDTO supplier = supplierService.createSupplier(
                requestDTO.getDisplayName(), requestDTO.getContactEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(supplier);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<SupplierResponseDTO> updateCatalogSupplier(
            @PathVariable UUID id, @Valid @RequestBody SupplierRequestDTO requestDTO) {
        SupplierResponseDTO supplier = supplierService.updateSupplier(
                id, requestDTO.getDisplayName(), requestDTO.getContactEmail(), requestDTO.getIsActive());
        return ResponseEntity.ok(supplier);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteCatalogSupplier(@PathVariable UUID id) {
        supplierService.deactivateSupplier(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deactivateCatalogSupplier(@PathVariable UUID id) {
        supplierService.deactivateSupplier(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> activateCatalogSupplier(@PathVariable UUID id) {
        supplierService.activateSupplier(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign-products")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Integer> assignCatalogSupplierProducts(
            @PathVariable UUID id, @Valid @RequestBody BulkAssignProductsRequestDTO requestDTO) {
        int count = supplierService.assignProductsToSupplier(id, requestDTO.getProductIds());
        return ResponseEntity.ok(count);
    }

    @GetMapping("/{id}/products")
    public ResponseEntity<List<ProductResponseDTO>> getCatalogSupplierProducts(
            @PathVariable UUID id, Authentication authentication) {
        List<ProductResponseDTO> products = supplierService.getProductsBySupplierId(id);
        CostVisibilityPolicy.applyCostVisibility(products, authentication);
        return ResponseEntity.ok(products);
    }
}
