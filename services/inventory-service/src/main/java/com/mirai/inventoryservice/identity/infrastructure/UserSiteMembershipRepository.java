package com.mirai.inventoryservice.identity.infrastructure;

import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSiteMembershipRepository extends JpaRepository<UserSiteMembership, UUID> {
    Optional<UserSiteMembership> findByUserIdAndSiteId(UUID userId, UUID siteId);
    List<UserSiteMembership> findByUserIdAndIsActiveTrue(UUID userId);
    boolean existsByUserIdAndSiteIdAndIsActiveTrue(UUID userId, UUID siteId);

    /**
     * Atomic upsert-as-no-op: two concurrent grant attempts for the same (user, site) pair (e.g.
     * duplicate /sync-user retries) can both read "no row exists" before either writes, per
     * MembershipAuthorizer#grantMainSiteMembershipIfAbsent. A plain find-then-save would let the
     * loser's insert violate the unique constraint at commit and surface as a server error; the
     * unique index itself (not application code) is what makes this correct under concurrency,
     * mirroring the reasoning behind UserRepository#backfillSupabaseUserId.
     * <p>
     * {@code @Transactional} here (not just on the caller): unlike the inherited CRUD methods
     * Spring Data generates, a custom {@code @Modifying @Query} method on the repository
     * interface is not implicitly transactional, so a caller with no transaction of its own
     * (e.g. a repository-level test invoking this directly) would otherwise fail with
     * {@code TransactionRequiredException} rather than exercising the intended race.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO user_site_memberships (user_id, site_id, is_active) "
            + "VALUES (:userId, :siteId, TRUE) ON CONFLICT (user_id, site_id) DO NOTHING",
            nativeQuery = true)
    void insertActiveIfAbsent(@Param("userId") UUID userId, @Param("siteId") UUID siteId);
}
