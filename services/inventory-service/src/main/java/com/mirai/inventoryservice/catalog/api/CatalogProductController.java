package com.mirai.inventoryservice.catalog.api;

import com.mirai.inventoryservice.catalog.application.ProductDeletionCoordinator;
import com.mirai.inventoryservice.catalog.application.ProductService;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.identity.domain.Permission;
import com.mirai.inventoryservice.identity.domain.RolePermissions;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
            @RequestParam(required = false) String search,
            Authentication authentication) {
        // Slim projection, not getAllProducts()/getProductsByCategory()/searchAllProducts() ->
        // *WithCategories(): those JOIN FETCH full Product/Category/parent-Product entities for a
        // response that needs 24 scalar/nested-object columns and no entity hydration at all.
        // See .specs/catalog-product-egress/spec.md AC-5.
        List<CatalogProductResponse> body;
        if (search != null && !search.isBlank()) {
            body = productService.searchCatalogListItems(search);
        } else if (categoryId != null) {
            body = productService.getCatalogListItemsByCategory(categoryId);
        } else {
            body = productService.getAllCatalogListItems();
        }
        body.forEach(dto -> applyKujiWebhookVisibility(dto, authentication));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CatalogProductResponse> getCatalogProductById(
            @PathVariable UUID id, Authentication authentication) {
        Product product = productService.getProductById(id);
        CatalogProductResponse dto = catalogProductMapper.toResponse(product);
        dto.setHasChildren(productService.countChildren(product.getId()) > 0);
        applyKujiWebhookVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<CatalogProductResponse> getCatalogProductBySku(
            @PathVariable String sku, Authentication authentication) {
        Product product = productService.getProductBySku(sku);
        CatalogProductResponse dto = catalogProductMapper.toResponse(product);
        dto.setHasChildren(productService.countChildren(product.getId()) > 0);
        applyKujiWebhookVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    /**
     * kujiSlackWebhookUrl is a Slack incoming-webhook bearer credential, gated the same way as
     * the legacy list/detail routes -- see CostVisibilityPolicy's javadoc for the rationale.
     * Not routed through CostVisibilityPolicy itself: that class's methods are typed to
     * ProductResponseDTO/ProductListItemDTO, not this module's CatalogProductResponse.
     */
    private void applyKujiWebhookVisibility(CatalogProductResponse dto, Authentication authentication) {
        if (!RolePermissions.hasPermission(authentication, Permission.PRODUCTS_UPDATE)) {
            dto.setKujiSlackWebhookUrl(null);
        }
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
