package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateSkuException;
import com.mirai.inventoryservice.exceptions.ProductNotFoundException;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.services.InventoryAggregateService;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final StockMovementService stockMovementService;
    private final SupabaseBroadcastService broadcastService;
    private final InventoryAggregateService inventoryAggregateService;
    private final StockMovementRepository stockMovementRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryService categoryService,
            @Lazy StockMovementService stockMovementService,
            SupabaseBroadcastService broadcastService,
            InventoryAggregateService inventoryAggregateService,
            StockMovementRepository stockMovementRepository) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.stockMovementService = stockMovementService;
        this.broadcastService = broadcastService;
        this.inventoryAggregateService = inventoryAggregateService;
        this.stockMovementRepository = stockMovementRepository;
    }

    public Product createProduct(String sku, UUID categoryId, UUID parentId,
                                 String letter, String name, String description, Integer reorderPoint,
                                 Integer targetStockLevel, Integer leadTimeDays,
                                 BigDecimal unitCost, String imageUrl, String notes,
                                 Integer initialStock) {
        // Validate parent if provided
        Product parent = null;
        Category category;
        if (parentId != null) {
            parent = getProductById(parentId);
            // Validate: parent cannot itself have a parent (single-level hierarchy)
            if (parent.getParentId() != null) {
                throw new IllegalArgumentException("Cannot create child of a child product. Only single-level hierarchy allowed.");
            }
            // Prizes inherit parent's category when not specified
            category = categoryId != null
                    ? categoryService.getCategoryById(categoryId)
                    : parent.getCategory();
        } else {
            if (categoryId == null) {
                throw new IllegalArgumentException("Category is required for root products.");
            }
            category = categoryService.getCategoryById(categoryId);
        }

        if (sku != null && productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException("Product with SKU already exists: " + sku);
        }

        // If initial stock is provided, product starts active; otherwise inactive
        boolean startsActive = initialStock != null && initialStock > 0;

        Product product = Product.builder()
                .sku(sku)
                .letter(letter != null && !letter.isBlank() ? letter.trim().substring(0, Math.min(2, letter.trim().length())) : null)
                .category(category)
                .parent(parent)
                .name(name)
                .description(description)
                .reorderPoint(reorderPoint != null ? reorderPoint : 10)
                .targetStockLevel(targetStockLevel != null ? targetStockLevel : 50)
                .leadTimeDays(leadTimeDays != null ? leadTimeDays : 7)
                .unitCost(unitCost)
                .imageUrl(imageUrl)
                .notes(notes)
                .isActive(startsActive)
                .build();

        Product savedProduct = productRepository.save(product);

        // Broadcast product creation so clients refresh product lists/details
        broadcastService.broadcastProductUpdated(List.of(savedProduct.getId().toString()));

        // If initial stock provided, create tracked inventory in NotAssigned
        if (initialStock != null && initialStock > 0) {
            stockMovementService.createInventoryWithTracking(
                    LocationType.NOT_ASSIGNED,
                    null,
                    savedProduct,
                    initialStock,
                    StockMovementReason.INITIAL_STOCK,
                    null,
                    "Initial stock on product creation"
            );
        }

        return savedProduct;
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

    public Product updateProduct(UUID id, String sku, UUID categoryId, UUID parentId,
                                 String letter, String name, String description, Integer reorderPoint,
                                 Integer targetStockLevel, Integer leadTimeDays,
                                 BigDecimal unitCost, String imageUrl, String notes,
                                 Boolean clearParent) {
        Product product = getProductById(id);

        if (sku != null && !sku.equals(product.getSku()) && productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException("Product with SKU already exists: " + sku);
        }

        // Handle parent change
        if (parentId != null && !parentId.equals(product.getParentId())) {
            Product newParent = getProductById(parentId);
            // Validate single-level hierarchy
            if (newParent.getParentId() != null) {
                throw new IllegalArgumentException("Cannot set parent to a child product. Only single-level hierarchy allowed.");
            }
            // Validate not creating circular reference
            if (newParent.getId().equals(id)) {
                throw new IllegalArgumentException("Product cannot be its own parent.");
            }
            // Validate product doesn't have children (can't become a child if it's a parent)
            if (productRepository.countChildrenByParentId(id) > 0) {
                throw new IllegalArgumentException("Cannot set parent on a product that has children.");
            }
            product.setParent(newParent);
        } else if (Boolean.TRUE.equals(clearParent) && product.getParentId() != null) {
            // Explicitly clearing parent - making it a root product
            product.setParent(null);
        }

        if (sku != null) product.setSku(sku);
        if (letter != null) {
            String trimmed = letter.trim();
            product.setLetter(trimmed.isEmpty() ? null : trimmed.substring(0, Math.min(2, trimmed.length())));
        }
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

        Product saved = productRepository.save(product);
        broadcastService.broadcastProductUpdated(List.of(saved.getId().toString()));
        return saved;
    }

    public void deactivateProduct(UUID id) {
        Product product = getProductById(id);
        product.setIsActive(false);
        Product saved = productRepository.save(product);
        broadcastService.broadcastProductUpdated(List.of(saved.getId().toString()));
    }

    public void activateProduct(UUID id) {
        Product product = getProductById(id);
        product.setIsActive(true);
        Product saved = productRepository.save(product);
        broadcastService.broadcastProductUpdated(List.of(saved.getId().toString()));
    }

    public void deleteProduct(UUID id) {
        Product product = getProductById(id);

        // If parent has children, delete children first (cascade), then parent
        long childCount = productRepository.countChildrenByParentId(id);
        if (childCount > 0) {
            List<Product> children = productRepository.findByParentIdWithCategories(id);
            for (Product child : children) {
                inventoryAggregateService.deleteAllInventoryForProduct(child.getId());
                stockMovementRepository.deleteByItem_Id(child.getId());
                productRepository.delete(child);
                broadcastService.broadcastProductUpdated(List.of(child.getId().toString()));
            }
        }

        // Delete parent's (or single product's) inventory and stock movements before deleting the product
        inventoryAggregateService.deleteAllInventoryForProduct(id);
        stockMovementRepository.deleteByItem_Id(id);
        productRepository.delete(product);
        broadcastService.broadcastProductUpdated(List.of(id.toString()));
    }

    public boolean existsBySku(String sku) {
        return productRepository.existsBySku(sku);
    }

    // ==================== Parent-Child Methods ====================

    /**
     * Get root products only (no parent) for main product listing
     */
    public List<Product> getRootProducts() {
        return productRepository.findRootProductsWithCategories();
    }

    /**
     * Get active root products only
     */
    public List<Product> getActiveRootProducts() {
        return productRepository.findRootProductsWithCategoriesActive();
    }

    /**
     * Get children of a parent product
     */
    public List<Product> getChildProducts(UUID parentId) {
        return productRepository.findByParentIdWithCategories(parentId);
    }

    /**
     * Get active children of a parent product
     */
    public List<Product> getActiveChildProducts(UUID parentId) {
        return productRepository.findByParentIdAndIsActiveTrueWithCategories(parentId);
    }

    /**
     * Get product by ID with children loaded
     */
    public Product getProductByIdWithChildren(UUID id) {
        return productRepository.findByIdWithChildren(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
    }

    /**
     * Get product by ID with parent loaded
     */
    public Product getProductByIdWithParent(UUID id) {
        return productRepository.findByIdWithParent(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
    }

    /**
     * Get aggregated total stock of all children
     */
    public Integer getTotalChildStock(UUID parentId) {
        return productRepository.sumChildrenQuantities(parentId);
    }

    /**
     * Count children of a product
     */
    public long countChildren(UUID parentId) {
        return productRepository.countChildrenByParentId(parentId);
    }
}
