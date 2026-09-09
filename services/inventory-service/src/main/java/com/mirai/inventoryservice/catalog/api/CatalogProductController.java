package com.mirai.inventoryservice.catalog.api;

import com.mirai.inventoryservice.catalog.application.ProductDeletionCoordinator;
import com.mirai.inventoryservice.catalog.application.ProductService;
import com.mirai.inventoryservice.catalog.domain.Product;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Global catalog v1 product routes (spec.md phase-5d "Product decisions"). Master-identity-only:
 * {@link CatalogProductRequest}/{@link CatalogProductResponse} deliberately omit every site-owned
 * field, so posting one is rejected with a 400 (Jackson's default fail-on-unknown-properties)
 * rather than silently ignored. {@code POST} creates zero {@code site_products} rows - a new
 * product is carried nowhere until an explicit assortment mutation adds it to a site. Handler and
 * operation names are distinct from the legacy {@link ProductController} so springdoc doesn't
 * collide their operation IDs. The legacy {@code /api/products} routes are untouched.
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
public class CatalogProductController {
    private final ProductService productService;
    private final CatalogProductMapper catalogProductMapper;
    private final ProductDeletionCoordinator productDeletionCoordinator;

    public CatalogProductController(ProductService productService,
                                     CatalogProductMapper catalogProductMapper,
                                     ProductDeletionCoordinator productDeletionCoordinator) {
        this.productService = productService;
        this.catalogProductMapper = catalogProductMapper;
        this.productDeletionCoordinator = productDeletionCoordinator;
    }

    @GetMapping
    public ResponseEntity<List<CatalogProductResponse>> getCatalogProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search) {
        List<Product> products;
        if (search != null && !search.isBlank()) {
            products = productService.searchAllProducts(search);
        } else if (categoryId != null) {
            products = productService.getProductsByCategory(categoryId);
        } else {
            products = productService.getAllProducts();
        }
        List<CatalogProductResponse> body = products.stream().map(catalogProductMapper::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CatalogProductResponse> getCatalogProductById(@PathVariable UUID id) {
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(catalogProductMapper.toResponse(product));
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<CatalogProductResponse> getCatalogProductBySku(@PathVariable String sku) {
        Product product = productService.getProductBySku(sku);
        return ResponseEntity.ok(catalogProductMapper.toResponse(product));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<CatalogProductResponse> createCatalogProduct(@Valid @RequestBody CatalogProductRequest requestDTO) {
        Product product = productService.createGlobalProduct(
                requestDTO.getSku(),
                requestDTO.getCategoryId(),
                requestDTO.getParentId(),
                requestDTO.getLetter(),
                requestDTO.getTemplateQuantity(),
                requestDTO.getName(),
                requestDTO.getDescription(),
                requestDTO.getImageUrl(),
                requestDTO.getNotes(),
                requestDTO.getKujiType(),
                requestDTO.getPacksPerBox());
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogProductMapper.toResponse(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<CatalogProductResponse> updateCatalogProduct(
            @PathVariable UUID id, @Valid @RequestBody CatalogProductRequest requestDTO) {
        Product product = productService.updateGlobalProduct(
                id,
                requestDTO.getSku(),
                requestDTO.getCategoryId(),
                requestDTO.getParentId(),
                requestDTO.getLetter(),
                requestDTO.getTemplateQuantity(),
                requestDTO.getName(),
                requestDTO.getDescription(),
                requestDTO.getImageUrl(),
                requestDTO.getNotes(),
                requestDTO.getKujiType(),
                requestDTO.getPacksPerBox());
        return ResponseEntity.ok(catalogProductMapper.toResponse(product));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteCatalogProduct(@PathVariable UUID id) {
        productDeletionCoordinator.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
