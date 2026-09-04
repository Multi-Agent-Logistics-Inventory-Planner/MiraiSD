package com.mirai.inventoryservice.identity.application;

import com.mirai.inventoryservice.identity.domain.MembershipNotFoundException;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.sites.application.SiteDirectory;
import com.mirai.inventoryservice.sites.domain.SiteNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Facade over {@link UserSiteMembershipRepository}, per
 * docs/specs/spring-domain-modular-monolith.md section 7 - the only way other code checks,
 * grants or revokes site access. Never exposes {@link UserSiteMembership} itself outside
 * {@code identity}. {@code sites.domain.SiteNotFoundException} is thrown directly rather than an
 * identity-owned equivalent - {@code identity -> sites} is an allowed dependency direction, and
 * {@code GlobalExceptionHandler} already maps it to 404.
 */
@Service
@Transactional
@Slf4j
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

    /**
     * Grants (or reactivates) a user's membership on any site, for the admin-driven
     * grant/revoke lifecycle - unlike {@link #grantMainSiteMembershipIfAbsent}, this is not
     * restricted to MAIN and handles reactivating a previously revoked row.
     * {@link UserSiteMembershipRepository#insertActiveIfAbsent} alone cannot reactivate (it is a
     * no-op on conflict, not a true upsert), so this pairs it with
     * {@link UserSiteMembershipRepository#activate} unconditionally - each statement is atomic on
     * its own, and a concurrent pair of grants just serializes on the row.
     *
     * @throws SiteNotFoundException if {@code siteId} does not exist
     */
    public void grantMembership(UUID actorUserId, UUID userId, UUID siteId) {
        if (siteDirectory.findById(siteId).isEmpty()) {
            throw new SiteNotFoundException("Site not found: " + siteId);
        }
        userSiteMembershipRepository.insertActiveIfAbsent(userId, siteId);
        userSiteMembershipRepository.activate(userId, siteId, OffsetDateTime.now());
        log.info("Membership granted: actor={} user={} site={}", actorUserId, userId, siteId);
    }

    /**
     * Revokes (deactivates) a user's membership on a site.
     *
     * @throws MembershipNotFoundException if no membership row exists for this pair
     */
    public void revokeMembership(UUID actorUserId, UUID userId, UUID siteId) {
        int rowsAffected = userSiteMembershipRepository.deactivate(userId, siteId, OffsetDateTime.now());
        if (rowsAffected == 0) {
            throw new MembershipNotFoundException(
                    "No membership found for user " + userId + " on site " + siteId);
        }
        log.info("Membership revoked: actor={} user={} site={}", actorUserId, userId, siteId);
    }

    /** All of a user's memberships, active and inactive, for admin visibility. */
    @Transactional(readOnly = true)
    public List<MembershipStatus> membershipsFor(UUID userId) {
        return userSiteMembershipRepository.findByUserId(userId).stream()
                .map(m -> new MembershipStatus(m.getSiteId(), Boolean.TRUE.equals(m.getIsActive()), m.getUpdatedAt()))
                .toList();
    }
}
