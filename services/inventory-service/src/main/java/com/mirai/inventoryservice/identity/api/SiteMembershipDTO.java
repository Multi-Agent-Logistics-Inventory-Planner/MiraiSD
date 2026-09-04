package com.mirai.inventoryservice.identity.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One row of {@code GET /api/v1/me/sites}. Only active memberships are ever returned - a user
 * with none gets an empty list, not implicit MAIN access - so there is no separate "active" flag.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteMembershipDTO {
    private UUID siteId;
    private String siteCode;
    private String siteName;
}
