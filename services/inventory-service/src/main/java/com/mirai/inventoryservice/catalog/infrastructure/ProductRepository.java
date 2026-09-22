package com.mirai.inventoryservice.catalog.infrastructure;

import com.mirai.inventoryservice.catalog.api.CatalogProductResponse;
import com.mirai.inventoryservice.catalog.application.ProductListItemDTO;
import com.mirai.inventoryservice.catalog.application.ProductRef;
import com.mirai.inventoryservice.catalog.application.ProductSettingsDefaults;
import com.mirai.inventoryservice.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    // Optimized queries with JOIN FETCH to avoid N+1 (includes parent for child products)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent LEFT JOIN FETCH p.parent")
    List<Product> findAllWithCategories();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent LEFT JOIN FETCH p.parent LEFT JOIN FETCH p.preferredSupplier WHERE p.id = :id")
    Optional<Product> findByIdWithCategories(@Param("id") UUID id);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent LEFT JOIN FETCH p.parent LEFT JOIN FETCH p.preferredSupplier WHERE p.sku = :sku")
    Optional<Product> findBySkuWithCategories(@Param("sku") String sku);

    // Keep original methods for cases where categories aren't needed
    List<Product> findByCategoryId(UUID categoryId);
    List<Product> findByIsActiveTrue();
    List<Product> findByCategoryIdAndIsActiveTrue(UUID categoryId);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND " +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Product> search(@Param("query") String query);

    // ==================== Parent-Child Methods ====================

    // Find children of a parent product
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.parent.id = :parentId ORDER BY p.sku")
    List<Product> findByParentIdWithCategories(@Param("parentId") UUID parentId);

    // Count children of a product
    @Query("SELECT COUNT(p) FROM Product p WHERE p.parent.id = :parentId")
    long countChildrenByParentId(@Param("parentId") UUID parentId);

    // Fetch product with parent eagerly loaded
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.parent LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.id = :id")
    Optional<Product> findByIdWithParent(@Param("id") UUID id);

    // Fetch product with children eagerly loaded
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.children LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent WHERE p.id = :id")
    Optional<Product> findByIdWithChildren(@Param("id") UUID id);

    // Sum children quantities for aggregation
    @Query("SELECT COALESCE(SUM(p.quantity), 0) FROM Product p WHERE p.parent.id = :parentId")
    Integer sumChildrenQuantities(@Param("parentId") UUID parentId);

    // Find products by preferred supplier
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent LEFT JOIN FETCH p.preferredSupplier WHERE p.preferredSupplier.id = :supplierId ORDER BY p.name")
    List<Product> findByPreferredSupplierIdWithCategories(@Param("supplierId") UUID supplierId);

    // Count products by preferred supplier
    @Query("SELECT COUNT(p) FROM Product p WHERE p.preferredSupplier.id = :supplierId")
    long countByPreferredSupplierId(@Param("supplierId") UUID supplierId);

    // ==================== Slim Projection Queries (list endpoints) ====================
    // Project flat columns into ProductListItemDTO. No JOIN FETCH — Hibernate does
    // not hydrate Product/Category/Supplier entities, just the named columns.
    // Drops description, notes, parent entity, children, createdAt to cut pooler egress.

    // hasChildren is a correlated EXISTS against the same Product table the outer query already
    // scans, computed in this one statement instead of a second, uncached full-table
    // `SELECT DISTINCT parent_id` per list call (the single most-called query in the database
    // before this fix -- see .specs/catalog-product-egress/spec.md AC-2).
    String LIST_ITEM_SELECT = "SELECT new com.mirai.inventoryservice.catalog.application.ProductListItemDTO("
            + "p.id, p.sku, p.name, p.imageUrl, p.isActive, "
            + "p.quantity, p.letter, p.templateQuantity, p.packsPerBox, "
            + "p.parentId, p.kujiType, p.kujiSlackWebhookUrl, "
            + "p.reorderPoint, p.targetStockLevel, p.leadTimeDays, "
            + "p.unitCost, p.msrp, "
            + "p.preferredSupplierId, s.displayName, p.preferredSupplierAuto, "
            + "p.updatedAt, "
            + "c.id, c.name, c.parentId, c.slug, c.displayOrder, c.isActive, c.usesPacks, "
            + "CASE WHEN EXISTS (SELECT 1 FROM Product ch WHERE ch.parentId = p.id) THEN true ELSE false END"
            + ") "
            + "FROM Product p "
            + "LEFT JOIN p.category c "
            + "LEFT JOIN p.preferredSupplier s ";

    @Query(LIST_ITEM_SELECT)
    List<ProductListItemDTO> findAllAsListItems();

    @Query(LIST_ITEM_SELECT + "WHERE p.isActive = true")
    List<ProductListItemDTO> findActiveAsListItems();

    @Query(LIST_ITEM_SELECT + "WHERE p.category.id = :categoryId")
    List<ProductListItemDTO> findByCategoryIdAsListItems(@Param("categoryId") UUID categoryId);

    @Query(LIST_ITEM_SELECT + "WHERE p.category.id = :categoryId AND p.isActive = true")
    List<ProductListItemDTO> findByCategoryIdActiveAsListItems(@Param("categoryId") UUID categoryId);

    @Query(LIST_ITEM_SELECT
            + "WHERE p.isActive = true AND ("
            + "LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR "
            + "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<ProductListItemDTO> searchAsListItems(@Param("query") String query);

    @Query(LIST_ITEM_SELECT + "WHERE p.parent IS NULL ORDER BY p.name")
    List<ProductListItemDTO> findRootAsListItems();

    @Query(LIST_ITEM_SELECT + "WHERE p.parent IS NULL AND p.isActive = true ORDER BY p.name")
    List<ProductListItemDTO> findRootActiveAsListItems();

    @Query(LIST_ITEM_SELECT
            + "WHERE p.parent IS NULL "
            + "AND EXISTS (SELECT 1 FROM Product ch WHERE ch.parent.id = p.id) "
            + "ORDER BY p.name")
    List<ProductListItemDTO> findRootKujiAsListItems();

    @Query(LIST_ITEM_SELECT + "WHERE p.parent.id = :parentId ORDER BY p.sku")
    List<ProductListItemDTO> findByParentIdAsListItems(@Param("parentId") UUID parentId);

    @Query(LIST_ITEM_SELECT + "WHERE p.parent.id = :parentId AND p.isActive = true ORDER BY p.sku")
    List<ProductListItemDTO> findByParentIdActiveAsListItems(@Param("parentId") UUID parentId);

    // ==================== Slim Projection Query (CatalogQueries.allProductRefs) ====================
    // Same egress-cutting rationale as LIST_ITEM_SELECT above: constructs ProductRef directly
    // (a record, so its canonical constructor's parameter order/types must match exactly) rather
    // than loading full Product entities via findAll(), which pulls description/notes/parent
    // entity/children/createdAt across the wire for fields ProductRef never exposes. Never
    // includes unitCost/msrp (ProductRef.from()'s own javadoc rule, enforced separately here too).

    @Query("SELECT new com.mirai.inventoryservice.catalog.application.ProductRef("
            + "p.id, p.sku, p.name, p.imageUrl, p.isActive, "
            + "p.quantity, p.letter, p.templateQuantity, p.packsPerBox, p.parentId, "
            + "p.kujiType, p.kujiSlackWebhookUrl, "
            + "p.reorderPoint, p.targetStockLevel, p.leadTimeDays, p.forecastingEnabled, "
            + "p.category.id, p.preferredSupplierId, p.preferredSupplierAuto) "
            + "FROM Product p")
    List<ProductRef> findAllProductRefs();

    // ==================== Slim Projection Query (SiteProductController.getSiteProducts) ====================
    // Every site-scoped product-settings list read (hit on every Products page load) needs only
    // these 9 scalar columns to compute EffectiveProductSettings -- no category, no parent, no
    // preferredSupplier join. Replaces ProductService.getAllProducts() -> findAllWithCategories(),
    // a JOIN FETCH across category/parent-category/parent-product for an endpoint that used none
    // of them (.specs/catalog-product-egress/spec.md AC-3).

    @Query("SELECT new com.mirai.inventoryservice.catalog.application.ProductSettingsDefaults("
            + "p.id, p.sku, p.name, p.unitCost, p.msrp, "
            + "p.reorderPoint, p.targetStockLevel, p.leadTimeDays, p.forecastingEnabled) "
            + "FROM Product p")
    List<ProductSettingsDefaults> findAllSettingsDefaults();

    // ==================== Slim Projection Query (CatalogProductController list) ====================
    // Master-identity-only fields for /api/v1/catalog/products -- deliberately never selects
    // isActive/quantity/unitCost/msrp/reorderPoint/targetStockLevel/leadTimeDays/forecastingEnabled
    // (site-owned data CatalogProductResponse's own contract forbids carrying), unlike
    // LIST_ITEM_SELECT above which the legacy, pre-multi-site /api/products route still needs.
    // hasChildren uses the same correlated EXISTS as LIST_ITEM_SELECT (see .specs/
    // catalog-product-egress/spec.md AC-2 and AC-5).
    String CATALOG_LIST_SELECT = "SELECT new com.mirai.inventoryservice.catalog.api.CatalogProductResponse("
            + "p.id, p.sku, c.id, c.name, p.parentId, "
            + "p.letter, p.templateQuantity, p.kujiType, p.kujiSlackWebhookUrl, p.packsPerBox, "
            + "p.name, p.description, p.imageUrl, p.notes, "
            + "p.preferredSupplierId, s.displayName, p.preferredSupplierAuto, "
            + "p.createdAt, p.updatedAt, "
            + "c.parentId, c.slug, c.displayOrder, c.isActive, c.usesPacks, "
            + "CASE WHEN EXISTS (SELECT 1 FROM Product ch WHERE ch.parentId = p.id) THEN true ELSE false END"
            + ") "
            + "FROM Product p "
            + "LEFT JOIN p.category c "
            + "LEFT JOIN p.preferredSupplier s ";

    @Query(CATALOG_LIST_SELECT)
    List<CatalogProductResponse> findAllCatalogListItems();

    @Query(CATALOG_LIST_SELECT + "WHERE p.category.id = :categoryId")
    List<CatalogProductResponse> findCatalogListItemsByCategoryId(@Param("categoryId") UUID categoryId);

    @Query(CATALOG_LIST_SELECT
            + "WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR "
            + "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<CatalogProductResponse> searchCatalogListItems(@Param("query") String query);
}
