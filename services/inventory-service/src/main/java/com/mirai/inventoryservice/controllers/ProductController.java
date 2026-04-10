package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.ProductMapper;
import com.mirai.inventoryservice.dtos.requests.ProductRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ProductResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ProductSummaryDTO;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.services.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;
    private final ProductMapper productMapper;

    public ProductController(ProductService productService, ProductMapper productMapper) {
        this.productService = productService;
        this.productMapper = productMapper;
    }

    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> getAllProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly,
            @RequestParam(required = false, defaultValue = "false") Boolean rootOnly,
            @RequestParam(required = false, defaultValue = "false") Boolean kujiOnly) {
        List<Product> products;

        if (search != null && !search.isBlank()) {
            products = productService.searchProducts(search);
        } else if (rootOnly && Boolean.TRUE.equals(kujiOnly)) {
            products = productService.getRootKujiProducts();
        } else if (rootOnly && activeOnly) {
            products = productService.getActiveRootProducts();
        } else if (rootOnly) {
            products = productService.getRootProducts();
        } else if (categoryId != null && activeOnly) {
            products = productService.getActiveProductsByCategory(categoryId);
        } else if (categoryId != null) {
            products = productService.getProductsByCategory(categoryId);
        } else if (activeOnly) {
            products = productService.getActiveProducts();
        } else {
            products = productService.getAllProducts();
        }

        // Batch fetch parent IDs to avoid N+1 when computing hasChildren
        Set<UUID> parentIds = productService.getParentProductIds();
        return ResponseEntity.ok(productMapper.toResponseDTOList(products, parentIds));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable UUID id) {
        Product product = productService.getProductById(id);
        ProductResponseDTO dto = productMapper.toResponseDTO(product);
        // Enrich with last delivered supplier for "Use Auto" feature
        Object[] lastSupplier = productService.getLastDeliveredSupplier(id);
        if (lastSupplier != null && lastSupplier.length >= 2) {
            dto.setLastDeliveredSupplierId((UUID) lastSupplier[0]);
            dto.setLastDeliveredSupplierName((String) lastSupplier[1]);
        }
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductResponseDTO> getProductBySku(@PathVariable String sku) {
        Product product = productService.getProductBySku(sku);
        ProductResponseDTO dto = productMapper.toResponseDTO(product);
        // Enrich with last delivered supplier for "Use Auto" feature
        Object[] lastSupplier = productService.getLastDeliveredSupplier(product.getId());
        if (lastSupplier != null && lastSupplier.length >= 2) {
            dto.setLastDeliveredSupplierId((UUID) lastSupplier[0]);
            dto.setLastDeliveredSupplierName((String) lastSupplier[1]);
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequestDTO requestDTO) {
        Product product = productService.createProduct(
                requestDTO.getSku(),
                requestDTO.getCategoryId(),
                requestDTO.getParentId(),
                requestDTO.getLetter(),
                requestDTO.getTemplateQuantity(),
                requestDTO.getName(),
                requestDTO.getDescription(),
                requestDTO.getReorderPoint(),
                requestDTO.getTargetStockLevel(),
                requestDTO.getLeadTimeDays(),
                requestDTO.getUnitCost(),
                requestDTO.getImageUrl(),
                requestDTO.getNotes(),
                requestDTO.getInitialStock()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(productMapper.toResponseDTO(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "false") Boolean clearParent,
            @RequestParam(required = false, defaultValue = "false") Boolean clearPreferredSupplier,
            @Valid @RequestBody ProductRequestDTO requestDTO) {
        Product product = productService.updateProduct(
                id,
                requestDTO.getSku(),
                requestDTO.getCategoryId(),
                requestDTO.getParentId(),
                requestDTO.getLetter(),
                requestDTO.getTemplateQuantity(),
                requestDTO.getName(),
                requestDTO.getDescription(),
                requestDTO.getReorderPoint(),
                requestDTO.getTargetStockLevel(),
                requestDTO.getLeadTimeDays(),
                requestDTO.getUnitCost(),
                requestDTO.getImageUrl(),
                requestDTO.getNotes(),
                clearParent,
                requestDTO.getQuantity(),
                requestDTO.getPreferredSupplierId(),
                requestDTO.getPreferredSupplierAuto(),
                clearPreferredSupplier
        );
        return ResponseEntity.ok(productMapper.toResponseDTO(product));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deactivateProduct(@PathVariable UUID id) {
        productService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> activateProduct(@PathVariable UUID id) {
        productService.activateProduct(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Parent-Child Endpoints ====================

    /**
     * Get product with children loaded (for Kuji detail page)
     */
    @GetMapping("/{id}/with-children")
    public ResponseEntity<ProductResponseDTO> getProductWithChildren(@PathVariable UUID id) {
        Product product = productService.getProductByIdWithChildren(id);
        Integer totalChildStock = productService.getTotalChildStock(id);
        return ResponseEntity.ok(productMapper.toResponseDTOWithAggregates(product, totalChildStock));
    }

    /**
     * Get children of a product
     */
    @GetMapping("/{id}/children")
    public ResponseEntity<List<ProductSummaryDTO>> getProductChildren(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly) {
        List<Product> children = activeOnly
                ? productService.getActiveChildProducts(id)
                : productService.getChildProducts(id);
        return ResponseEntity.ok(productMapper.toSummaryDTOList(children));
    }
}
