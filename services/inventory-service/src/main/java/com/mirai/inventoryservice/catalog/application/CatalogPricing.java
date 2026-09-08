package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cost/MSRP reads, isolated from {@link CatalogQueries} so money access stays greppable and
 * role-gate-able (docs: .specs/phase-5b-catalog-facade/spec.md, Product decisions). Callers apply
 * their own role-based redaction on the values returned here (see
 * {@code catalog.api.CostVisibilityPolicy}) — this facade does not redact.
 */
@Service
@Transactional(readOnly = true)
public class CatalogPricing {

    private final ProductRepository productRepository;

    public CatalogPricing(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Optional<ProductPricing> findPricing(UUID productId) {
        return productRepository.findById(productId).map(ProductPricing::from);
    }

    public ProductPricing getPricing(UUID productId) {
        return findPricing(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
    }

    public List<ProductPricing> findPricingForIds(Collection<UUID> productIds) {
        return productRepository.findAllById(productIds).stream().map(ProductPricing::from).toList();
    }
}
