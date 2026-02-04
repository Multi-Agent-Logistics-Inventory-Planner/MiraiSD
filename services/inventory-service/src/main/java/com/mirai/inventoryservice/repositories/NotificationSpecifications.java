package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.dtos.requests.NotificationFilterDTO;
import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.models.enums.NotificationType;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    public static Specification<Notification> withFilters(NotificationFilterDTO filters) {
        return Specification
                .where(matchesSearch(filters.getSearch()))
                .and(hasType(filters.getType()))
                .and(isResolved(filters.getResolved()))
                .and(isAfterDate(filters.getFromDate()))
                .and(isBeforeDate(filters.getToDate()));
    }

    private static Specification<Notification> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.like(cb.lower(root.get("message")), pattern);
        };
    }

    private static Specification<Notification> hasType(NotificationType type) {
        return (root, query, cb) -> {
            if (type == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("type"), type);
        };
    }

    private static Specification<Notification> isResolved(Boolean resolved) {
        return (root, query, cb) -> {
            if (resolved == null) {
                return cb.conjunction();
            }
            if (resolved) {
                return cb.isNotNull(root.get("resolvedAt"));
            } else {
                return cb.isNull(root.get("resolvedAt"));
            }
        };
    }

    private static Specification<Notification> isAfterDate(OffsetDateTime fromDate) {
        return (root, query, cb) -> {
            if (fromDate == null) {
                return cb.conjunction();
            }
            return cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate);
        };
    }

    private static Specification<Notification> isBeforeDate(OffsetDateTime toDate) {
        return (root, query, cb) -> {
            if (toDate == null) {
                return cb.conjunction();
            }
            return cb.lessThanOrEqualTo(root.get("createdAt"), toDate);
        };
    }
}
