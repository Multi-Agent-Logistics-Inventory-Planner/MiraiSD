package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.DuplicateSkuException;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.domain.SupplierNotFoundException;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.Supplier;
import com.mirai.inventoryservice.catalog.infrastructure.SupplierRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.services.SupabaseBroadcastService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final SupabaseBroadcastService broadcastService;
    private final SupplierRepository supplierRepository;
    private final InitialStockPort initialStockPort;
    private final ForecastPurgePort forecastPurgePort;
    private final SupplierDeliveryHistoryPort supplierDeliveryHistoryPort;
    private final OpenKujiBoxPort openKujiBoxPort;
    private final SiteProductService siteProductService;
    private final MainSiteResolver mainSiteResolver;

    public ProductService(
            ProductRepository productRepository,
            CategoryService categoryService,
            SupabaseBroadcastService broadcastService,
            SupplierRepository supplierRepository,
            InitialStockPort initialStockPort,
            ForecastPurgePort forecastPurgePort,
            SupplierDeliveryHistoryPort supplierDeliveryHistoryPort,
            OpenKujiBoxPort openKujiBoxPort,
            SiteProductService siteProductService,
            MainSiteResolver mainSiteResolver) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.broadcastService = broadcastService;
        this.supplierRepository = supplierRepository;
        this.initialStockPort = initialStockPort;
        this.forecastPurgePort = forecastPurgePort;
        this.supplierDeliveryHistoryPort = supplierDeliveryHistoryPort;
        this.openKujiBoxPort = openKujiBoxPort;
        this.siteProductService = siteProductService;
        this.mainSiteResolver = mainSiteResolver;
    }

    public Product createProduct(String sku, UUID categoryId, UUID parentId,
                                 String letter, Integer templateQuantity, String name, String description, Integer reorderPoint,
                                 Integer targetStockLevel, Integer leadTimeDays,
                                 BigDecimal unitCost, BigDecimal msrp, String imageUrl, String notes,
                                 Integer initialStock,
                                 com.mirai.inventoryservice.catalog.domain.KujiType kujiType,
                                 String kujiSlackWebhookUrl,
                                 Integer packsPerBox,
                                 Boolean forecastingEnabled) {
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

        // kuji_type and kuji_slack_webhook_url are only valid on root products
        if (parentId != null && (kujiType != null || (kujiSlackWebhookUrl != null && !kujiSlackWebhookUrl.isBlank()))) {
            throw new IllegalArgumentException("kujiType and kujiSlackWebhookUrl can only be set on root products.");
        }

        // If initial stock is provided, product starts active; otherwise inactive
        boolean startsActive = initialStock != null && initialStock > 0;

        Product product = Product.builder()
                .sku(sku)
                .letter(letter != null && !letter.isBlank() ? letter.trim().substring(0, Math.min(50, letter.trim().length())) : null)
                .templateQuantity(templateQuantity)
                .kujiType(kujiType)
                .kujiSlackWebhookUrl(kujiSlackWebhookUrl != null && !kujiSlackWebhookUrl.isBlank() ? kujiSlackWebhookUrl.trim() : null)
                .packsPerBox(packsPerBox)
                .category(category)
                .parent(parent)
                .name(name)
                .description(description)
                .reorderPoint(reorderPoint != null ? reorderPoint : 10)
                .targetStockLevel(targetStockLevel != null ? targetStockLevel : 50)
                .leadTimeDays(leadTimeDays != null ? leadTimeDays : 14)
                .unitCost(unitCost)
                .msrp(msrp)
                .imageUrl(imageUrl)
                .notes(notes)
                .isActive(startsActive)
                .forecastingEnabled(forecastingEnabled == null ? Boolean.TRUE : forecastingEnabled)
                .build();

        Product savedProduct = productRepository.save(product);

        // Broadcast product creation so clients refresh product lists/details
        broadcastService.broadcastProductUpdated(List.of(savedProduct.getId().toString()));

        // If initial stock provided, create inventory
        // Only create tracked inventory (with audit log) for parent products
        // Prize products (with parentId) skip audit logging
        if (initialStock != null && initialStock > 0) {
            if (parentId == null) {
                // Parent product - create tracked inventory with audit log
                initialStockPort.recordInitialStock(savedProduct, initialStock);
            } else {
                // Prize product - just set quantity without audit log
                savedProduct.setQuantity(initialStock);
                savedProduct = productRepository.save(savedProduct);
            }
        }

        // AC-5 (legacy create): every product gets a MAIN site_products row with NULL overrides,
        // matching V57's backfill shape - is_stocked seeded from the same startsActive derivation
        // used for products.is_active, not a request field ProductRequestDTO doesn't have.
        siteProductService.setStocked(mainSiteResolver.resolve(), savedProduct.getId(), startsActive);

        return savedProduct;
    }

    public Product getProductById(UUID id) {
        return productRepository.findByIdWithCategories(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
    }

    /**
     * Get the last delivered supplier for a product.
     * Returns [supplier_id (UUID), supplier_display_name (String)] or null if no delivered shipments.
     */
    public Object[] getLastDeliveredSupplier(UUID productId) {
        return supplierDeliveryHistoryPort.findLastDeliveredSupplier(productId);
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
                                 String letter, Integer templateQuantity, String name, String description, Integer reorderPoint,
                                 Integer targetStockLevel, Integer leadTimeDays,
                                 BigDecimal unitCost, BigDecimal msrp, String imageUrl, String notes,
                                 Boolean clearParent, Integer quantity,
                                 UUID preferredSupplierId, Boolean preferredSupplierAuto,
                                 Boolean clearPreferredSupplier,
                                 com.mirai.inventoryservice.catalog.domain.KujiType kujiType,
                                 String kujiSlackWebhookUrl,
                                 Integer packsPerBox,
                                 Boolean clearPacksPerBox,
                                 Boolean forecastingEnabled) {
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
            product.setLetter(trimmed.isEmpty() ? null : trimmed.substring(0, Math.min(50, trimmed.length())));
        }
        if (templateQuantity != null) product.setTemplateQuantity(templateQuantity);
        if (kujiType != null || kujiSlackWebhookUrl != null) {
            // kuji_type and kuji_slack_webhook_url only valid on root products
            if (product.getParentId() != null) {
                throw new IllegalArgumentException("kujiType and kujiSlackWebhookUrl can only be set on root products.");
            }
            if (kujiType != null) product.setKujiType(kujiType);
            if (kujiSlackWebhookUrl != null) {
                product.setKujiSlackWebhookUrl(kujiSlackWebhookUrl.isBlank() ? null : kujiSlackWebhookUrl.trim());
            }
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
        if (msrp != null) product.setMsrp(msrp);
        if (imageUrl != null) product.setImageUrl(imageUrl);
        if (notes != null) product.setNotes(notes);
        if (Boolean.TRUE.equals(clearPacksPerBox)) {
            product.setPacksPerBox(null);
        } else if (packsPerBox != null) {
            product.setPacksPerBox(packsPerBox);
        }
        // Allow direct quantity update only for prize products (products with a parent)
        if (quantity != null && product.getParentId() != null) {
            product.setQuantity(quantity);
        }

        // Handle preferred supplier
        if (preferredSupplierId != null) {
            Supplier supplier = supplierRepository.findById(preferredSupplierId)
                .orElseThrow(() -> new SupplierNotFoundException("Supplier not found with id: " + preferredSupplierId));
            product.setPreferredSupplier(supplier);
            product.setPreferredSupplierAuto(Boolean.TRUE.equals(preferredSupplierAuto));
        } else if (Boolean.TRUE.equals(clearPreferredSupplier)) {
            product.setPreferredSupplier(null);
            product.setPreferredSupplierAuto(null);
        } else if (Boolean.TRUE.equals(preferredSupplierAuto) && product.getPreferredSupplierId() != null) {
            // Switching existing supplier to auto mode (keeps supplier, enables auto-update on delivery)
            product.setPreferredSupplierAuto(true);
        }

        // Capture the pre-update value so we can detect a true -> false transition
        // and purge existing forecast_predictions rows in the same transaction.
        boolean wasForecastingEnabled = !Boolean.FALSE.equals(product.getForecastingEnabled());
        boolean turningForecastingOff = false;
        if (forecastingEnabled != null) {
            product.setForecastingEnabled(forecastingEnabled);
            turningForecastingOff = wasForecastingEnabled && Boolean.FALSE.equals(forecastingEnabled);
        }

        productRepository.save(product);

        // AC-5 (legacy-write -> site-read): sync this call's changed fields into MAIN's
        // site_products row, but only where MAIN already holds an override for that field -
        // a field MAIN inherits is left alone so this edit doesn't manufacture an override it
        // never asked for. Uses the raw request values, not the entity's post-update state, so
        // "field not provided in this call" and "field provided" keep the same meaning here as
        // they do for products.* just above.
        siteProductService.syncExistingMainOverrides(
                mainSiteResolver.resolve(),
                product.getId(),
                new SiteProductSettingsUpdate(
                        forecastingEnabled, unitCost, msrp, reorderPoint, targetStockLevel, leadTimeDays));

        if (turningForecastingOff) {
            forecastPurgePort.purgeForecastsForProduct(product.getId());
        }
        broadcastService.broadcastProductUpdated(List.of(product.getId().toString()));
        // Re-fetch to ensure preferredSupplier is eagerly loaded via JOIN FETCH
        return productRepository.findByIdWithCategories(product.getId())
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + product.getId()));
    }

    public void deactivateProduct(UUID id) {
        Product product = getProductById(id);
        // AC-5: setStocked both saves products.is_active and syncs MAIN's site_products.is_stocked
        // atomically, upserting the MAIN row if this product somehow doesn't have one yet.
        siteProductService.setStocked(mainSiteResolver.resolve(), id, false);
        broadcastService.broadcastProductUpdated(List.of(product.getId().toString()));
    }

    public void activateProduct(UUID id) {
        Product product = getProductById(id);
        siteProductService.setStocked(mainSiteResolver.resolve(), id, true);
        broadcastService.broadcastProductUpdated(List.of(product.getId().toString()));
    }

    // deleteProduct moved to ProductDeletionCoordinator (docs:
    // .specs/phase-5a-catalog-module-move/spec.md T-4) — it required six foreign repositories
    // this class must not depend on. Callers use ProductDeletionCoordinator.deleteProduct(id).

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
     * Get root products that have at least one child (Kuji parents only)
     */
    public List<Product> getRootKujiProducts() {
        return productRepository.findRootKujiProductsWithCategories();
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

    /**
     * Get set of all product IDs that have children (for efficient hasChildren computation)
     */
    public Set<UUID> getParentProductIds() {
        return new HashSet<>(productRepository.findAllParentIds());
    }

    // ==================== Slim List-Item Methods (egress optimization) ====================
    // Return projected ProductListItemDTO directly instead of full Product entities.
    // Fills in hasChildren via a single parentIds batch lookup (existing findAllParentIds).

    private List<ProductListItemDTO> withHasChildren(List<ProductListItemDTO> items) {
        Set<UUID> parentIds = getParentProductIds();
        for (ProductListItemDTO item : items) {
            item.setHasChildren(parentIds.contains(item.getId()));
        }
        return items;
    }

    private List<ProductListItemDTO> withHasActiveBox(List<ProductListItemDTO> items) {
        Set<UUID> activeBoxProductIds = openKujiBoxPort.findProductIdsWithOpenBox();
        for (ProductListItemDTO item : items) {
            item.setHasActiveBox(activeBoxProductIds.contains(item.getId()));
        }
        return items;
    }

    public List<ProductListItemDTO> getAllProductsAsListItems() {
        return withHasChildren(productRepository.findAllAsListItems());
    }

    public List<ProductListItemDTO> getActiveProductsAsListItems() {
        return withHasChildren(productRepository.findActiveAsListItems());
    }

    public List<ProductListItemDTO> getProductsByCategoryAsListItems(UUID categoryId) {
        return withHasChildren(productRepository.findByCategoryIdAsListItems(categoryId));
    }

    public List<ProductListItemDTO> getActiveProductsByCategoryAsListItems(UUID categoryId) {
        return withHasChildren(productRepository.findByCategoryIdActiveAsListItems(categoryId));
    }

    public List<ProductListItemDTO> searchProductsAsListItems(String query) {
        return withHasChildren(productRepository.searchAsListItems(query));
    }

    public List<ProductListItemDTO> getRootProductsAsListItems() {
        return withHasActiveBox(withHasChildren(productRepository.findRootAsListItems()));
    }

    public List<ProductListItemDTO> getActiveRootProductsAsListItems() {
        return withHasActiveBox(withHasChildren(productRepository.findRootActiveAsListItems()));
    }

    public List<ProductListItemDTO> getRootKujiProductsAsListItems() {
        return withHasActiveBox(withHasChildren(productRepository.findRootKujiAsListItems()));
    }

    public List<ProductListItemDTO> getChildProductsAsListItems(UUID parentId) {
        return withHasChildren(productRepository.findByParentIdAsListItems(parentId));
    }

    public List<ProductListItemDTO> getActiveChildProductsAsListItems(UUID parentId) {
        return withHasChildren(productRepository.findByParentIdActiveAsListItems(parentId));
    }
}
