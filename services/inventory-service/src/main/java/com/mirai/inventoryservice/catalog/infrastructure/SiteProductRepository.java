package com.mirai.inventoryservice.catalog.infrastructure;

import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteProductRepository extends JpaRepository<SiteProduct, UUID> {
    Optional<SiteProduct> findBySiteIdAndProductId(UUID siteId, UUID productId);

    /**
     * Product ids stocked at one site. A projection, not {@code findBySiteIdAndIsStockedTrue} plus
     * in-memory mapping - the assortment read facade only ever needs the id set.
     */
    @Query("SELECT sp.productId FROM SiteProduct sp WHERE sp.siteId = :siteId AND sp.isStocked = true")
    List<UUID> findStockedProductIdsBySiteId(@Param("siteId") UUID siteId);

    /**
     * Every row (stocked or de-assorted-with-overrides) for one site - used to bulk-resolve
     * {@link com.mirai.inventoryservice.catalog.application.EffectiveProductSettings} for a
     * site's whole product list in one query instead of one lookup per product.
     */
    List<SiteProduct> findBySiteId(UUID siteId);
}
