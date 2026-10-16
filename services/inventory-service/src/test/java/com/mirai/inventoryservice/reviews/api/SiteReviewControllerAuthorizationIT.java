package com.mirai.inventoryservice.reviews.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.reviews.domain.Review;
import com.mirai.inventoryservice.reviews.domain.ReviewDailyCount;
import com.mirai.inventoryservice.reviews.infrastructure.ReviewDailyCountRepository;
import com.mirai.inventoryservice.reviews.infrastructure.ReviewRepository;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SiteReviewControllerAuthorizationIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository membershipRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ReviewDailyCountRepository dailyCountRepository;

    private Site callerSite(String code) {
        adminToken();
        Site site = siteRepository.save(Site.builder().name(code + " Site").code(code).build());
        User admin = userRepository.findByEmail("admin.persona@test.internal").orElseThrow();
        membershipRepository.save(UserSiteMembership.builder()
                .userId(admin.getId()).siteId(site.getId()).isActive(true).build());
        return site;
    }

    private User tracked(String name, Site memberOf) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@test.internal");
        user.setRole(UserRole.EMPLOYEE);
        user.setIsReviewTracked(true);
        user = userRepository.save(user);
        if (memberOf != null) {
            membershipRepository.save(UserSiteMembership.builder()
                    .userId(user.getId()).siteId(memberOf.getId()).isActive(true).build());
        }
        return user;
    }

    private void review(User user, Site site) {
        reviewRepository.save(Review.builder()
                .siteId(site.getId()).user(user).externalId("ext-" + UUID.randomUUID())
                .reviewDate(LocalDate.of(2026, 9, 10)).rating(5).reviewText("great").build());
    }

    @Test
    @DisplayName("tracked users lists only this site's members")
    void trackedUsers_areLimitedToTheSite() throws Exception {
        Site site = callerSite("REV-USR-A");
        User member = tracked("Site Member", site);
        User outsider = tracked("Outside Person", null);

        mockMvc.perform(get("/api/v1/sites/{siteId}/reviews/users/tracked", site.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(member.getId().toString())))
                .andExpect(jsonPath("$[*].id", not(hasItem(outsider.getId().toString()))));
    }

    @Test
    @DisplayName("a user who is not a member of the site is 404 on the user and review routes")
    void nonMemberUser_isNotFound() throws Exception {
        Site site = callerSite("REV-NOM-A");
        User outsider = tracked("Outside Person", null);

        mockMvc.perform(get("/api/v1/sites/{siteId}/reviews/users/{id}", site.getId(), outsider.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/sites/{siteId}/reviews/users/{id}/reviews", site.getId(), outsider.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/sites/{siteId}/reviews/users/{id}/stats", site.getId(), outsider.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a member's reviews exclude reviews recorded at another site")
    void memberReviews_excludeOtherSites() throws Exception {
        Site site = callerSite("REV-LST-A");
        Site other = siteRepository.save(Site.builder().name("Other").code("REV-LST-B").build());
        User member = tracked("Both Sites", site);
        membershipRepository.save(UserSiteMembership.builder()
                .userId(member.getId()).siteId(other.getId()).isActive(true).build());
        review(member, site);
        review(member, other);

        mockMvc.perform(get("/api/v1/sites/{siteId}/reviews/users/{id}/reviews", site.getId(), member.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("monthly summaries count only this site's daily counts")
    void summaries_countOnlyTheSite() throws Exception {
        Site site = callerSite("REV-SUM-A");
        Site other = siteRepository.save(Site.builder().name("Other").code("REV-SUM-B").build());
        User member = tracked("Counted Person", site);
        dailyCountRepository.save(ReviewDailyCount.builder()
                .siteId(site.getId()).user(member).date(LocalDate.of(2026, 9, 10)).reviewCount(2).coinsAwarded(2).build());
        dailyCountRepository.save(ReviewDailyCount.builder()
                .siteId(other.getId()).user(member).date(LocalDate.of(2026, 9, 11)).reviewCount(7).coinsAwarded(7).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/reviews/summaries", site.getId())
                        .param("year", "2026").param("month", "9")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId=='" + member.getId() + "')].totalReviews").value(2));
    }

    @Test
    @DisplayName("a former member still listed in the summary can be opened, but not edited")
    void formerMember_isReadableButNotEditable() throws Exception {
        Site site = callerSite("REV-FRM-A");
        User former = tracked("Former Member", null);
        membershipRepository.save(UserSiteMembership.builder()
                .userId(former.getId()).siteId(site.getId()).isActive(false).build());
        dailyCountRepository.save(ReviewDailyCount.builder()
                .siteId(site.getId()).user(former).date(LocalDate.of(2026, 9, 10)).reviewCount(3).coinsAwarded(3).build());
        review(former, site);

        mockMvc.perform(get("/api/v1/sites/{siteId}/reviews/summaries", site.getId())
                        .param("year", "2026").param("month", "9")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(jsonPath("$[?(@.userId=='" + former.getId() + "')].totalReviews").value(3));
        mockMvc.perform(get("/api/v1/sites/{siteId}/reviews/users/{id}/stats", site.getId(), former.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sites/{siteId}/reviews/users/{id}/reviews", site.getId(), former.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/sites/{siteId}/reviews/users/{id}/tracking", site.getId(), former.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("{\"isReviewTracked\": false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an oversized page request is capped")
    void memberReviews_capThePageSize() throws Exception {
        Site site = callerSite("REV-CAP-A");
        User member = tracked("Capped Person", site);

        mockMvc.perform(get("/api/v1/sites/{siteId}/reviews/users/{id}/reviews", site.getId(), member.getId())
                        .param("size", "1000000")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(org.hamcrest.Matchers.lessThanOrEqualTo(200)));
    }

    @Test
    @DisplayName("tracking update rejects an oversized canonical name")
    void trackingUpdate_rejectsOversizedInput() throws Exception {
        Site site = callerSite("REV-VAL-A");
        User member = tracked("Validated Person", site);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/sites/{siteId}/reviews/users/{id}/tracking", site.getId(), member.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("{\"canonicalName\": \"" + "x".repeat(300) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a caller with no membership at the path's site is rejected")
    void withoutMembership_isRejected() throws Exception {
        Site otherSite = siteRepository.save(Site.builder().name("No Member").code("REV-NOM-Z").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/reviews/users/tracked", otherSite.getId())
                        .header("Authorization", "Bearer " + employeeToken()))
                .andExpect(status().is4xxClientError());
    }
}
