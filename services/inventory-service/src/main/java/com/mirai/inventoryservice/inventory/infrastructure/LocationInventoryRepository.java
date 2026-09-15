package com.mirai.inventoryservice.inventory.infrastructure;

import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationInventoryRepository extends JpaRepository<LocationInventory, UUID> {
    /**
     * Lists displayable inventory at a location. Excludes:
     *  - child products (parent IS NOT NULL): kuji prize children, PREMADE (tracking-only)
     *    and CUSTOM (managed in the dedicated kuji UI), never appear as stockable rack items.
     *  - CUSTOM kuji parents: their lifecycle lives on KujiBox; convention is no
     *    location_inventory row, but the filter defends against stray ones.
     */
    @Query("""
        SELECT li FROM LocationInventory li
        JOIN FETCH li.location l
        JOIN FETCH l.storageLocation sl
        JOIN FETCH li.product p
        WHERE li.location.id = :locationId
          AND p.parent IS NULL
          AND (p.kujiType IS NULL OR p.kujiType <> com.mirai.inventoryservice.catalog.domain.KujiType.CUSTOM)
        """)
    List<LocationInventory> findByLocation_Id(@Param("locationId") UUID locationId);

    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl WHERE li.product.id = :productId")
    List<LocationInventory> findByProduct_Id(@Param("productId") UUID productId);

    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl JOIN FETCH li.product WHERE li.location.id = :locationId AND li.product.id = :productId")
    Optional<LocationInventory> findByLocation_IdAndProduct_Id(@Param("locationId") UUID locationId, @Param("productId") UUID productId);

    @Query("SELECT li FROM LocationInventory li WHERE li.location.id = :locationId AND li.product.id IN :productIds")
    List<LocationInventory> findByLocation_IdAndProduct_IdIn(@Param("locationId") UUID locationId, @Param("productIds") Collection<UUID> productIds);

    /**
     * Batch fetch LocationInventory rows by id with all associations needed for
     * stock-movement bookkeeping eager-loaded (location, storage location, product,
     * product.parent). Used by batch-adjust and batch-transfer to avoid N+1 lazy
     * fetches during the validation + outbox-event loops.
     */
    @Query("""
        SELECT li FROM LocationInventory li
        JOIN FETCH li.location l
        JOIN FETCH l.storageLocation sl
        JOIN FETCH li.product p
        LEFT JOIN FETCH p.parent
        WHERE li.id IN :ids
        """)
    List<LocationInventory> findAllByIdWithGraph(@Param("ids") Collection<UUID> ids);

    /**
     * Sum on-hand quantity grouped by product id for the given products in one query.
     * Returns rows of [productId UUID, totalQuantity Long]; missing products mean total = 0.
     */
    @Query("""
        SELECT li.product.id, SUM(li.quantity)
        FROM LocationInventory li
        WHERE li.product.id IN :productIds
        GROUP BY li.product.id
        """)
    List<Object[]> sumQuantitiesByProductIds(@Param("productIds") Collection<UUID> productIds);

    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl JOIN FETCH li.product WHERE li.site.id = :siteId")
    List<LocationInventory> findBySite_Id(@Param("siteId") UUID siteId);

    /**
     * Restored (.specs/phase-6-inventory 6e, R-3 revert, 2026-09-15) alongside
     * {@code LocationInventoryController}. Now applies the same kuji-child/CUSTOM-kuji-parent
     * exclusion {@link #findByLocation_Id} always has -- 6d recorded the previous, unfiltered
     * version of this method as "a trap for a future, not-yet-existing caller"; that caller
     * (the restored {@code LocationInventoryService.listInventoryByStorageLocation}) exists
     * again now, so the query itself is fixed rather than re-shipping the trap.
     */
    @Query("""
        SELECT li FROM LocationInventory li
        JOIN FETCH li.location l
        JOIN FETCH l.storageLocation sl
        JOIN FETCH li.product p
        WHERE sl.id = :storageLocationId
          AND p.parent IS NULL
          AND (p.kujiType IS NULL OR p.kujiType <> com.mirai.inventoryservice.catalog.domain.KujiType.CUSTOM)
        """)
    List<LocationInventory> findByStorageLocation_Id(@Param("storageLocationId") UUID storageLocationId);

    @Query("SELECT SUM(li.quantity) FROM LocationInventory li WHERE li.product.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);

    @Query("SELECT SUM(li.quantity) FROM LocationInventory li WHERE li.product.id = :productId AND li.site.id = :siteId")
    Integer sumQuantityByProductIdAndSiteId(@Param("productId") UUID productId, @Param("siteId") UUID siteId);

    void deleteByProduct_Id(UUID productId);

    // Batch delete all inventory records for multiple products (optimized for N+1 prevention)
    @Modifying
    @Query("DELETE FROM LocationInventory li WHERE li.product.id IN :productIds")
    void deleteAllByProductIdIn(@Param("productIds") Collection<UUID> productIds);

    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl JOIN FETCH li.product WHERE sl.code = :storageLocationCode AND li.site.id = :siteId")
    List<LocationInventory> findByStorageLocationCodeAndSiteId(@Param("storageLocationCode") String storageLocationCode, @Param("siteId") UUID siteId);

    // --- Site-qualified methods (.specs/phase-6-inventory 6c, T-6c-1, AC-3) ---
    // Query-level site predicates, not a post-load filter: a foreign-site id must miss at the
    // database, not load-then-reject in Java. The existing un-scoped methods above are left
    // untouched (F-6c-4) -- these are additive overloads for the new v1/scoped call paths.

    @Query("""
        SELECT li FROM LocationInventory li
        JOIN FETCH li.location l
        JOIN FETCH l.storageLocation sl
        JOIN FETCH li.product p
        WHERE li.id = :id AND li.site.id = :siteId
        """)
    Optional<LocationInventory> findByIdAndSite_Id(@Param("id") UUID id, @Param("siteId") UUID siteId);

    /**
     * Scalar-only site-membership check (.specs/phase-6-inventory 6d, review-driven fix: P1 finding,
     * concurrent batch transfers losing source debits). {@code boolean} projections never populate
     * the persistence context, unlike {@link #findByIdAndSite_Id} -- a caller that only needs to
     * confirm a row belongs to a site before locking it (e.g. {@code
     * StockMovementService#requireInventoryBelongsToSite}) MUST use this instead: loading the entity
     * here, then locking it later via {@code ensureAndLockInventoryRow}, then reading it again via
     * {@code findById}/{@code findAllByIdWithGraph}, would silently return the first, unlocked read's
     * stale scalar state -- Hibernate does not refresh an already-managed entity's fields from a
     * later query, locked or not.
     */
    boolean existsByIdAndSite_Id(UUID id, UUID siteId);

    @Query("""
        SELECT li FROM LocationInventory li
        JOIN FETCH li.location l
        JOIN FETCH l.storageLocation sl
        JOIN FETCH li.product
        WHERE li.location.id = :locationId AND li.product.id = :productId AND li.site.id = :siteId
        """)
    Optional<LocationInventory> findByLocation_IdAndProduct_IdAndSite_Id(
            @Param("locationId") UUID locationId, @Param("productId") UUID productId, @Param("siteId") UUID siteId);

    /**
     * Site-scoped counterpart to {@link #findAllByIdWithGraph}: same eager-loaded graph, but
     * excludes any id whose row belongs to a different site from the result -- callers (T-6c-6's
     * lock query, v1 controllers) must treat a missing id as not-found-at-this-site, not silently
     * proceed with a partial batch.
     */
    @Query("""
        SELECT li FROM LocationInventory li
        JOIN FETCH li.location l
        JOIN FETCH l.storageLocation sl
        JOIN FETCH li.product p
        LEFT JOIN FETCH p.parent
        WHERE li.id IN :ids AND li.site.id = :siteId
        """)
    List<LocationInventory> findAllByIdInAndSite_IdWithGraph(@Param("ids") Collection<UUID> ids, @Param("siteId") UUID siteId);

    @Query("""
        SELECT li FROM LocationInventory li
        JOIN FETCH li.location l
        JOIN FETCH l.storageLocation sl
        JOIN FETCH li.product p
        WHERE li.location.id = :locationId
          AND li.site.id = :siteId
          AND p.parent IS NULL
          AND (p.kujiType IS NULL OR p.kujiType <> com.mirai.inventoryservice.catalog.domain.KujiType.CUSTOM)
        """)
    List<LocationInventory> findByLocation_IdAndSite_Id(@Param("locationId") UUID locationId, @Param("siteId") UUID siteId);

    /**
     * Site-scoped counterpart to {@link #findByProduct_Id} (.specs/phase-6-inventory 6c, T-6c-11):
     * every location carrying this product at one site, for the v1
     * {@code GET .../inventory/products/{productId}} route.
     */
    @Query("SELECT li FROM LocationInventory li JOIN FETCH li.location l JOIN FETCH l.storageLocation sl WHERE li.product.id = :productId AND li.site.id = :siteId")
    List<LocationInventory> findByProduct_IdAndSite_Id(@Param("productId") UUID productId, @Param("siteId") UUID siteId);

    /**
     * Site-scoped counterpart to {@link #sumQuantitiesByProductIds}: returns rows of
     * [productId UUID, totalQuantity Long] for the given products at one site only. Missing
     * products (no inventory at this site) mean total = 0 -- callers must treat absence as zero,
     * not as "not found" (F-6c-3's zero-stock hazard).
     */
    @Query("""
        SELECT li.product.id, SUM(li.quantity)
        FROM LocationInventory li
        WHERE li.product.id IN :productIds AND li.site.id = :siteId
        GROUP BY li.product.id
        """)
    List<Object[]> sumQuantitiesByProductIdsAndSiteId(
            @Param("productIds") Collection<UUID> productIds, @Param("siteId") UUID siteId);

    // --- Row locking for read-modify-write paths (.specs/phase-6-inventory 6c, T-6c-6, F-6c-5;
    // review-driven fix round 2: T-6c-6 P1 findings, unified locking strategy; review-driven fix
    // round 3: adjustment/transfer lock-order conflict) ---
    // No @Version column exists on LocationInventory (F-6c-5); PESSIMISTIC_WRITE on an
    // ordered lock query is the chosen concurrency control instead of adding one. The methods below
    // exist solely to let callers acquire correct locks BEFORE the first read/load of a row in a
    // transaction -- Hibernate will not overwrite an already-managed entity's scalar state from a
    // later query, so calling any of these AFTER an unlocked read of the same id would silently
    // keep the stale, unlocked value. There is exactly ONE lock-ordering domain across every writer
    // that can hold more than one of these rows in a single transaction: (location, product), not
    // row id. An earlier version of this fix let batchAdjustInventory lock by ascending id (sufficient
    // on its own, since its rows always already exist) while the transfer paths locked by
    // (location, product) (required, since a not-yet-created destination has no id to sort by) --
    // two independently-consistent orderings that could still disagree with each other for two
    // products at one location, producing a real cross-writer deadlock (reproduced on Postgres).
    // batchAdjustInventory now shares the same (location, product)-keyed routine via
    // StockMovementService.lockInventoryRowsForUpdate / ensureAndLockInventoryRow / lockPlannedRows,
    // using findLocationAndProductIdById + findIdByLocation_IdAndProduct_IdForUpdate +
    // insertLocationInventoryIfAbsent together (the id-ordered lockAllByIdForUpdate this comment
    // used to describe has been removed as dead code).

    /**
     * A row's natural key, for {@link #findLocationAndProductIdById}. A small constructor-expression
     * DTO rather than a raw {@code Object[]} so callers get named, typed access.
     */
    record LocationProductIds(UUID locationId, UUID productId) {
    }

    /**
     * Scalar-only lookup of an existing row's (location, product) key, for planning a transfer or
     * batch-adjust before any entity is loaded or locked (.specs/phase-6-inventory 6c, review-driven
     * fix rounds 2-3). Both columns are immutable once a row exists, so reading them without a lock
     * is safe; unlike an entity-returning query, this does not populate the persistence context, so
     * it cannot cause the stale-state hazard {@link #findIdByLocation_IdAndProduct_IdForUpdate}
     * exists to avoid. Used for a transfer's source, an explicit destination id, and every
     * batch-adjust line id -- none is special-cased out of the unified (location, product)-keyed
     * locking scheme.
     */
    @Query("SELECT new com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository$LocationProductIds"
            + "(li.location.id, li.product.id) FROM LocationInventory li WHERE li.id = :id")
    Optional<LocationProductIds> findLocationAndProductIdById(@Param("id") UUID id);

    /**
     * Scalar, {@code PESSIMISTIC_WRITE}-locking id lookup for a (location, product) pair --
     * deliberately plain (no joins), so {@code FOR UPDATE} only ever locks the
     * {@code location_inventory} row itself, never {@code locations}/{@code products}/
     * {@code storage_locations} (avoids both the outer-join-hazard F-6c-5 already documented and
     * needless cross-table contention). This is the routine's single find-and-lock step: unlike the
     * scalar-but-unlocked {@link #findLocationAndProductIdById}, finding a row here IS locking it --
     * there is no separate read-then-lock gap for a concurrent delete (or delete-and-recreate) to
     * race through. See {@code StockMovementService.ensureAndLockInventoryRow}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT li.id FROM LocationInventory li WHERE li.location.id = :locationId AND li.product.id = :productId")
    Optional<UUID> findIdByLocation_IdAndProduct_IdForUpdate(
            @Param("locationId") UUID locationId, @Param("productId") UUID productId);

    /**
     * Guarantees a location_inventory row exists at (locationId, productId), at quantity 0,
     * without ever erroring on a concurrent creation race (.specs/phase-6-inventory 6c, review-fix
     * T-6c-6 P1: resolveTransferLockIds must return only ids that are already real before they are
     * locked). A plain JPA save-if-absent cannot do this safely: two overlapping transactions each
     * finding "absent" would both try to INSERT and one would abort the whole transaction on the
     * unique (location_id, product_id) index (idx_location_inventory_unique /
     * infra/init-db/20-unified-locations.sql) with no savepoint to recover from.
     * <p>
     * {@code INSERT ... ON CONFLICT (location_id, product_id) DO NOTHING} sidesteps that entirely:
     * Postgres's speculative-insertion protocol for {@code ON CONFLICT} serializes concurrent
     * inserts of the same key by having the later inserter wait for the earlier one's transaction
     * to finish (commit or abort) rather than erroring or deadlocking, and it also self-resolves
     * within one transaction (a second call in the same transaction sees its own uncommitted
     * insert and no-ops).
     * <p>
     * {@code id}, {@code siteId}, and both timestamps are supplied explicitly by the caller rather
     * than left to database defaults/triggers: production Supabase derives site_id via the
     * {@code trg_sync_inventory_site_id} trigger and defaults id/created_at/updated_at
     * (infra/init-db/20-unified-locations.sql), but this repository's own Testcontainers-backed
     * integration tests run against a schema Hibernate generates from the entity mapping
     * ({@code spring.jpa.hibernate.ddl-auto=create-drop}), which has none of those triggers or
     * column defaults. Supplying every value explicitly makes this method correct in both
     * environments instead of silently depending on production-only schema objects this module
     * cannot verify from Java.
     */
    @Modifying
    @Query(value = "INSERT INTO location_inventory (id, location_id, site_id, product_id, quantity, created_at, updated_at) "
            + "VALUES (:id, :locationId, :siteId, :productId, 0, now(), now()) "
            + "ON CONFLICT (location_id, product_id) DO NOTHING", nativeQuery = true)
    void insertLocationInventoryIfAbsent(
            @Param("id") UUID id,
            @Param("locationId") UUID locationId,
            @Param("siteId") UUID siteId,
            @Param("productId") UUID productId);
}
