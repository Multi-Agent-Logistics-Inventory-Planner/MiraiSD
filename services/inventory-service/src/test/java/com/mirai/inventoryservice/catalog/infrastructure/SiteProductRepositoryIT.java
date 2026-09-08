package com.mirai.inventoryservice.catalog.infrastructure;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Constraint/version behavior real Postgres semantics must prove for
 * V56__create_site_products.sql (.specs/phase-5c-site-products AC-1): unique (site_id,
 * product_id), FK cascade/restrict, and optimistic locking. The shared test schema is Hibernate
 * {@code ddl-auto=create-drop}, not the migration SQL (see UserSiteMembershipRepositoryIT), and
 * {@link SiteProduct} deliberately has no JPA relation for {@code siteId}/{@code productId}
 * (spring-domain-modular-monolith.md rule 8), so Hibernate never emits V56's foreign keys. Add
 * them by hand, once, so this class's cascade/restrict assertions exercise the same constraints
 * production has.
 */
class SiteProductRepositoryIT extends BaseKafkaIntegrationTest {

    @Autowired
    private SiteProductRepository siteProductRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static boolean foreignKeysAdded = false;

    @BeforeEach
    void addForeignKeyConstraintsOnce() {
        if (foreignKeysAdded) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE site_products "
                + "ADD CONSTRAINT fk_sp_site_it FOREIGN KEY (site_id) REFERENCES sites(id) ON DELETE RESTRICT");
        jdbcTemplate.execute("ALTER TABLE site_products "
                + "ADD CONSTRAINT fk_sp_product_it FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE");
        foreignKeysAdded = true;
    }

    private Product newProduct(String label) {
        Category category = categoryRepository.save(Category.builder()
                .name("SiteProduct IT Category " + label + " " + System.nanoTime())
                .slug("site-product-it-category-" + label.toLowerCase() + "-" + System.nanoTime())
                .build());
        return productRepository.save(Product.builder()
                .sku("SITE-PRODUCT-IT-" + label + "-" + System.nanoTime())
                .name("SiteProduct IT Product " + label)
                .category(category)
                .build());
    }

    private Site newSite(String label) {
        return siteRepository.save(Site.builder()
                .name("SiteProduct IT Site " + label)
                .code("SP-" + Long.toString(System.nanoTime(), 36).toUpperCase())
                .build());
    }

    @Test
    void rejectsADuplicateSiteProductPair() {
        Product product = newProduct("Dup");
        Site site = newSite("Dup");

        siteProductRepository.saveAndFlush(SiteProduct.builder()
                .siteId(site.getId()).productId(product.getId()).build());

        assertThatThrownBy(() -> siteProductRepository.saveAndFlush(SiteProduct.builder()
                .siteId(site.getId()).productId(product.getId()).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cascadeDeletesSiteProductsWhenTheProductIsDeleted() {
        Product product = newProduct("Cascade");
        Site site = newSite("Cascade");
        SiteProduct siteProduct = siteProductRepository.saveAndFlush(SiteProduct.builder()
                .siteId(site.getId()).productId(product.getId()).build());

        productRepository.delete(product);
        productRepository.flush();

        assertThat(siteProductRepository.findById(siteProduct.getId())).isEmpty();
    }

    @Test
    void restrictsDeletingASiteThatStillHasSiteProducts() {
        Product product = newProduct("Restrict");
        Site site = newSite("Restrict");
        siteProductRepository.saveAndFlush(SiteProduct.builder()
                .siteId(site.getId()).productId(product.getId()).build());

        assertThatThrownBy(() -> {
            siteRepository.delete(site);
            siteRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void optimisticLockRejectsAConcurrentUpdate() {
        Product product = newProduct("Lock");
        Site site = newSite("Lock");
        SiteProduct saved = siteProductRepository.saveAndFlush(SiteProduct.builder()
                .siteId(site.getId()).productId(product.getId()).build());

        SiteProduct firstRead = siteProductRepository.findById(saved.getId()).orElseThrow();
        SiteProduct secondRead = siteProductRepository.findById(saved.getId()).orElseThrow();

        firstRead.setIsStocked(true);
        siteProductRepository.saveAndFlush(firstRead);

        secondRead.setIsStocked(true);
        assertThatThrownBy(() -> siteProductRepository.saveAndFlush(secondRead))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void findBySiteIdAndProductId_returnsEmptyForAForeignSitePair() {
        Product product = newProduct("Foreign");
        Site site = newSite("Foreign");
        Site otherSite = newSite("Foreign-Other");
        siteProductRepository.saveAndFlush(SiteProduct.builder()
                .siteId(site.getId()).productId(product.getId()).build());

        assertThat(siteProductRepository.findBySiteIdAndProductId(otherSite.getId(), product.getId()))
                .isEmpty();
        assertThat(siteProductRepository.findBySiteIdAndProductId(site.getId(), product.getId()))
                .isPresent();
    }
}
