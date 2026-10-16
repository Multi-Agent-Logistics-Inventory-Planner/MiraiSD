package com.mirai.inventoryservice.audit.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.audit.domain.AuditLog;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.audit.infrastructure.AuditLogRepository;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SiteAuditLogControllerAuthorizationIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository userSiteMembershipRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Site siteWithMembership(String code) {
        adminToken();
        Site site = siteRepository.save(Site.builder().name(code + " Site").code(code).build());
        User user = userRepository.findByEmail("admin.persona@test.internal").orElseThrow();
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());
        return site;
    }

    private UUID logAt(UUID siteId) {
        return auditLogRepository.save(AuditLog.builder()
                .siteId(siteId)
                .reason(StockMovementReason.ADJUSTMENT)
                .itemCount(1)
                .totalQuantityMoved(1)
                .productSummary("seed")
                .build()).getId();
    }

    private void addMovement(UUID auditLogId, UUID movementSiteId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder()
                .name("aud-cat-" + suffix).slug("aud-cat-" + suffix).build());
        Product product = productRepository.save(Product.builder()
                .name("Audit Product").sku("AUD-" + suffix).category(category).quantity(0).reorderPoint(0).build());
        AuditLog log = auditLogRepository.findById(auditLogId).orElseThrow();
        stockMovementRepository.save(StockMovement.builder()
                .auditLog(log).site(siteRepository.getReferenceById(movementSiteId)).item(product).locationType(LocationType.NOT_ASSIGNED)
                .quantityChange(1).reason(StockMovementReason.ADJUSTMENT).at(OffsetDateTime.now()).build());
    }

    @Test
    @DisplayName("GET a foreign-site audit log id through this site's path is 404")
    void detail_foreignSiteAuditLog_returnsNotFound() throws Exception {
        Site callerSite = siteWithMembership("AUD-DET-A");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign").code("AUD-DET-B").build());
        UUID foreignLog = logAt(foreignSite.getId());

        mockMvc.perform(get("/api/v1/sites/{siteId}/audit-logs/{id}", callerSite.getId(), foreignLog)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET detail of this site's own audit log succeeds")
    void detail_ownSiteAuditLog_succeeds() throws Exception {
        Site callerSite = siteWithMembership("AUD-OWN-A");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign").code("AUD-OWN-B").build());
        UUID ownLog = logAt(callerSite.getId());
        addMovement(ownLog, callerSite.getId());
        addMovement(ownLog, foreignSite.getId());

        mockMvc.perform(get("/api/v1/sites/{siteId}/audit-logs/{id}", callerSite.getId(), ownLog)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownLog.toString()))
                .andExpect(jsonPath("$.movements.length()").value(1));
    }

    @Test
    @DisplayName("list returns only this site's audit logs")
    void list_returnsOnlyTheCallersSite() throws Exception {
        Site callerSite = siteWithMembership("AUD-LST-A");
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign").code("AUD-LST-B").build());
        UUID own = logAt(callerSite.getId());
        UUID foreign = logAt(foreignSite.getId());

        mockMvc.perform(get("/api/v1/sites/{siteId}/audit-logs", callerSite.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(own.toString())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(foreign.toString()))));
    }

    @Test
    @DisplayName("an oversized page request is capped")
    void list_capsThePageSize() throws Exception {
        Site callerSite = siteWithMembership("AUD-CAP-A");

        mockMvc.perform(get("/api/v1/sites/{siteId}/audit-logs", callerSite.getId())
                        .param("size", "1000000")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(org.hamcrest.Matchers.lessThanOrEqualTo(200)));
    }

    @Test
    @DisplayName("a caller with no membership at the path's site is rejected before any lookup")
    void list_withoutMembership_isRejected() throws Exception {
        Site otherSite = siteRepository.save(Site.builder().name("No Member").code("AUD-NOM-A").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/audit-logs", otherSite.getId())
                        .header("Authorization", "Bearer " + employeeToken()))
                .andExpect(status().is4xxClientError());
    }
}
