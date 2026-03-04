package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.StockMovement;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class AuditLogSpecifications {

    public static Specification<AuditLog> withFilters(AuditLogFilterDTO filters) {
        return (root, query, cb) -> {
            Predicate predicate = cb.conjunction();

            // Filter by actor
            if (filters.getActorId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("actorId"), filters.getActorId()));
            }

            // Filter by reason
            if (filters.getReason() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("reason"), filters.getReason()));
            }

            // Filter by date range
            if (filters.getFromDate() != null) {
                OffsetDateTime fromDateTime = filters.getFromDate().atStartOfDay().atOffset(ZoneOffset.UTC);
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"), fromDateTime));
            }

            if (filters.getToDate() != null) {
                OffsetDateTime toDateTime = filters.getToDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                predicate = cb.and(predicate, cb.lessThan(root.get("createdAt"), toDateTime));
            }

            // Filters that require a JOIN to stock_movements are handled with a single
            // shared join to avoid cartesian products when multiple such filters are active.
            boolean needsMovementsJoin = filters.getProductId() != null
                    || filters.getLocationId() != null
                    || (filters.getSearch() != null && !filters.getSearch().isBlank());

            if (needsMovementsJoin) {
                query.distinct(true);
                Join<AuditLog, StockMovement> sm = root.join("movements", JoinType.LEFT);

                if (filters.getProductId() != null) {
                    predicate = cb.and(predicate,
                            cb.equal(sm.get("item").get("id"), filters.getProductId()));
                }

                if (filters.getLocationId() != null) {
                    predicate = cb.and(predicate, cb.or(
                            cb.equal(sm.get("fromLocationId"), filters.getLocationId()),
                            cb.equal(sm.get("toLocationId"), filters.getLocationId())
                    ));
                }

                if (filters.getSearch() != null && !filters.getSearch().isBlank()) {
                    String pattern = "%" + filters.getSearch().toLowerCase() + "%";
                    predicate = cb.and(predicate, cb.or(
                            cb.like(cb.lower(root.get("actorName")), pattern),
                            cb.like(cb.lower(root.get("productSummary")), pattern),
                            cb.like(cb.lower(sm.get("item").get("name")), pattern),
                            cb.like(cb.lower(sm.get("item").get("sku")), pattern)
                    ));
                }
            }

            return predicate;
        };
    }
}
