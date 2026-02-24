package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.ReviewEmployeeResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ReviewResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ReviewSummaryResponseDTO;
import com.mirai.inventoryservice.dtos.responses.UserResponseDTO;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.review.Review;
import com.mirai.inventoryservice.models.review.ReviewEmployee;
import com.mirai.inventoryservice.repositories.ReviewDailyCountRepository;
import com.mirai.inventoryservice.repositories.ReviewEmployeeRepository;
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

    private final ReviewEmployeeRepository employeeRepository;
    private final ReviewDailyCountRepository dailyCountRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public ReviewService(
            ReviewEmployeeRepository employeeRepository,
            ReviewDailyCountRepository dailyCountRepository,
            ReviewRepository reviewRepository,
            UserRepository userRepository) {
        this.employeeRepository = employeeRepository;
        this.dailyCountRepository = dailyCountRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // Employee methods
    // -------------------------------------------------------------------------

    public List<ReviewEmployeeResponseDTO> getAllEmployees() {
        return employeeRepository.findByIsActiveTrueOrderByCanonicalNameAsc()
                .stream()
                .map(this::toEmployeeDTO)
                .collect(Collectors.toList());
    }

    public ReviewEmployeeResponseDTO getEmployeeById(UUID id) {
        ReviewEmployee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Review employee not found: " + id));
        return toEmployeeDTO(employee);
    }

    @Transactional
    public ReviewEmployeeResponseDTO createEmployee(String canonicalName, List<String> nameVariants) {
        if (employeeRepository.existsByCanonicalName(canonicalName)) {
            throw new IllegalArgumentException("Employee with name already exists: " + canonicalName);
        }

        ReviewEmployee employee = ReviewEmployee.builder()
                .canonicalName(canonicalName)
                .nameVariants(nameVariants)
                .isActive(true)
                .build();

        employee = employeeRepository.save(employee);
        return toEmployeeDTO(employee);
    }

    @Transactional
    public ReviewEmployeeResponseDTO updateEmployee(UUID id, String canonicalName, List<String> nameVariants, Boolean isActive) {
        ReviewEmployee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Review employee not found: " + id));

        if (canonicalName != null && !canonicalName.equals(employee.getCanonicalName())) {
            if (employeeRepository.existsByCanonicalName(canonicalName)) {
                throw new IllegalArgumentException("Employee with name already exists: " + canonicalName);
            }
            employee.setCanonicalName(canonicalName);
        }

        if (nameVariants != null) {
            employee.setNameVariants(nameVariants);
        }

        if (isActive != null) {
            employee.setIsActive(isActive);
        }

        employee = employeeRepository.save(employee);
        return toEmployeeDTO(employee);
    }

    // -------------------------------------------------------------------------
    // User-based review tracking methods (new)
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
    public UserResponseDTO updateUserReviewTracking(UUID userId, List<String> nameVariants, Boolean isReviewTracked) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

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

        List<Object[]> results = dailyCountRepository.getMonthlySummaries(startDate, endDate);

        return results.stream()
                .map(row -> {
                    UUID employeeId = (UUID) row[0];
                    String employeeName = (String) row[1];
                    Long totalReviews = (Long) row[2];
                    Double avgPerDay = (Double) row[3];

                    return ReviewSummaryResponseDTO.builder()
                            .employeeId(employeeId)
                            .employeeName(employeeName)
                            .totalReviews(totalReviews.intValue())
                            .averageReviewsPerDay(avgPerDay)
                            .lastReviewDate(null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<ReviewSummaryResponseDTO> getMonthlySummariesByUser(int year, int month) {
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

    public Page<ReviewResponseDTO> getEmployeeReviews(UUID employeeId, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Page<Review> reviews;

        if (fromDate != null && toDate != null) {
            reviews = reviewRepository.findByEmployeeIdAndDateRange(employeeId, fromDate, toDate, pageable);
        } else {
            reviews = reviewRepository.findByEmployeeId(employeeId, pageable);
        }

        return reviews.map(this::toReviewDTO);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private ReviewEmployeeResponseDTO toEmployeeDTO(ReviewEmployee employee) {
        return ReviewEmployeeResponseDTO.builder()
                .id(employee.getId())
                .canonicalName(employee.getCanonicalName())
                .nameVariants(employee.getNameVariants() != null
                        ? employee.getNameVariants()
                        : List.of())
                .isActive(employee.getIsActive())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .build();
    }

    private UserResponseDTO toUserDTO(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
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
                .employeeId(review.getEmployee() != null ? review.getEmployee().getId() : null)
                .employeeName(review.getEmployee() != null ? review.getEmployee().getCanonicalName() : null)
                .reviewDate(review.getReviewDate())
                .reviewText(review.getReviewText())
                .rating(review.getRating())
                .reviewerName(review.getReviewerName())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
