package com.mirai.inventoryservice.identity.api;

import com.mirai.inventoryservice.identity.application.MembershipAuthorizer;
import com.mirai.inventoryservice.identity.application.UserService;
import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.sites.application.SiteDirectory;
import com.mirai.inventoryservice.sites.application.SiteSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The site-agnostic identity endpoints from docs/specs/authentication-and-authorization.md
 * section 7: who the caller is, and which sites they can reach. {@code /api/v1/sites/{siteId}/**}
 * endpoints (site-scoped) live in each owning module instead - see
 * {@code sites.api.SitePermissionsController}.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserService userService;
    private final MembershipAuthorizer membershipAuthorizer;
    private final SiteDirectory siteDirectory;

    public MeController(UserService userService,
                         MembershipAuthorizer membershipAuthorizer,
                         SiteDirectory siteDirectory) {
        this.userService = userService;
        this.membershipAuthorizer = membershipAuthorizer;
        this.siteDirectory = siteDirectory;
    }

    @GetMapping
    public ResponseEntity<MeResponseDTO> getMe(Authentication authentication) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();

        Optional<User> resolved = userService.resolveBySupabaseIdOrEmail(
                principal.supabaseUserId(), principal.email());
        if (resolved.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = resolved.get();
        return ResponseEntity.ok(MeResponseDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .systemAdmin(Boolean.TRUE.equals(user.getIsSystemAdmin()))
                .build());
    }

    /**
     * A user with no active memberships gets an empty list here, never implicit MAIN access -
     * per docs/specs/authentication-and-authorization.md section 6. This reflects real membership
     * rows only: a system administrator's bypass (see AuthorizedSiteContextFactory) never creates
     * one, so it does not appear here even though it grants access via
     * {@code /api/v1/sites/{siteId}/permissions}.
     */
    @GetMapping("/sites")
    public ResponseEntity<List<SiteMembershipDTO>> getMySites(Authentication authentication) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        UUID backendUserId = principal.backendUserId();
        if (backendUserId == null) {
            return ResponseEntity.ok(List.of());
        }

        Set<UUID> activeSiteIds = Set.copyOf(membershipAuthorizer.activeSiteIdsFor(backendUserId));
        List<SiteMembershipDTO> memberships = siteDirectory.allSites().stream()
                .filter(site -> activeSiteIds.contains(site.id()))
                .map(MeController::toDTO)
                .toList();

        return ResponseEntity.ok(memberships);
    }

    private static SiteMembershipDTO toDTO(SiteSummary site) {
        return SiteMembershipDTO.builder()
                .siteId(site.id())
                .siteCode(site.code())
                .siteName(site.name())
                .build();
    }
}
