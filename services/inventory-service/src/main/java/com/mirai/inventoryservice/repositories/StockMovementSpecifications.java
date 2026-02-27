package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.models.audit.StockMovement;
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
