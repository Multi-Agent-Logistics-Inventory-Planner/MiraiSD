package com.mirai.inventoryservice.reviews.application;

import com.mirai.inventoryservice.identity.application.UserService;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.reviews.api.ReviewSummaryResponseDTO;
import com.mirai.inventoryservice.reviews.api.ReviewUserDTO;
import com.mirai.inventoryservice.reviews.domain.Review;
import com.mirai.inventoryservice.reviews.infrastructure.ReviewDailyCountRepository;
import com.mirai.inventoryservice.reviews.infrastructure.ReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceSiteTest {

    @Mock private ReviewDailyCountRepository dailyCountRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private UserService userService;

    private ReviewService service;
    private final UUID siteId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReviewService(dailyCountRepository, reviewRepository, userService);
    }

    private User member() {
        User user = new User();
        user.setId(userId);
        user.setFullName("Alice");
        return user;
    }

    @Test
    void trackedUsersAreLimitedToTheSitesMembers() {
        when(userService.findTrackedSiteMembers(siteId)).thenReturn(List.of(member()));

        List<ReviewUserDTO> result = service.getReviewTrackedUsers(siteId);

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getId());
    }

    @Test
    void managementListIsLimitedToTheSitesMembers() {
        when(userService.findActiveSiteMembers(siteId)).thenReturn(List.of(member()));

        assertEquals(1, service.getAllUsersForReviewManagement(siteId).size());
    }

    @Test
    void monthlySummariesAreReadForTheCallersSiteOnly() {
        Object[] row = {userId, "Alice", 3L, 1.5d};
        when(dailyCountRepository.getMonthlySummariesByUserAndSite(
                siteId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(Collections.singletonList(row));

        List<ReviewSummaryResponseDTO> result = service.getMonthlySummaries(siteId, 2026, 9);

        assertEquals(3, result.get(0).getTotalReviews());
    }

    @Test
    void nonMemberIsNotFoundBeforeAnyReviewLookup() {
        when(userService.findSiteMember(siteId, userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.getUserReviews(siteId, userId, null, null, PageRequest.of(0, 20)));

        verify(reviewRepository, never()).findByUserIdAndSiteIdWithUser(any(), any(), any());
    }

    @Test
    void memberReviewsAreReadWithinTheSite() {
        when(userService.findSiteMember(siteId, userId)).thenReturn(Optional.of(member()));
        Page<Review> empty = new PageImpl<>(List.of());
        when(reviewRepository.findByUserIdAndSiteIdWithUser(userId, siteId, PageRequest.of(0, 20))).thenReturn(empty);

        service.getUserReviews(siteId, userId, null, null, PageRequest.of(0, 20));

        verify(reviewRepository).findByUserIdAndSiteIdWithUser(userId, siteId, PageRequest.of(0, 20));
    }

    @Test
    void trackingUpdateForANonMemberIsNotFoundAndWritesNothing() {
        when(userService.findActiveSiteMember(siteId, userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.updateUserReviewTracking(siteId, userId, "x", null, true));

        verify(userService, never()).save(any());
    }

    @Test
    void statsForANonMemberAreNotFoundBeforeAnyCountQuery() {
        when(userService.findSiteMember(siteId, userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.getUserReviewStats(siteId, userId, null, null));

        verify(dailyCountRepository, never()).getAllTimeStatsByUserAndSite(any(), any());
    }
}
