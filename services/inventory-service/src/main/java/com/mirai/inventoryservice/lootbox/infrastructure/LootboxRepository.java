package com.mirai.inventoryservice.lootbox.infrastructure;

import com.mirai.inventoryservice.lootbox.domain.Lootbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LootboxRepository extends JpaRepository<Lootbox, UUID> {

    List<Lootbox> findAllByOrderBySortOrderAscNameAsc();

    List<Lootbox> findBySiteIdOrderBySortOrderAscNameAsc(UUID siteId);

    /** Site-scoped lookup for admin CRUD: a foreign-site id must 404, not silently resolve. */
    Optional<Lootbox> findByIdAndSiteId(UUID id, UUID siteId);

    /** Crates currently open for play: active and within their (optional) date window. */
    @Query("""
            SELECT l FROM Lootbox l
            WHERE l.siteId = :siteId
              AND l.active = true
              AND (l.startsAt IS NULL OR l.startsAt <= :now)
              AND (l.endsAt   IS NULL OR l.endsAt   >  :now)
            ORDER BY l.sortOrder ASC, l.name ASC
            """)
    List<Lootbox> findOpen(@Param("siteId") UUID siteId, @Param("now") OffsetDateTime now);

    /** A single crate, only if it's currently open in this site. Returned for the play guard. */
    @Query("""
            SELECT l FROM Lootbox l
            WHERE l.id = :id
              AND l.siteId = :siteId
              AND l.active = true
              AND (l.startsAt IS NULL OR l.startsAt <= :now)
              AND (l.endsAt   IS NULL OR l.endsAt   >  :now)
            """)
    Optional<Lootbox> findByIdOpen(@Param("id") UUID id, @Param("siteId") UUID siteId, @Param("now") OffsetDateTime now);
}
