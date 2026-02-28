package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateSkuException;
import com.mirai.inventoryservice.exceptions.ProductNotFoundException;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
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
    private final CategoryService categoryService;

    public ProductService(
            ProductRepository productRepository,
            CategoryService categoryService) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
    }

    public Product createProduct(String sku, UUID categoryId,
                                 String name, String description, Integer reorderPoint,
                                 Integer targetStockLevel, Integer leadTimeDays,
                                 BigDecimal unitCost, String imageUrl, String notes) {
        Category category = categoryService.getCategoryById(categoryId);

        if (sku != null && productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException("Product with SKU already exists: " + sku);
        }

        Product product = Product.builder()
                .sku(sku)
                .category(category)
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
        return productRepository.findByIdWithCategories(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
    }

    public Product getProductBySku(String sku) {
        return productRepository.findBySkuWithCategories(sku)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with SKU: " + sku));
    }

    public List<Product> getAllProducts() {
        return productRepository.findAllWithCategories();
    }

    public List<Product> getActiveProducts() {
        return productRepository.findByIsActiveTrueWithCategories();
    }

    public List<Product> getProductsByCategory(UUID categoryId) {
        return productRepository.findByCategoryIdWithCategories(categoryId);
    }

    public List<Product> getActiveProductsByCategory(UUID categoryId) {
        return productRepository.findByCategoryIdAndIsActiveTrueWithCategories(categoryId);
    }

    public List<Product> searchProducts(String query) {
        return productRepository.searchWithCategories(query);
    }

    public Product updateProduct(UUID id, String sku, UUID categoryId,
                                 String name, String description, Integer reorderPoint,
                                 Integer targetStockLevel, Integer leadTimeDays,
                                 BigDecimal unitCost, String imageUrl, String notes) {
        Product product = getProductById(id);

        if (sku != null && !sku.equals(product.getSku()) && productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException("Product with SKU already exists: " + sku);
        }

        if (sku != null) product.setSku(sku);
        if (categoryId != null) {
            Category newCategory = categoryService.getCategoryById(categoryId);
            product.setCategory(newCategory);
        }
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
