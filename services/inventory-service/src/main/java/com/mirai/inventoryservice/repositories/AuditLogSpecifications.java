package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class AuditLogSpecifications {

    private static boolean isKujiReason(StockMovementReason reason) {
        return reason == StockMovementReason.KUJI_PRIZE_WON
                || reason == StockMovementReason.KUJI_DRAW_REVERSED
                || reason == StockMovementReason.KUJI_SLIP_ADJUSTMENT;
    }


    public static Specification<AuditLog> withFilters(AuditLogFilterDTO filters) {
        return (root, query, cb) -> {
            Predicate predicate = cb.conjunction();

            // Filter by actor
            if (filters.getActorId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("actorId"), filters.getActorId()));
            }

            // Filter by reason — list takes precedence over singular for callers that need
            // multiple reasons in one query (e.g. the per-kuji-box session feed).
            if (filters.getReasons() != null && !filters.getReasons().isEmpty()) {
                predicate = cb.and(predicate, root.get("reason").in(filters.getReasons()));
            } else if (filters.getReason() != null) {
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
                    // Kuji draws on linked tiers record the prize product as sm.item, so
                    // matching only on sm.item.id misses them when the caller filters by
                    // the kuji box's parent product. The kujiBoxProductId column on the
                    // audit log lets those linked draws still match.
                    predicate = cb.and(predicate, cb.or(
                            cb.equal(sm.get("item").get("id"), filters.getProductId()),
                            cb.equal(root.get("kujiBoxProductId"), filters.getProductId())
                    ));
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

            // Hide kuji entries from the main audit log unless the caller explicitly asked
            // for a kuji reason (the kuji activity log card, undo-draw picker, and per-box
            // session feed pass kuji reasons through `reason` or `reasons`).
            StockMovementReason explicitReason = filters.getReason();
            List<StockMovementReason> requestedReasons = filters.getReasons();
            boolean explicitlyKuji =
                    isKujiReason(explicitReason)
                            || (requestedReasons != null
                                && requestedReasons.stream().anyMatch(AuditLogSpecifications::isKujiReason));

            if (!explicitlyKuji) {
                predicate = cb.and(predicate, root.get("reason").in(
                        StockMovementReason.KUJI_PRIZE_WON,
                        StockMovementReason.KUJI_DRAW_REVERSED,
                        StockMovementReason.KUJI_SLIP_ADJUSTMENT
                ).not());
            }

            return predicate;
        };
    }
}
