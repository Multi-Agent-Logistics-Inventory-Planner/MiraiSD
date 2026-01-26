package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.ProductMapper;
import com.mirai.inventoryservice.dtos.requests.ProductRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ProductResponseDTO;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.services.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly) {
        List<Product> products;

        if (search != null && !search.isBlank()) {
            products = productService.searchProducts(search);
        } else if (category != null && activeOnly) {
            products = productService.getActiveProductsByCategory(category);
        } else if (category != null) {
            products = productService.getProductsByCategory(category);
        } else if (activeOnly) {
            products = productService.getActiveProducts();
        } else {
            products = productService.getAllProducts();
        }

        return ResponseEntity.ok(productMapper.toResponseDTOList(products));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable UUID id) {
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(productMapper.toResponseDTO(product));
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductResponseDTO> getProductBySku(@PathVariable String sku) {
        Product product = productService.getProductBySku(sku);
        return ResponseEntity.ok(productMapper.toResponseDTO(product));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequestDTO requestDTO) {
        Product product = productService.createProduct(
                requestDTO.getSku(),
                requestDTO.getCategory(),
                requestDTO.getSubcategory(),
                requestDTO.getName(),
                requestDTO.getDescription(),
                requestDTO.getReorderPoint(),
                requestDTO.getTargetStockLevel(),
                requestDTO.getLeadTimeDays(),
                requestDTO.getUnitCost(),
                requestDTO.getImageUrl(),
                requestDTO.getNotes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(productMapper.toResponseDTO(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequestDTO requestDTO) {
        Product product = productService.updateProduct(
                id,
                requestDTO.getSku(),
                requestDTO.getCategory(),
                requestDTO.getSubcategory(),
                requestDTO.getName(),
                requestDTO.getDescription(),
                requestDTO.getReorderPoint(),
                requestDTO.getTargetStockLevel(),
                requestDTO.getLeadTimeDays(),
                requestDTO.getUnitCost(),
                requestDTO.getImageUrl(),
                requestDTO.getNotes()
        );
        return ResponseEntity.ok(productMapper.toResponseDTO(product));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateProduct(@PathVariable UUID id) {
        productService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activateProduct(@PathVariable UUID id) {
        productService.activateProduct(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
