package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateSkuException;
import com.mirai.inventoryservice.exceptions.ProductNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import com.mirai.inventoryservice.repositories.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product createProduct(String sku, ProductCategory category, ProductSubcategory subcategory,
                                 String name, String description, Integer reorderPoint,
                                 Integer targetStockLevel, Integer leadTimeDays,
                                 BigDecimal unitCost, String imageUrl, String notes) {
        if (sku != null && productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException("Product with SKU already exists: " + sku);
        }

        Product product = Product.builder()
                .sku(sku)
                .category(category)
                .subcategory(subcategory)
                .name(name)
                .description(description)
                .reorderPoint(reorderPoint != null ? reorderPoint : 10)
                .targetStockLevel(targetStockLevel != null ? targetStockLevel : 50)
                .leadTimeDays(leadTimeDays != null ? leadTimeDays : 7)
                .unitCost(unitCost)
                .imageUrl(imageUrl)
                .notes(notes)
                .isActive(true)
                .build();

        return productRepository.save(product);
    }

    public Product getProductById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
    }

    public Product getProductBySku(String sku) {
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with SKU: " + sku));
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> getActiveProducts() {
        return productRepository.findByIsActiveTrue();
    }

    public List<Product> getProductsByCategory(ProductCategory category) {
        return productRepository.findByCategory(category);
    }

    public List<Product> getActiveProductsByCategory(ProductCategory category) {
        return productRepository.findByCategoryAndIsActiveTrue(category);
    }

    public List<Product> searchProducts(String query) {
        return productRepository.search(query);
    }

    public Product updateProduct(UUID id, String sku, ProductCategory category, ProductSubcategory subcategory,
                                 String name, String description, Integer reorderPoint,
                                 Integer targetStockLevel, Integer leadTimeDays,
                                 BigDecimal unitCost, String imageUrl, String notes) {
        Product product = getProductById(id);

        if (sku != null && !sku.equals(product.getSku()) && productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException("Product with SKU already exists: " + sku);
        }

        if (sku != null) product.setSku(sku);
        if (category != null) product.setCategory(category);
        if (subcategory != null) product.setSubcategory(subcategory);
        if (name != null) product.setName(name);
        if (description != null) product.setDescription(description);
        if (reorderPoint != null) product.setReorderPoint(reorderPoint);
        if (targetStockLevel != null) product.setTargetStockLevel(targetStockLevel);
        if (leadTimeDays != null) product.setLeadTimeDays(leadTimeDays);
        if (unitCost != null) product.setUnitCost(unitCost);
        if (imageUrl != null) product.setImageUrl(imageUrl);
        if (notes != null) product.setNotes(notes);

        return productRepository.save(product);
    }

    public void deactivateProduct(UUID id) {
        Product product = getProductById(id);
        product.setIsActive(false);
        productRepository.save(product);
    }

    public void activateProduct(UUID id) {
        Product product = getProductById(id);
        product.setIsActive(true);
        productRepository.save(product);
    }

    public void deleteProduct(UUID id) {
        Product product = getProductById(id);
        productRepository.delete(product);
    }

    public boolean existsBySku(String sku) {
        return productRepository.existsBySku(sku);
    }
}
