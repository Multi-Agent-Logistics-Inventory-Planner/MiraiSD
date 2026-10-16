package com.mirai.inventoryservice.reviews.application;

import com.mirai.inventoryservice.identity.application.UserService;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.reviews.api.ReviewResponseDTO;
import com.mirai.inventoryservice.reviews.api.ReviewSummaryResponseDTO;
import com.mirai.inventoryservice.reviews.api.ReviewUserDTO;
import com.mirai.inventoryservice.reviews.api.UserReviewStatsResponseDTO;
import com.mirai.inventoryservice.reviews.domain.Review;
import com.mirai.inventoryservice.reviews.infrastructure.ReviewDailyCountRepository;
import com.mirai.inventoryservice.reviews.infrastructure.ReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Every operation takes the authorized site first. User-addressed operations resolve the user
 * through a membership at the site (any row for reads, an active one for writes), so a user
 * outside the site is "not found" before any review data is read or written.
 */
@Service
public class ReviewService {

    private final ReviewDailyCountRepository dailyCountRepository;
    private final ReviewRepository reviewRepository;
    private final UserService userService;

    public ReviewService(
            ReviewDailyCountRepository dailyCountRepository,
            ReviewRepository reviewRepository,
            UserService userService) {
        this.dailyCountRepository = dailyCountRepository;
        this.reviewRepository = reviewRepository;
        this.userService = userService;
    }

    public List<ReviewUserDTO> getReviewTrackedUsers(UUID siteId) {
        return userService.findTrackedSiteMembers(siteId).stream()
                .map(this::toUserDTO)
                .collect(Collectors.toList());
    }

    public List<ReviewUserDTO> getAllUsersForReviewManagement(UUID siteId) {
        return userService.findActiveSiteMembers(siteId).stream()
                .map(this::toUserDTO)
                .collect(Collectors.toList());
    }

    public ReviewUserDTO getUserForReviewTracking(UUID siteId, UUID userId) {
        return toUserDTO(requireSiteMember(siteId, userId));
    }

    @Transactional
    public ReviewUserDTO updateUserReviewTracking(
            UUID siteId, UUID userId, String canonicalName, List<String> nameVariants, Boolean isReviewTracked) {
        User user = requireActiveSiteMember(siteId, userId);
        if (canonicalName != null) {
            user.setCanonicalName(canonicalName);
        }
        if (nameVariants != null) {
            user.setNameVariants(nameVariants);
        }
        if (isReviewTracked != null) {
            user.setIsReviewTracked(isReviewTracked);
        }
        return toUserDTO(userService.save(user));
    }

    public List<ReviewSummaryResponseDTO> getMonthlySummaries(UUID siteId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        List<Object[]> results = dailyCountRepository.getMonthlySummariesByUserAndSite(
                siteId, yearMonth.atDay(1), yearMonth.atEndOfMonth());

        return results.stream()
                .map(row -> ReviewSummaryResponseDTO.builder()
                        .userId((UUID) row[0])
                        .userName((String) row[1])
                        .totalReviews(((Long) row[2]).intValue())
                        .averageReviewsPerDay((Double) row[3])
                        .lastReviewDate(null)
                        .build())
                .collect(Collectors.toList());
    }

    public Page<ReviewResponseDTO> getUserReviews(
            UUID siteId, UUID userId, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        requireSiteMember(siteId, userId);
        Page<Review> reviews = (fromDate != null && toDate != null)
                ? reviewRepository.findByUserIdAndSiteIdAndDateRangeWithUser(userId, siteId, fromDate, toDate, pageable)
                : reviewRepository.findByUserIdAndSiteIdWithUser(userId, siteId, pageable);
        return reviews.map(this::toReviewDTO);
    }

    public UserReviewStatsResponseDTO getUserReviewStats(UUID siteId, UUID userId, Integer year, Integer month) {
        User user = requireSiteMember(siteId, userId);

        List<Object[]> allTimeResults = dailyCountRepository.getAllTimeStatsByUserAndSite(userId, siteId);
        Integer allTimeReviewCount = 0;
        LocalDate firstReviewDate = null;
        LocalDate lastReviewDate = null;
        if (!allTimeResults.isEmpty() && allTimeResults.get(0)[0] != null) {
            Object[] row = allTimeResults.get(0);
            allTimeReviewCount = ((Number) row[0]).intValue();
            firstReviewDate = (LocalDate) row[1];
            lastReviewDate = (LocalDate) row[2];
        }

        Integer selectedMonthReviewCount = 0;
        Integer selectedMonthTotalReviews = null;
        Double selectedMonthPercentage = null;
        if (year != null && month != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            List<Object[]> monthResults = dailyCountRepository.getMonthlySummariesByUserAndSite(
                    siteId, yearMonth.atDay(1), yearMonth.atEndOfMonth());

            long totalInMonth = 0;
            for (Object[] row : monthResults) {
                long userTotal = ((Number) row[2]).longValue();
                totalInMonth += userTotal;
                if (userId.equals(row[0])) {
                    selectedMonthReviewCount = (int) userTotal;
                }
            }
            selectedMonthTotalReviews = (int) totalInMonth;
            if (totalInMonth > 0) {
                selectedMonthPercentage = 100.0 * selectedMonthReviewCount / totalInMonth;
            }
        }

        int allTimeRank = (int) dailyCountRepository.getAllTimeRankByUserAndSite(userId, siteId);

        return UserReviewStatsResponseDTO.builder()
                .userId(userId)
                .userName(user.getFullName())
                .allTimeReviewCount(allTimeReviewCount)
                .firstReviewDate(firstReviewDate)
                .lastReviewDate(lastReviewDate)
                .selectedMonthReviewCount(selectedMonthReviewCount)
                .selectedMonthTotalReviews(selectedMonthTotalReviews)
                .selectedMonthPercentage(selectedMonthPercentage)
                .allTimeRank(allTimeRank)
                .build();
    }

    private User requireSiteMember(UUID siteId, UUID userId) {
        return userService.findSiteMember(siteId, userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private User requireActiveSiteMember(UUID siteId, UUID userId) {
        return userService.findActiveSiteMember(siteId, userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private ReviewUserDTO toUserDTO(User user) {
        return ReviewUserDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .canonicalName(user.getCanonicalName())
                .nameVariants(user.getNameVariants() != null ? user.getNameVariants() : List.of())
                .isReviewTracked(user.getIsReviewTracked())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private ReviewResponseDTO toReviewDTO(Review review) {
        return ReviewResponseDTO.builder()
                .id(review.getId())
                .externalId(review.getExternalId())
                .userId(review.getUser() != null ? review.getUser().getId() : null)
                .userName(review.getUser() != null ? review.getUser().getFullName() : null)
                .reviewDate(review.getReviewDate())
                .reviewText(review.getReviewText())
                .rating(review.getRating())
                .reviewerName(review.getReviewerName())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
