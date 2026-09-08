package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogEntityAccessTest {

    @Mock private ProductRepository productRepository;

    private CatalogEntityAccess entityAccess;

    @BeforeEach
    void setUp() {
        entityAccess = new CatalogEntityAccess(productRepository);
    }

    @Test
    void getReference_delegatesToRepositoryProxy_noExistenceCheck() {
        UUID id = UUID.randomUUID();
        Product proxy = Product.builder().id(id).build();
        when(productRepository.getReferenceById(id)).thenReturn(proxy);

        assertThat(entityAccess.getReference(id)).isSameAs(proxy);
        verify(productRepository, never()).findById(id);
    }

    @Test
    void requireManagedProduct_found_returnsIt() {
        UUID id = UUID.randomUUID();
        Product p = Product.builder().id(id).name("Widget").build();
        when(productRepository.findById(id)).thenReturn(Optional.of(p));

        assertThat(entityAccess.requireManagedProduct(id)).isSameAs(p);
    }

    @Test
    void requireManagedProduct_missing_throws() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entityAccess.requireManagedProduct(id))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
