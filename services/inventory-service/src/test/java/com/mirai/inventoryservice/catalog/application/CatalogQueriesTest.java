package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogQueriesTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;

    private CatalogQueries catalogQueries;

    @BeforeEach
    void setUp() {
        catalogQueries = new CatalogQueries(productRepository, categoryRepository);
    }

    private Product product(UUID id, String name, Category category) {
        return Product.builder()
                .id(id)
                .sku("SKU-" + id)
                .name(name)
                .isActive(true)
                .quantity(5)
                .category(category)
                .build();
    }

    private Category category(UUID id, String name) {
        return Category.builder().id(id).name(name).slug("slug-" + id).isActive(true).build();
    }

    @Test
    void findById_returnsProductRefNeverTheEntity() {
        UUID id = UUID.randomUUID();
        Category cat = category(UUID.randomUUID(), "Figures");
        when(productRepository.findById(id)).thenReturn(Optional.of(product(id, "Widget", cat)));

        Optional<ProductRef> result = catalogQueries.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(id);
        assertThat(result.get().name()).isEqualTo("Widget");
        assertThat(result.get().categoryId()).isEqualTo(cat.getId());
    }

    @Test
    void findById_missing_returnsEmpty() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(catalogQueries.findById(id)).isEmpty();
    }

    @Test
    void getById_missing_throwsProductNotFoundException() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogQueries.getById(id))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getById_found_returnsRef() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.of(product(id, "Widget", null)));

        assertThat(catalogQueries.getById(id).name()).isEqualTo("Widget");
    }

    @Test
    void productRef_neverExposesCostOrMsrp() {
        UUID id = UUID.randomUUID();
        Product p = product(id, "Widget", null);
        p.setUnitCost(java.math.BigDecimal.TEN);
        p.setMsrp(java.math.BigDecimal.valueOf(20));
        when(productRepository.findById(id)).thenReturn(Optional.of(p));

        ProductRef ref = catalogQueries.getById(id);

        // ProductRef has no unitCost/msrp accessor at all - compile-time proof it's absent.
        // This assertion documents the record's field count as the closest runtime check.
        assertThat(ref.getClass().getRecordComponents()).extracting("name")
                .doesNotContain("unitCost", "msrp");
    }

    @Test
    void findAllByIds_batchMapsEveryResult() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(productRepository.findAllById(List.of(id1, id2)))
                .thenReturn(List.of(product(id1, "A", null), product(id2, "B", null)));

        List<ProductRef> refs = catalogQueries.findAllByIds(List.of(id1, id2));

        assertThat(refs).extracting(ProductRef::name).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void allProductRefs_delegatesToTheSlimProjectionQuery_notFindAllPlusMapping() {
        UUID id = UUID.randomUUID();
        ProductRef ref = new ProductRef(id, "SKU-A", "A", null, true, 0, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        when(productRepository.findAllProductRefs()).thenReturn(List.of(ref));

        List<ProductRef> refs = catalogQueries.allProductRefs();

        assertThat(refs).hasSize(1);
    }

    @Test
    void findByCategoryIdActive_delegatesToRepository() {
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(productRepository.findByCategoryIdAndIsActiveTrue(categoryId))
                .thenReturn(List.of(product(productId, "A", null)));

        assertThat(catalogQueries.findByCategoryIdActive(categoryId))
                .extracting(ProductRef::id)
                .containsExactly(productId);
    }

    @Test
    void findCategoryById_singleLookup_doesNotScanEveryCategory() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category(categoryId, "Figures")));

        Optional<CategoryRef> result = catalogQueries.findCategoryById(categoryId);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Figures");
        verify(categoryRepository, never()).findAll();
    }

    @Test
    void findCategoryById_missing_returnsEmpty() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThat(catalogQueries.findCategoryById(categoryId)).isEmpty();
    }

    @Test
    void allCategoryRefs_mapsEveryCategory() {
        Category c1 = category(UUID.randomUUID(), "Figures");
        Category c2 = category(UUID.randomUUID(), "Cards");
        when(categoryRepository.findAll()).thenReturn(List.of(c1, c2));

        assertThat(catalogQueries.allCategoryRefs())
                .extracting(CategoryRef::name)
                .containsExactlyInAnyOrder("Figures", "Cards");
    }
}
