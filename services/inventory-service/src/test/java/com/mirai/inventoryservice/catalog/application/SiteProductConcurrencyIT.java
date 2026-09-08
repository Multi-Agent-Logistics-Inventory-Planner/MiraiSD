package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import com.mirai.inventoryservice.catalog.domain.SiteProductVersionConflictException;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SiteProductRepository;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * T-6 (.specs/phase-5c-site-products AC-6): a genuine two-transaction concurrency test - not the
 * mocked exception-translation test in {@link SiteProductServiceTest} - proving the real
 * behavior against real PostgreSQL: one concurrent settings write succeeds, the other is
 * rejected with {@link SiteProductVersionConflictException}, and no update is silently lost.
 * <p>
 * Forces the race deterministically with a real row-level lock: a third, independently-managed
 * transaction takes {@code SELECT ... FOR UPDATE} on the target row and holds it open, so both
 * racing {@link SiteProductService#updateSettings} calls complete their own (non-blocking) read -
 * both observing the same starting version - and then block on their {@code UPDATE} inside
 * {@code saveAndFlush}. Releasing the lock lets Postgres itself decide which {@code UPDATE} wins;
 * the loser's {@code WHERE version = ?} matches zero rows once the winner has committed, which is
 * exactly the real optimistic-lock failure the P2 review fix's fresh-transaction version lookup
 * exists to handle safely.
 */
class SiteProductConcurrencyIT extends BaseKafkaIntegrationTest {

    @Autowired private SiteProductService siteProductService;
    @Autowired private SiteProductRepository siteProductRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Product newProduct(String label) {
        Category category = categoryRepository.save(Category.builder()
                .name("Concurrency IT Category " + label + " " + System.nanoTime())
                .slug("concurrency-it-category-" + label.toLowerCase() + "-" + System.nanoTime())
                .build());
        return productRepository.save(Product.builder()
                .sku("CONCURRENCY-IT-" + label + "-" + System.nanoTime())
                .name("Concurrency IT Product " + label)
                .category(category)
                .build());
    }

    private Site newSite(String label) {
        return siteRepository.save(Site.builder()
                .name("Concurrency IT Site " + label)
                .code("CX-" + Long.toString(System.nanoTime(), 36).toUpperCase())
                .build());
    }

    private static SiteProductSettingsUpdate settingsUpdate(long version, BigDecimal unitCost) {
        return new SiteProductSettingsUpdate(
                version, FieldUpdate.omitted(), FieldUpdate.of(unitCost), FieldUpdate.omitted(),
                FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted());
    }

    /** Polls until at least {@code n} backends are actively blocked on a lock, or times out. */
    private void awaitBackendsWaitingOnALock(int n, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE state = 'active' AND wait_event_type = 'Lock'",
                    Integer.class);
            if (waiting != null && waiting >= n) {
                return;
            }
            Thread.sleep(25);
        }
        throw new IllegalStateException("timed out waiting for " + n + " backends to block on a lock");
    }

    @Test
    void concurrentSettingsUpdatesOneSucceedsOneConflictsWithNoLostUpdate() throws Exception {
        Product product = newProduct("Race");
        Site site = newSite("Race");
        SiteProduct carried = siteProductService.setStocked(site.getId(), product.getId(), true);
        long startingVersion = carried.getVersion();

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        TransactionTemplate lockTemplate = new TransactionTemplate(transactionManager);

        ExecutorService lockExecutor = Executors.newSingleThreadExecutor();
        Future<?> lockHolder = lockExecutor.submit(() -> lockTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForList(
                    "SELECT id FROM site_products WHERE site_id = ? AND product_id = ? FOR UPDATE",
                    site.getId(), product.getId());
            lockAcquired.countDown();
            try {
                if (!releaseLock.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release signal never arrived");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }));

        assertThat(lockAcquired.await(10, TimeUnit.SECONDS))
                .as("the row lock must be acquired before the racing writes start")
                .isTrue();

        ExecutorService raceExecutor = Executors.newFixedThreadPool(2);
        try {
            Future<SiteProduct> futureA = raceExecutor.submit(() -> siteProductService.updateSettings(
                    site.getId(), product.getId(), settingsUpdate(startingVersion, new BigDecimal("11.00"))));
            Future<SiteProduct> futureB = raceExecutor.submit(() -> siteProductService.updateSettings(
                    site.getId(), product.getId(), settingsUpdate(startingVersion, new BigDecimal("22.00"))));

            // Both calls' own reads are non-blocking (plain SELECT under MVCC); once both are
            // blocked trying to UPDATE, releasing the lock lets them race for real.
            awaitBackendsWaitingOnALock(2, 10_000);
            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);

            SiteProduct successResult = null;
            Throwable failureCause = null;
            try {
                successResult = futureA.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failureCause = e.getCause();
            }
            try {
                SiteProduct b = futureB.get(10, TimeUnit.SECONDS);
                if (successResult != null) {
                    fail("both concurrent writes succeeded - a lost update slipped through");
                }
                successResult = b;
            } catch (ExecutionException e) {
                if (failureCause != null) {
                    fail("both concurrent writes failed - exactly one should have succeeded", e.getCause());
                }
                failureCause = e.getCause();
            }

            assertThat(successResult).as("exactly one of the two concurrent writes must succeed").isNotNull();
            assertThat(failureCause)
                    .as("the other must fail with a version conflict naming the row's new current version")
                    .isInstanceOf(SiteProductVersionConflictException.class)
                    .hasMessageContaining("current version is " + (startingVersion + 1));

            SiteProduct finalRow = siteProductRepository.findBySiteIdAndProductId(site.getId(), product.getId())
                    .orElseThrow();
            assertThat(finalRow.getUnitCost())
                    .as("the final row must reflect exactly the winning write, not a merge of both")
                    .isEqualByComparingTo(successResult.getUnitCost());
            assertThat(finalRow.getVersion())
                    .as("exactly one write applied - the row advances by one version, not two")
                    .isEqualTo(startingVersion + 1);
        } finally {
            raceExecutor.shutdownNow();
            lockExecutor.shutdownNow();
        }
    }
}
