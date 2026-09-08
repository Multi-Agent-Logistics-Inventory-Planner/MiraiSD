package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SiteProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Site-scoped catalog reads (.specs/phase-5c-site-products AC-4). Every method requires a
 * {@code siteId} and resolves through {@link SiteProductRepository#findBySiteIdAndProductId} -
 * never a bare id lookup followed by an in-memory site check - so a foreign site's row can never
 * leak into another site's read.
 */
@Service
@Transactional(readOnly = true)
public class SiteAssortment {

    private final SiteProductRepository siteProductRepository;
    private final ProductRepository productRepository;

    public SiteAssortment(SiteProductRepository siteProductRepository, ProductRepository productRepository) {
        this.siteProductRepository = siteProductRepository;
        this.productRepository = productRepository;
    }

    /**
     * The effective settings for one product at one site - absent-row and retained-row cases are
     * both routed through {@link EffectiveProductSettings}, never resolved ad hoc here.
     */
    public EffectiveProductSettings effectiveSettingsFor(UUID siteId, UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
        return siteProductRepository.findBySiteIdAndProductId(siteId, productId)
                .map(siteProduct -> EffectiveProductSettings.from(siteProduct, product))
                .orElseGet(() -> EffectiveProductSettings.absent(siteId, product));
    }

    /** {@code true} only when a row exists and is stocked - an absent row is never stocked. */
    public boolean isStockedAt(UUID siteId, UUID productId) {
        return siteProductRepository.findBySiteIdAndProductId(siteId, productId)
                .map(SiteProduct::getIsStocked)
                .orElse(false);
    }

    /** Product ids carried (stocked) at this site. Empty for a site with no assortment rows yet. */
    public Set<UUID> stockedProductIds(UUID siteId) {
        return Set.copyOf(siteProductRepository.findStockedProductIdsBySiteId(siteId));
    }
}
