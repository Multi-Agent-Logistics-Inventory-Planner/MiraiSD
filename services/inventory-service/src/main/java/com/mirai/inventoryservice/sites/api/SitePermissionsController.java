package com.mirai.inventoryservice.sites.api;

import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Site-scoped per docs/specs/multi-site-data-and-api.md section 7, so it lives here rather than
 * in {@code identity.api} alongside {@code MeController}, matching where
 * {@code LocationController}/{@code StorageLocationController} already sit. Only reads the
 * context that {@code identity.infrastructure.SiteAccessAuthorizationFilter} already resolved for
 * this path and stashed in {@link AuthorizedSiteContextHolder} - this does not create a
 * {@code sites -> identity} dependency.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}")
public class SitePermissionsController {

    @GetMapping("/permissions")
    public ResponseEntity<SitePermissionsDTO> getPermissions(@PathVariable UUID siteId) {
        AuthorizedSiteContext context = AuthorizedSiteContextHolder.require();

        return ResponseEntity.ok(SitePermissionsDTO.builder()
                .role(context.role())
                .permissions(context.effectivePermissions())
                .systemAdmin(context.systemAdmin())
                .build());
    }
}
