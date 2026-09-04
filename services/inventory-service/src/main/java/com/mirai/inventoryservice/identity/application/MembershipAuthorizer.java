package com.mirai.inventoryservice.identity.application;

import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.sites.application.SiteDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Facade over {@link UserSiteMembershipRepository}, per
 * docs/specs/spring-domain-modular-monolith.md section 7 - the only way other code checks or
 * grants site access. Never exposes {@link UserSiteMembership} itself outside {@code identity}.
 */
@Service
@Transactional
public class MembershipAuthorizer {

    private static final String MAIN_SITE_CODE = "MAIN";

    private final UserSiteMembershipRepository userSiteMembershipRepository;
    private final SiteDirectory siteDirectory;

    public MembershipAuthorizer(UserSiteMembershipRepository userSiteMembershipRepository,
                                 SiteDirectory siteDirectory) {
        this.userSiteMembershipRepository = userSiteMembershipRepository;
        this.siteDirectory = siteDirectory;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveMembership(UUID userId, UUID siteId) {
        if (userId == null || siteId == null) {
            return false;
        }
        return userSiteMembershipRepository.existsByUserIdAndSiteIdAndIsActiveTrue(userId, siteId);
    }

    @Transactional(readOnly = true)
    public List<UUID> activeSiteIdsFor(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return userSiteMembershipRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .map(UserSiteMembership::getSiteId)
                .toList();
    }

    /**
     * Idempotent, including under concurrent callers: called from invitation acceptance so every
     * new user gets MAIN access without a future migration, per
     * docs/plans/enterprise-modernization.md section 7. Two simultaneous calls for the same user
     * (e.g. a retried /sync-user request) can both see no existing row - a plain find-then-save
     * would let the second writer's insert violate the unique constraint and surface as a server
     * error, so this delegates to an atomic {@code INSERT ... ON CONFLICT DO NOTHING}
     * ({@link UserSiteMembershipRepository#insertActiveIfAbsent}) rather than checking first.
     * Never reactivates an existing, explicitly deactivated row - the conflict clause is a no-op,
     * not an upsert.
     */
    public void grantMainSiteMembershipIfAbsent(UUID userId) {
        if (userId == null) {
            return;
        }
        siteDirectory.allSites().stream()
                .filter(site -> MAIN_SITE_CODE.equals(site.code()))
                .findFirst()
                .ifPresent(mainSite ->
                        userSiteMembershipRepository.insertActiveIfAbsent(userId, mainSite.id()));
    }
}
