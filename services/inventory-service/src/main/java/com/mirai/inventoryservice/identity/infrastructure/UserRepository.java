package com.mirai.inventoryservice.identity.infrastructure;

import com.mirai.inventoryservice.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByFullName(String fullName);
    Optional<User> findBySupabaseUserId(UUID supabaseUserId);
    boolean existsByEmail(String email);
    boolean existsByFullName(String fullName);

    /**
     * Atomically backfills supabase_user_id only if the row is still unbound, per
     * docs/specs/authentication-and-authorization.md section 2. Two concurrent requests can
     * both read the same legacy row with supabase_user_id = NULL before either writes; a plain
     * save() would let the last writer silently overwrite the first, since there is no version
     * column or row lock. The WHERE ... IS NULL guard makes only one of the two updates take
     * effect - the loser gets 0 rows updated and must re-resolve rather than trust its stale
     * read.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.supabaseUserId = :supabaseUserId WHERE u.id = :id AND u.supabaseUserId IS NULL")
    int backfillSupabaseUserId(@Param("id") UUID id, @Param("supabaseUserId") UUID supabaseUserId);

    // Review tracking methods
    List<User> findByIsReviewTrackedTrueOrderByFullNameAsc();
    List<User> findAllByOrderByFullNameAsc();
}

