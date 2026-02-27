package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.ReviewResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ReviewSummaryResponseDTO;
import com.mirai.inventoryservice.dtos.responses.UserResponseDTO;
import com.mirai.inventoryservice.dtos.responses.UserReviewStatsResponseDTO;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.review.Review;
import com.mirai.inventoryservice.repositories.ReviewDailyCountRepository;
import com.mirai.inventoryservice.repositories.ReviewRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
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

@Service
public class ReviewService {

    private final ReviewDailyCountRepository dailyCountRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public ReviewService(
            ReviewDailyCountRepository dailyCountRepository,
            ReviewRepository reviewRepository,
            UserRepository userRepository) {
        this.dailyCountRepository = dailyCountRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // User-based review tracking methods
    // -------------------------------------------------------------------------

    public List<UserResponseDTO> getReviewTrackedUsers() {
        return userRepository.findByIsReviewTrackedTrueOrderByFullNameAsc()
                .stream()
                .map(this::toUserDTO)
                .collect(Collectors.toList());
    }

    public List<UserResponseDTO> getAllUsersForReviewManagement() {
        return userRepository.findAllByOrderByFullNameAsc()
                .stream()
                .map(this::toUserDTO)
                .collect(Collectors.toList());
    }

    public UserResponseDTO getUserForReviewTracking(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        return toUserDTO(user);
    }

    @Transactional
    public UserResponseDTO updateUserReviewTracking(UUID userId, String canonicalName, List<String> nameVariants, Boolean isReviewTracked) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        if (canonicalName != null) {
            user.setCanonicalName(canonicalName);
        }

        if (nameVariants != null) {
            user.setNameVariants(nameVariants);
        }

        if (isReviewTracked != null) {
            user.setIsReviewTracked(isReviewTracked);
        }

        user = userRepository.save(user);
        return toUserDTO(user);
    }

    // -------------------------------------------------------------------------
    // Summary methods
    // -------------------------------------------------------------------------

    public List<ReviewSummaryResponseDTO> getMonthlySummaries(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<Object[]> results = dailyCountRepository.getMonthlySummariesByUser(startDate, endDate);

        return results.stream()
                .map(row -> {
                    UUID userId = (UUID) row[0];
                    String userName = (String) row[1];
                    Long totalReviews = (Long) row[2];
                    Double avgPerDay = (Double) row[3];

                    return ReviewSummaryResponseDTO.builder()
                            .userId(userId)
                            .userName(userName)
                            .totalReviews(totalReviews.intValue())
                            .averageReviewsPerDay(avgPerDay)
                            .lastReviewDate(null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Individual review methods
    // -------------------------------------------------------------------------

    public Page<ReviewResponseDTO> getUserReviews(UUID userId, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Page<Review> reviews;

        if (fromDate != null && toDate != null) {
            reviews = reviewRepository.findByUserIdAndDateRange(userId, fromDate, toDate, pageable);
        } else {
            reviews = reviewRepository.findByUserId(userId, pageable);
        }

        return reviews.map(this::toReviewDTO);
    }

    // -------------------------------------------------------------------------
    // User stats methods
    // -------------------------------------------------------------------------

    public UserReviewStatsResponseDTO getUserReviewStats(UUID userId, Integer year, Integer month) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        // Get all-time stats
        List<Object[]> allTimeResults = dailyCountRepository.getAllTimeStatsByUser(userId);
        Integer allTimeReviewCount = 0;
        LocalDate firstReviewDate = null;
        LocalDate lastReviewDate = null;

        if (!allTimeResults.isEmpty() && allTimeResults.get(0)[0] != null) {
            Object[] row = allTimeResults.get(0);
            allTimeReviewCount = ((Number) row[0]).intValue();
            firstReviewDate = (LocalDate) row[1];
            lastReviewDate = (LocalDate) row[2];
        }

        // Get selected month stats
        Integer selectedMonthReviewCount = 0;
        if (year != null && month != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDate startDate = yearMonth.atDay(1);
            LocalDate endDate = yearMonth.atEndOfMonth();

            List<Object[]> monthResults = dailyCountRepository.getMonthlySummariesByUser(startDate, endDate);
            for (Object[] row : monthResults) {
                if (userId.equals(row[0])) {
                    selectedMonthReviewCount = ((Number) row[2]).intValue();
                    break;
                }
            }
        }

        // Calculate all-time rank
        List<Object[]> rankings = dailyCountRepository.getAllTimeTotalsByUser();
        int allTimeRank = 0;
        for (int i = 0; i < rankings.size(); i++) {
            if (userId.equals(rankings.get(i)[0])) {
                allTimeRank = i + 1;
                break;
            }
        }

        return UserReviewStatsResponseDTO.builder()
                .userId(userId)
                .userName(user.getFullName())
                .allTimeReviewCount(allTimeReviewCount)
                .firstReviewDate(firstReviewDate)
                .lastReviewDate(lastReviewDate)
                .selectedMonthReviewCount(selectedMonthReviewCount)
                .allTimeRank(allTimeRank)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private UserResponseDTO toUserDTO(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .canonicalName(user.getCanonicalName())
                .nameVariants(user.getNameVariants() != null
                        ? user.getNameVariants()
                        : List.of())
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
