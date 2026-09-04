package com.mirai.inventoryservice.sites.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/** Response shape for {@code GET /api/v1/sites/{siteId}/permissions}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SitePermissionsDTO {
    private String role;
    private Set<String> permissions;
    private boolean systemAdmin;
}
