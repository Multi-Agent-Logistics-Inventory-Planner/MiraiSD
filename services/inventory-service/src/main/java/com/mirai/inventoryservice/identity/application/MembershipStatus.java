package com.mirai.inventoryservice.identity.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One user's membership state for one site - active or previously revoked, never absent. */
public record MembershipStatus(UUID siteId, boolean active, OffsetDateTime updatedAt) {
}
