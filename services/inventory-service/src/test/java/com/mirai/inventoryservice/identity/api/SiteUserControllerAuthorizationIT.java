package com.mirai.inventoryservice.identity.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SiteUserControllerAuthorizationIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository membershipRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Site callerSite(String code) {
        adminToken();
        Site site = siteRepository.save(Site.builder().name(code + " Site").code(code).build());
        User admin = userRepository.findByEmail("admin.persona@test.internal").orElseThrow();
        membershipRepository.save(UserSiteMembership.builder()
                .userId(admin.getId()).siteId(site.getId()).isActive(true).build());
        return site;
    }

    private User user(String name, Site memberOf, boolean active) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@test.internal");
        user.setRole(UserRole.EMPLOYEE);
        user = userRepository.save(user);
        if (memberOf != null) {
            membershipRepository.save(UserSiteMembership.builder()
                    .userId(user.getId()).siteId(memberOf.getId()).isActive(active).build());
        }
        return user;
    }

    private void movement(User actor, Site site, OffsetDateTime at) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder()
                .name("usr-cat-" + suffix).slug("usr-cat-" + suffix).build());
        Product product = productRepository.save(Product.builder()
                .name("Users Product").sku("USR-" + suffix).category(category).quantity(0).reorderPoint(0).build());
        stockMovementRepository.save(StockMovement.builder()
                .site(site).item(product).actorId(actor.getId()).locationType(LocationType.NOT_ASSIGNED)
                .quantityChange(1).reason(StockMovementReason.ADJUSTMENT).at(at).build());
    }

    @Test
    @DisplayName("lists only the active members of this site")
    void list_isLimitedToActiveMembers() throws Exception {
        Site site = callerSite("USR-LST-A");
        Site other = siteRepository.save(Site.builder().name("Other").code("USR-LST-B").build());
        User member = user("Active Member", site, true);
        User former = user("Former Member", site, false);
        User elsewhere = user("Other Site Person", other, true);

        mockMvc.perform(get("/api/v1/sites/{siteId}/users", site.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(member.getId().toString())))
                .andExpect(jsonPath("$[*].id", not(hasItem(former.getId().toString()))))
                .andExpect(jsonPath("$[*].id", not(hasItem(elsewhere.getId().toString()))));
    }

    @Test
    @DisplayName("last audit time counts only movements at this site")
    void lastAudits_ignoreOtherSites() throws Exception {
        Site site = callerSite("USR-AUD-A");
        Site other = siteRepository.save(Site.builder().name("Other").code("USR-AUD-B").build());
        User both = user("Works Both", site, true);
        membershipRepository.save(UserSiteMembership.builder()
                .userId(both.getId()).siteId(other.getId()).isActive(true).build());
        User onlyElsewhere = user("Quiet Here", site, true);
        OffsetDateTime here = OffsetDateTime.now().minusDays(3).withNano(0);
        movement(both, site, here);
        movement(both, other, OffsetDateTime.now().minusHours(1));
        movement(onlyElsewhere, other, OffsetDateTime.now().minusHours(2));

        String body = mockMvc.perform(get("/api/v1/sites/{siteId}/users/last-audits", site.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey(both.getId().toString())))
                .andExpect(jsonPath("$", not(hasKey(onlyElsewhere.getId().toString()))))
                .andReturn().getResponse().getContentAsString();

        String reported = com.jayway.jsonpath.JsonPath.read(body, "$['" + both.getId() + "']");
        org.assertj.core.api.Assertions.assertThat(OffsetDateTime.parse(reported).toInstant())
                .isEqualTo(here.toInstant());
    }

    @Test
    @DisplayName("a caller with no membership at the site is refused")
    void nonMemberCaller_isRefused() throws Exception {
        adminToken();
        Site foreign = siteRepository.save(Site.builder().name("Foreign").code("USR-NOM-A").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/users", foreign.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/sites/{siteId}/users/last-audits", foreign.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("legacy list and last-audit routes carry deprecation headers and the v1 routes do not")
    void legacyRoutes_areDeprecated() throws Exception {
        String token = employeeTokenWithMainMembership();
        for (String path : new String[] {"/api/users", "/api/users/last-audits", "/api/users/" + UUID.randomUUID() + "/last-audit"}) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Deprecation"))
                    .andExpect(header().string("Link", org.hamcrest.Matchers.containsString("rel=\"deprecation\"")))
                    .andExpect(header().doesNotExist("Sunset"));
        }

        Site site = callerSite("USR-DEP-A");
        mockMvc.perform(get("/api/v1/sites/{siteId}/users", site.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Deprecation"));
    }

    private void joinSite(String email, Site site) {
        User user = userRepository.findByEmail(email).orElseThrow();
        membershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());
    }

    @Test
    @DisplayName("employees and assistant managers read their own site and are refused at another")
    void nonAdminRoles_readOwnSiteOnly() throws Exception {
        String employee = employeeToken();
        String manager = assistantManagerToken();
        Site mine = siteRepository.save(Site.builder().name("Mine").code("USR-ROL-A").build());
        Site foreign = siteRepository.save(Site.builder().name("Foreign").code("USR-ROL-B").build());
        joinSite("employee.persona@test.internal", mine);
        joinSite("assistant-manager.persona@test.internal", mine);

        for (String token : new String[] {employee, manager}) {
            for (String suffix : new String[] {"", "/last-audits"}) {
                mockMvc.perform(get("/api/v1/sites/{siteId}/users" + suffix, mine.getId())
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
                mockMvc.perform(get("/api/v1/sites/{siteId}/users" + suffix, foreign.getId())
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isForbidden());
            }
        }
        mockMvc.perform(get("/api/v1/sites/{siteId}/users", mine.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("legacy list and last-audit routes leave out users who only work at another site")
    void legacyRoutes_excludeUsersOutsideMain() throws Exception {
        String token = employeeTokenWithMainMembership();
        Site other = siteRepository.save(Site.builder().name("Other").code("USR-LEG-B").build());
        User outsider = user("Other Site Only", other, true);
        movement(outsider, other, OffsetDateTime.now().minusHours(1));

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(outsider.getId().toString()))))
                .andExpect(jsonPath("$[*].email", hasItem("employee.persona@test.internal")));
        mockMvc.perform(get("/api/users/last-audits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasKey(outsider.getId().toString()))));
        mockMvc.perform(get("/api/users/{id}/last-audit", outsider.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("legacy routes send deprecation headers on error responses too, and writes are not marked")
    void legacyRoutes_deprecationHeadersOnErrors() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("Deprecation"));
        mockMvc.perform(get("/api/users/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + employeeTokenWithMainMembership()))
                .andExpect(header().doesNotExist("Deprecation"));
    }

    @Test
    @DisplayName("legacy routes send deprecation headers on authentication failures")
    void legacyRoutes_deprecationHeadersOnAuthFailures() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Deprecation"))
                .andExpect(header().exists("Link"));
        mockMvc.perform(get("/api/users/last-audits").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Deprecation"));
    }
}
