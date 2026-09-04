package com.mirai.inventoryservice.identity.api;

import com.mirai.inventoryservice.identity.application.MembershipAuthorizer;
import com.mirai.inventoryservice.identity.application.MembershipStatus;
import com.mirai.inventoryservice.identity.application.UserService;
import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.sites.application.SiteDirectory;
import com.mirai.inventoryservice.sites.application.SiteSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin-driven membership lifecycle mutations (grant/revoke), per
 * docs/plans/enterprise-modernization.md Phase 4's last outstanding item. Deliberately not under
 * {@code /api/v1/sites/{siteId}/**}: {@code SiteAccessAuthorizationFilter} would require the
 * acting ADMIN to already be a member of the target site, which is wrong for granting a user's
 * first access to a site. ADMIN-only throughout, including the list endpoint - unlike
 * {@code UserController}'s broader read gate, every {@code /api/admin/**} path is restricted to
 * {@code ADMIN} at the URL-matcher level in {@code SecurityConfig}, ahead of any controller
 * method's own {@code @PreAuthorize}, so a broader read annotation here would be unreachable.
 */
@RestController
@RequestMapping("/api/admin/users/{userId}/site-memberships")
public class UserSiteMembershipController {

    private final UserService userService;
    private final MembershipAuthorizer membershipAuthorizer;
    private final SiteDirectory siteDirectory;

    public UserSiteMembershipController(UserService userService,
                                         MembershipAuthorizer membershipAuthorizer,
                                         SiteDirectory siteDirectory) {
        this.userService = userService;
        this.membershipAuthorizer = membershipAuthorizer;
        this.siteDirectory = siteDirectory;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserSiteMembershipStatusDTO>> getMemberships(@PathVariable UUID userId) {
        userService.getUserById(userId);

        List<UserSiteMembershipStatusDTO> memberships = membershipAuthorizer.membershipsFor(userId).stream()
                .flatMap(status -> siteDirectory.findById(status.siteId()).stream()
                        .map(site -> toDTO(status, site)))
                .toList();

        return ResponseEntity.ok(memberships);
    }

    @PutMapping("/{siteId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSiteMembershipStatusDTO> grantMembership(
            @PathVariable UUID userId,
            @PathVariable UUID siteId,
            Authentication authentication) {
        userService.getUserById(userId);
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();

        membershipAuthorizer.grantMembership(principal.backendUserId(), userId, siteId);

        UserSiteMembershipStatusDTO dto = membershipAuthorizer.membershipsFor(userId).stream()
                .filter(status -> status.siteId().equals(siteId))
                .findFirst()
                .flatMap(status -> siteDirectory.findById(siteId).map(site -> toDTO(status, site)))
                .orElseThrow();

        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{siteId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> revokeMembership(
            @PathVariable UUID userId,
            @PathVariable UUID siteId,
            Authentication authentication) {
        userService.getUserById(userId);
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();

        membershipAuthorizer.revokeMembership(principal.backendUserId(), userId, siteId);

        return ResponseEntity.noContent().build();
    }

    private static UserSiteMembershipStatusDTO toDTO(MembershipStatus status, SiteSummary site) {
        return UserSiteMembershipStatusDTO.builder()
                .siteId(site.id())
                .siteCode(site.code())
                .siteName(site.name())
                .active(status.active())
                .updatedAt(status.updatedAt())
                .build();
    }
}
