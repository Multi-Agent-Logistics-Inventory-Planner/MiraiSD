package com.mirai.inventoryservice.identity.infrastructure;

import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Constraint/version behavior that needs real Postgres semantics H2 can't be trusted for
 * (unique index enforcement, FK cascade, optimistic locking) - see
 * V52__create_user_site_memberships.sql.
 */
class UserSiteMembershipRepositoryIT extends BaseKafkaIntegrationTest {

    @Autowired
    private UserSiteMembershipRepository userSiteMembershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Test
    void rejectsADuplicateUserSitePair() {
        User user = userRepository.save(User.builder()
                .email("dup-membership@test.internal").fullName("Dup").role(UserRole.EMPLOYEE).build());
        Site site = siteRepository.save(Site.builder().name("Dup Site").code("DUP-SITE").build());

        userSiteMembershipRepository.saveAndFlush(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());

        assertThatThrownBy(() -> userSiteMembershipRepository.saveAndFlush(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cascadeDeletesMembershipsWhenTheUserIsDeleted() {
        User user = userRepository.save(User.builder()
                .email("cascade-membership@test.internal").fullName("Cascade").role(UserRole.EMPLOYEE).build());
        Site site = siteRepository.save(Site.builder().name("Cascade Site").code("CASCADE-SITE").build());
        UserSiteMembership membership = userSiteMembershipRepository.saveAndFlush(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());

        userRepository.delete(user);
        userRepository.flush();

        assertThat(userSiteMembershipRepository.findById(membership.getId())).isEmpty();
    }

    @Test
    void insertActiveIfAbsentIsSafeUnderConcurrentCallersForTheSamePair() throws InterruptedException {
        User user = userRepository.save(User.builder()
                .email("concurrent-grant@test.internal").fullName("Concurrent").role(UserRole.EMPLOYEE).build());
        Site site = siteRepository.save(Site.builder().name("Concurrent Site").code("CONCURRENT-SITE").build());

        int callers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<?>> futures = IntStream.range(0, callers)
                    .<Future<?>>mapToObj(i -> executor.submit(() -> {
                        ready.countDown();
                        try {
                            go.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        // Must not throw even though every caller races to insert the same
                        // (user, site) pair - this is exactly the /sync-user retry scenario
                        // MembershipAuthorizer#grantMainSiteMembershipIfAbsent guards against.
                        userSiteMembershipRepository.insertActiveIfAbsent(user.getId(), site.getId());
                    }))
                    .toList();

            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            throw new AssertionError("A concurrent insertActiveIfAbsent call threw", e);
        } finally {
            executor.shutdownNow();
        }

        List<UserSiteMembership> rows = userSiteMembershipRepository.findByUserIdAndIsActiveTrue(user.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSiteId()).isEqualTo(site.getId());
    }

    @Test
    void optimisticLockRejectsAConcurrentUpdate() {
        User user = userRepository.save(User.builder()
                .email("optimistic-lock@test.internal").fullName("Lock").role(UserRole.EMPLOYEE).build());
        Site site = siteRepository.save(Site.builder().name("Lock Site").code("LOCK-SITE").build());
        UserSiteMembership saved = userSiteMembershipRepository.saveAndFlush(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());

        UserSiteMembership firstRead = userSiteMembershipRepository.findById(saved.getId()).orElseThrow();
        UserSiteMembership secondRead = userSiteMembershipRepository.findById(saved.getId()).orElseThrow();

        firstRead.setIsActive(false);
        userSiteMembershipRepository.saveAndFlush(firstRead);

        secondRead.setIsActive(false);
        assertThatThrownBy(() -> userSiteMembershipRepository.saveAndFlush(secondRead))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
