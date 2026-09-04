package com.mirai.inventoryservice.identity.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of {@code GET /api/admin/users/{userId}/site-memberships} - unlike
 * {@link SiteMembershipDTO} (only ever active), this carries {@code active} since admin
 * visibility includes revoked memberships too.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSiteMembershipStatusDTO {
    private UUID siteId;
    private String siteCode;
    private String siteName;
    private boolean active;
    private OffsetDateTime updatedAt;
}
