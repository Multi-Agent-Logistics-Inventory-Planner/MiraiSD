package com.mirai.inventoryservice.catalog.api;

import com.mirai.inventoryservice.catalog.application.ProductListItemDTO;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.KujiType;
import com.mirai.inventoryservice.catalog.application.ProductDeletionCoordinator;
import com.mirai.inventoryservice.catalog.application.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;
    private final ProductMapper productMapper;
    private final ProductDeletionCoordinator productDeletionCoordinator;

    public ProductController(ProductService productService, ProductMapper productMapper,
                              ProductDeletionCoordinator productDeletionCoordinator) {
        this.productService = productService;
        this.productMapper = productMapper;
        this.productDeletionCoordinator = productDeletionCoordinator;
    }

    @GetMapping
    public ResponseEntity<List<ProductListItemDTO>> getAllProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly,
            @RequestParam(required = false, defaultValue = "false") Boolean rootOnly,
            @RequestParam(required = false, defaultValue = "false") Boolean kujiOnly,
            @RequestParam(required = false, defaultValue = "false") Boolean excludeCustomKuji,
            Authentication authentication) {
        List<ProductListItemDTO> products;

        if (search != null && !search.isBlank()) {
            products = productService.searchProductsAsListItems(search);
        } else if (rootOnly && Boolean.TRUE.equals(kujiOnly)) {
            products = productService.getRootKujiProductsAsListItems();
        } else if (rootOnly && activeOnly) {
            products = productService.getActiveRootProductsAsListItems();
        } else if (rootOnly) {
            products = productService.getRootProductsAsListItems();
        } else if (categoryId != null && activeOnly) {
            products = productService.getActiveProductsByCategoryAsListItems(categoryId);
        } else if (categoryId != null) {
            products = productService.getProductsByCategoryAsListItems(categoryId);
        } else if (activeOnly) {
            products = productService.getActiveProductsAsListItems();
        } else {
            products = productService.getAllProductsAsListItems();
        }

        if (Boolean.TRUE.equals(excludeCustomKuji)) {
            products = products.stream()
                    .filter(p -> p.getKujiType() != KujiType.CUSTOM)
                    .toList();
        }

        products.forEach(p -> CostVisibilityPolicy.applyCostVisibilityToListItem(p, authentication));
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable UUID id, Authentication authentication) {
        Product product = productService.getProductById(id);
        ProductResponseDTO dto = productMapper.toResponseDTO(product);
        // Enrich with last delivered supplier for "Use Auto" feature
        Object[] lastSupplier = productService.getLastDeliveredSupplier(id);
        if (lastSupplier != null && lastSupplier.length >= 2) {
            dto.setLastDeliveredSupplierId((UUID) lastSupplier[0]);
            dto.setLastDeliveredSupplierName((String) lastSupplier[1]);
        }
        CostVisibilityPolicy.applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductResponseDTO> getProductBySku(@PathVariable String sku, Authentication authentication) {
        Product product = productService.getProductBySku(sku);
        ProductResponseDTO dto = productMapper.toResponseDTO(product);
        // Enrich with last delivered supplier for "Use Auto" feature
        Object[] lastSupplier = productService.getLastDeliveredSupplier(product.getId());
        if (lastSupplier != null && lastSupplier.length >= 2) {
            dto.setLastDeliveredSupplierId((UUID) lastSupplier[0]);
            dto.setLastDeliveredSupplierName((String) lastSupplier[1]);
        }
        CostVisibilityPolicy.applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequestDTO requestDTO, Authentication authentication) {
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
                requestDTO.getMsrp(),
                requestDTO.getImageUrl(),
                requestDTO.getNotes(),
                requestDTO.getInitialStock(),
                requestDTO.getKujiType(),
                requestDTO.getKujiSlackWebhookUrl(),
                requestDTO.getPacksPerBox(),
                requestDTO.getForecastingEnabled()
        );
        ProductResponseDTO dto = productMapper.toResponseDTO(product);
        CostVisibilityPolicy.applyCostVisibility(dto, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "false") Boolean clearParent,
            @RequestParam(required = false, defaultValue = "false") Boolean clearPreferredSupplier,
            @RequestParam(required = false, defaultValue = "false") Boolean clearPacksPerBox,
            @Valid @RequestBody ProductRequestDTO requestDTO,
            Authentication authentication) {
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
                requestDTO.getMsrp(),
                requestDTO.getImageUrl(),
                requestDTO.getNotes(),
                clearParent,
                requestDTO.getQuantity(),
                requestDTO.getPreferredSupplierId(),
                requestDTO.getPreferredSupplierAuto(),
                clearPreferredSupplier,
                requestDTO.getKujiType(),
                requestDTO.getKujiSlackWebhookUrl(),
                requestDTO.getPacksPerBox(),
                clearPacksPerBox,
                requestDTO.getForecastingEnabled()
        );
        ProductResponseDTO dto = productMapper.toResponseDTO(product);
        CostVisibilityPolicy.applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
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
        productDeletionCoordinator.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Parent-Child Endpoints ====================

    /**
     * Get product with children loaded (for Kuji detail page)
     */
    @GetMapping("/{id}/with-children")
    public ResponseEntity<ProductResponseDTO> getProductWithChildren(@PathVariable UUID id, Authentication authentication) {
        Product product = productService.getProductByIdWithChildren(id);
        Integer totalChildStock = productService.getTotalChildStock(id);
        ProductResponseDTO dto = productMapper.toResponseDTOWithAggregates(product, totalChildStock);
        CostVisibilityPolicy.applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    /**
     * Get children of a product
     */
    @GetMapping("/{id}/children")
    public ResponseEntity<List<ProductListItemDTO>> getProductChildren(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly,
            Authentication authentication) {
        List<ProductListItemDTO> children = activeOnly
                ? productService.getActiveChildProductsAsListItems(id)
                : productService.getChildProductsAsListItems(id);
        children.forEach(p -> CostVisibilityPolicy.applyCostVisibilityToListItem(p, authentication));
        return ResponseEntity.ok(children);
    }

}
