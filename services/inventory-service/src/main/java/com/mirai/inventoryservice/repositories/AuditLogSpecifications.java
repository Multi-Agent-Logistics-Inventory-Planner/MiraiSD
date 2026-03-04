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

            // Filter by product (requires join to stock_movements)
            if (filters.getProductId() != null) {
                // Ensure distinct results when joining
                query.distinct(true);
                Join<AuditLog, StockMovement> movementsJoin = root.join("movements", JoinType.INNER);
                predicate = cb.and(predicate, cb.equal(movementsJoin.get("item").get("id"), filters.getProductId()));
            }

            // Filter by location (requires join to stock_movements)
            if (filters.getLocationId() != null) {
                query.distinct(true);
                Join<AuditLog, StockMovement> movementsJoin = root.join("movements", JoinType.INNER);
                Predicate fromLocation = cb.equal(movementsJoin.get("fromLocationId"), filters.getLocationId());
                Predicate toLocation = cb.equal(movementsJoin.get("toLocationId"), filters.getLocationId());
                predicate = cb.and(predicate, cb.or(fromLocation, toLocation));
            }

            // Search by actor name or product name
            if (filters.getSearch() != null && !filters.getSearch().isBlank()) {
                String searchPattern = "%" + filters.getSearch().toLowerCase() + "%";

                // Search in actor name
                Predicate actorNameMatch = cb.like(cb.lower(root.get("actorName")), searchPattern);

                // Search in product name (requires join)
                query.distinct(true);
                Join<AuditLog, StockMovement> movementsJoin = root.join("movements", JoinType.LEFT);
                Predicate productNameMatch = cb.like(cb.lower(movementsJoin.get("item").get("name")), searchPattern);
                Predicate productSkuMatch = cb.like(cb.lower(movementsJoin.get("item").get("sku")), searchPattern);

                predicate = cb.and(predicate, cb.or(actorNameMatch, productNameMatch, productSkuMatch));
            }

            return predicate;
        };
    }
}
