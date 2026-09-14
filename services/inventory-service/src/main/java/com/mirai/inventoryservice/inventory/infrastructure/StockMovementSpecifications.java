package com.mirai.inventoryservice.inventory.infrastructure;

import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import org.springframework.data.jpa.domain.Specification;

public final class StockMovementSpecifications {

    private StockMovementSpecifications() {
    }

    public static Specification<StockMovement> withFilters(AuditLogFilterDTO filters) {
        return Specification
                .where(matchesSearch(filters.getSearch()))
                .and(hasActorId(filters.getActorId()))
                .and(hasReason(filters.getReason()))
                .and(isAfterDate(filters.getFromDate()))
                .and(isBeforeDate(filters.getToDate()));
    }

    /**
     * Site-qualified counterpart to {@link #withFilters} (.specs/phase-6-inventory 6c, T-6c-1,
     * AC-3): composes the same filters with a mandatory site predicate for the v1 scoped
     * audit-log path. Per the user's resolved Q-6c-5 decision, a movement whose {@code site} is
     * still {@code null} (pre-backfill compatibility window, .specs/phase-6-inventory 6b
     * worksheet) is deliberately <b>included</b> here rather than excluded -- callers
     * (T-6c-11's controller) are responsible for labeling such rows as unknown-site in the
     * response, not for filtering them out, so the audit trail stays complete during the
     * compatibility window instead of silently incomplete.
     */
    public static Specification<StockMovement> withSiteFilter(AuditLogFilterDTO filters, java.util.UUID siteId) {
        return withFilters(filters).and(matchesSiteOrUnknown(siteId));
    }

    private static Specification<StockMovement> matchesSiteOrUnknown(java.util.UUID siteId) {
        return (root, query, cb) -> {
            // Explicit LEFT JOIN, not implicit path navigation via root.get("site"): a movement
            // with a null site must still match (via the isNull branch below), which an implicit
            // inner join on the association path would silently exclude.
            jakarta.persistence.criteria.Join<StockMovement, ?> siteJoin =
                    root.join("site", jakarta.persistence.criteria.JoinType.LEFT);
            return cb.or(
                    cb.equal(siteJoin.get("id"), siteId),
                    cb.isNull(siteJoin.get("id")));
        };
    }

    private static Specification<StockMovement> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("item").get("name")), pattern),
                    cb.like(cb.lower(root.get("item").get("sku")), pattern)
            );
        };
    }

    private static Specification<StockMovement> hasActorId(java.util.UUID actorId) {
        return (root, query, cb) -> {
            if (actorId == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("actorId"), actorId);
        };
    }

    private static Specification<StockMovement> hasReason(
            com.mirai.inventoryservice.models.enums.StockMovementReason reason) {
        return (root, query, cb) -> {
            if (reason == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("reason"), reason);
        };
    }

    private static Specification<StockMovement> isAfterDate(java.time.LocalDate fromDate) {
        return (root, query, cb) -> {
            if (fromDate == null) {
                return cb.conjunction();
            }
            // Convert LocalDate to start of day in UTC
            java.time.OffsetDateTime fromDateTime = fromDate.atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime();
            return cb.greaterThanOrEqualTo(root.get("at"), fromDateTime);
        };
    }

    private static Specification<StockMovement> isBeforeDate(java.time.LocalDate toDate) {
        return (root, query, cb) -> {
            if (toDate == null) {
                return cb.conjunction();
            }
            // Convert LocalDate to end of day in UTC (start of next day)
            java.time.OffsetDateTime toDateTime = toDate.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime();
            return cb.lessThan(root.get("at"), toDateTime);
        };
    }
}
