package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import com.mirai.inventoryservice.catalog.infrastructure.SiteProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reads a {@link SiteProduct}'s current version in its own transaction and persistence context,
 * independent of any caller's. {@link SiteProductService#updateSettings} needs this after a
 * failed {@code saveAndFlush}: once a flush throws, the enclosing transaction's persistence
 * context must be treated as unusable for further work (the JPA provider requires the
 * transaction to roll back), so reading the row that actually won the race has to go through a
 * separate, fresh transaction ({@code REQUIRES_NEW}) rather than reusing the poisoned one -
 * reusing it risks a second failure there, surfacing as a 500 instead of the intended 409.
 */
@Component
class SiteProductVersionReader {

    private final SiteProductRepository siteProductRepository;

    SiteProductVersionReader(SiteProductRepository siteProductRepository) {
        this.siteProductRepository = siteProductRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Long currentVersion(UUID siteId, UUID productId) {
        return siteProductRepository.findBySiteIdAndProductId(siteId, productId)
                .map(SiteProduct::getVersion)
                .orElse(null);
    }
}
