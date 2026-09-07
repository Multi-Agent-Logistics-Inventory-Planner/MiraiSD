package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Global, cross-module product/category reads. Returns immutable {@link ProductRef}/
 * {@link CategoryRef} records, never the JPA entity and never cost/MSRP (see
 * {@link CatalogPricing} for that). Public shape recorded verbatim in
 * .specs/phase-5b-catalog-facade/log.md as Phase 6's external contract.
 */
@Service
@Transactional(readOnly = true)
public class CatalogQueries {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public CatalogQueries(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public Optional<ProductRef> findById(UUID productId) {
        return productRepository.findById(productId).map(ProductRef::from);
    }

    public ProductRef getById(UUID productId) {
        return findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
    }

    public List<ProductRef> findAllByIds(Collection<UUID> productIds) {
        return productRepository.findAllById(productIds).stream().map(ProductRef::from).toList();
    }

    /**
     * Every product, global (no site filter — catalog identity is global; see
     * docs/specs/multi-site-data-and-api.md). Replaces two full-table {@code ProductRepository}
     * scans in {@code AnalyticsService}. Backed by {@code ProductRepository.findAllProductRefs()},
     * a JPQL constructor-expression projection (same egress-cutting pattern as
     * {@code LIST_ITEM_SELECT}/{@code ProductListItemDTO}) — not {@code findAll()} plus in-memory
     * mapping, which would load every column of every full {@code Product} entity (description,
     * notes, parent association, children, timestamps) across the wire for fields {@link
     * ProductRef} never exposes. Verified against real Postgres in {@code CatalogQueriesEgressIT}:
     * one query, and — unlike a full-entity load — the same selected-column set as
     * {@code findAllAsListItems()}'s existing slim projection (no {@code description}/{@code
     * notes}, per AC-4).
     */
    public List<ProductRef> allProductRefs() {
        return productRepository.findAllProductRefs();
    }

    public List<ProductRef> findByCategoryIdActive(UUID categoryId) {
        return productRepository.findByCategoryIdAndIsActiveTrue(categoryId).stream()
                .map(ProductRef::from)
                .toList();
    }

    public List<CategoryRef> allCategoryRefs() {
        return categoryRepository.findAll().stream().map(CategoryRef::from).toList();
    }

    /**
     * Single-category lookup for a caller that only needs one category resolved (e.g. a
     * per-product report rendering one product's category name) — {@link #allCategoryRefs()}
     * would force a full table scan for that case.
     */
    public Optional<CategoryRef> findCategoryById(UUID categoryId) {
        return categoryRepository.findById(categoryId).map(CategoryRef::from);
    }
}
