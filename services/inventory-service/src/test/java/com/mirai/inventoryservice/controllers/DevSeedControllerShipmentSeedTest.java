package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.shipments.domain.Shipment;
import com.mirai.inventoryservice.shipments.domain.ShipmentItem;
import com.mirai.inventoryservice.shipments.infrastructure.ShipmentRepository;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * .specs/phase-7-shipments T-5, round-4 finding 2: {@code DevSeedController.seedShipments()}
 * previously built {@code Shipment}/{@code ShipmentItem} rows with no {@code site}, which V71's
 * indexes and the eventual constrain migration's {@code NOT NULL} would both reject. Verifies the
 * fix without exercising the rest of {@code DevSeedController}'s large constructor (see
 * {@code DevSeedControllerTest} for the {@code seedAll()} coverage this deliberately doesn't
 * duplicate).
 */
@ExtendWith(MockitoExtension.class)
class DevSeedControllerShipmentSeedTest {

    @Mock ProductRepository productRepository;
    @Mock SiteRepository siteRepository;
    @Mock ShipmentRepository shipmentRepository;
    @InjectMocks DevSeedController controller;

    private Site mainSite;

    @BeforeEach
    void seedMainSiteAndOneProduct() {
        mainSite = Site.builder().id(UUID.randomUUID()).code("MAIN").build();
        when(siteRepository.findByCode("MAIN")).thenReturn(Optional.of(mainSite));
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .sku("DEVSEED-SHIP-1")
                .name("DevSeed Shipment Product")
                .unitCost(new BigDecimal("1.00"))
                .build();
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(shipmentRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void seedShipments_setsSiteOnEveryShipmentAndItem() {
        controller.seedShipments();

        ArgumentCaptor<List<Shipment>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(shipmentRepository).saveAll(captor.capture());
        List<Shipment> saved = captor.getValue();

        assertTrue(!saved.isEmpty(), "seedShipments should build at least one shipment");
        for (Shipment shipment : saved) {
            assertEquals(mainSite, shipment.getSite(), "Every seeded shipment must carry the MAIN site");
            assertNotNull(shipment.getItems());
            for (ShipmentItem item : shipment.getItems()) {
                assertEquals(mainSite, item.getSite(), "Every seeded shipment item must carry the MAIN site");
            }
        }
    }
}
