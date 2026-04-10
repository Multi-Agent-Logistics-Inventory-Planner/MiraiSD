package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.requests.BulkAssignProductsRequestDTO;
import com.mirai.inventoryservice.dtos.requests.SupplierRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ProductResponseDTO;
import com.mirai.inventoryservice.dtos.responses.SupplierResponseDTO;
import com.mirai.inventoryservice.services.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    /**
     * Get all suppliers with lead time statistics.
     * Optional query params:
     * - q: Search by display name
     * - active: Filter by active status (true/false)
     */
    @GetMapping
    public ResponseEntity<List<SupplierResponseDTO>> getSuppliers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active) {

        List<SupplierResponseDTO> suppliers;

        if (q != null && !q.isBlank()) {
            // Search mode
            suppliers = supplierService.searchSuppliers(q, active);
        } else if (active != null) {
            // Filter by active status
            suppliers = supplierService.getSuppliersByActiveWithStats(active);
        } else {
            // Get all with stats
            suppliers = supplierService.getAllSuppliersWithStats();
        }

        return ResponseEntity.ok(suppliers);
    }

    /**
     * Get a single supplier by ID with lead time statistics.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponseDTO> getSupplierById(@PathVariable UUID id) {
        SupplierResponseDTO supplier = supplierService.getSupplierWithStats(id);
        return ResponseEntity.ok(supplier);
    }

    /**
     * Create a new supplier.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<SupplierResponseDTO> createSupplier(
            @Valid @RequestBody SupplierRequestDTO requestDTO) {
        SupplierResponseDTO supplier = supplierService.createSupplier(
                requestDTO.getDisplayName(),
                requestDTO.getContactEmail()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(supplier);
    }

    /**
     * Update supplier details.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<SupplierResponseDTO> updateSupplier(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierRequestDTO requestDTO) {
        SupplierResponseDTO supplier = supplierService.updateSupplier(
                id,
                requestDTO.getDisplayName(),
                requestDTO.getContactEmail(),
                requestDTO.getIsActive()
        );
        return ResponseEntity.ok(supplier);
    }

    /**
     * Soft delete (deactivate) a supplier.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteSupplier(@PathVariable UUID id) {
        supplierService.deactivateSupplier(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deactivate a supplier (same as DELETE but explicit).
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deactivateSupplier(@PathVariable UUID id) {
        supplierService.deactivateSupplier(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reactivate a supplier.
     */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> activateSupplier(@PathVariable UUID id) {
        supplierService.activateSupplier(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Bulk assign products to a supplier (sets preferred_supplier_id).
     */
    @PostMapping("/{id}/assign-products")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Integer> assignProducts(
            @PathVariable UUID id,
            @Valid @RequestBody BulkAssignProductsRequestDTO requestDTO) {
        int count = supplierService.assignProductsToSupplier(id, requestDTO.getProductIds());
        return ResponseEntity.ok(count);
    }

    /**
     * Get all products assigned to a supplier.
     */
    @GetMapping("/{id}/products")
    public ResponseEntity<List<ProductResponseDTO>> getSupplierProducts(@PathVariable UUID id) {
        List<ProductResponseDTO> products = supplierService.getProductsBySupplierId(id);
        return ResponseEntity.ok(products);
    }
}
