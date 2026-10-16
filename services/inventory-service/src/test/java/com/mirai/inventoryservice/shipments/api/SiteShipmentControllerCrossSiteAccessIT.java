package com.mirai.inventoryservice.shipments.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.shipments.api.ShipmentRequestDTO;
import com.mirai.inventoryservice.shipments.application.ShipmentService;
import com.mirai.inventoryservice.shipments.domain.Shipment;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * .specs/phase-7-shipments T-5/T-6, external review advisory: {@link SiteShipmentControllerSecurityTest}
 * only proves a caller with no membership at the path's site is rejected before reaching a
 * shipment lookup; it never proves a caller who IS a member of one site cannot reach a shipment
 * that belongs to a different, real site through that site's own path. Every read/write handler
 * resolves the shipment via {@code ShipmentService.getShipmentById(contextSiteId, id)} ->
 * {@code ShipmentRepository.findByIdAndSite_IdWithAssociations}, so a foreign-site id is
 * "not found," not "forbidden" -- this proves that shape at the HTTP layer for the three routes
 * with no request-body validation to short-circuit first (GET, DELETE, receive). PUT/PATCH/
 * undo-receive route through the identical site-scoped lookup (confirmed by source inspection,
 * {@code ShipmentService.java} lines ~263/763/899) but aren't separately covered here.
 */
class SiteShipmentControllerCrossSiteAccessIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository userSiteMembershipRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ShipmentService shipmentService;

    private Site siteWithMembership(String email, String code) {
        Site site = siteRepository.save(Site.builder().name(code + " Site").code(code).build());
        User user = userRepository.findByEmail(email).orElseThrow();
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());
        return site;
    }

    private UUID createShipmentAtSite(UUID siteId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder()
                .name("Cross Site Access Category " + suffix)
                .slug("cross-site-access-category-" + suffix)
                .build());
        Product product = productRepository.save(Product.builder()
                .sku("XSITE-ACCESS-" + suffix)
                .name("Cross Site Access Product")
                .category(category)
                .reorderPoint(5)
                .targetStockLevel(20)
                .leadTimeDays(7)
                .isActive(true)
                .quantity(0)
                .build());

        ShipmentRequestDTO requestDTO = new ShipmentRequestDTO();
        requestDTO.setShipmentNumber("XSITE-SHIP-" + suffix);
        requestDTO.setSupplierName("Cross Site Supplier");
        requestDTO.setStatus(ShipmentStatus.PENDING);
        requestDTO.setOrderDate(LocalDate.now());
        com.mirai.inventoryservice.shipments.api.ShipmentItemRequestDTO item =
                new com.mirai.inventoryservice.shipments.api.ShipmentItemRequestDTO();
        item.setItemId(product.getId());
        item.setOrderedQuantity(10);
        requestDTO.setItems(List.of(item));

        Shipment shipment = shipmentService.createShipment(siteId, requestDTO);
        return shipment.getId();
    }

    @Test
    @DisplayName("GET a foreign-site shipment id through this site's path is 404, not the other site's data")
    void getShipmentById_foreignSiteShipment_returnsNotFound() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-GET-A");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign B").code("XSITE-GET-B").build());
        UUID foreignShipmentId = createShipmentAtSite(foreignSite.getId());

        mockMvc.perform(get("/api/v1/sites/{siteId}/shipments/{id}", callerSite.getId(), foreignShipmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE a foreign-site shipment id through this site's path is 404 and does not delete it")
    void deleteShipment_foreignSiteShipment_returnsNotFoundAndLeavesItIntact() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-DEL-A");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign B").code("XSITE-DEL-B").build());
        UUID foreignShipmentId = createShipmentAtSite(foreignSite.getId());

        mockMvc.perform(delete("/api/v1/sites/{siteId}/shipments/{id}", callerSite.getId(), foreignShipmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        assertThat(shipmentService.getShipmentById(foreignSite.getId(), foreignShipmentId)).isNotNull();
    }

    @Test
    @DisplayName("receive against a foreign-site shipment id through this site's path is 404")
    void receiveShipment_foreignSiteShipment_returnsNotFound() throws Exception {
        String token = adminToken();
        Site callerSite = siteWithMembership("admin.persona@test.internal", "XSITE-RECV-A");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign B").code("XSITE-RECV-B").build());
        UUID foreignShipmentId = createShipmentAtSite(foreignSite.getId());
        String body = """
                {
                    "actualDeliveryDate": "2024-01-26",
                    "receivedBy": "550e8400-e29b-41d4-a716-446655440000",
                    "itemReceipts": []
                }
                """;

        mockMvc.perform(post("/api/v1/sites/{siteId}/shipments/{id}/receive", callerSite.getId(), foreignShipmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
