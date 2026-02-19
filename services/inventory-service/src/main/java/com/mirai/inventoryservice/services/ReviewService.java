package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.ReviewEmployeeResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ReviewResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ReviewSummaryResponseDTO;
import com.mirai.inventoryservice.models.review.Review;
import com.mirai.inventoryservice.models.review.ReviewEmployee;
import com.mirai.inventoryservice.repositories.ReviewDailyCountRepository;
import com.mirai.inventoryservice.repositories.ReviewEmployeeRepository;
import com.mirai.inventoryservice.repositories.ReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewEmployeeRepository employeeRepository;
    private final ReviewDailyCountRepository dailyCountRepository;
    private final ReviewRepository reviewRepository;

    public ReviewService(
            ReviewEmployeeRepository employeeRepository,
            ReviewDailyCountRepository dailyCountRepository,
            ReviewRepository reviewRepository) {
        this.employeeRepository = employeeRepository;
        this.dailyCountRepository = dailyCountRepository;
        this.reviewRepository = reviewRepository;
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
    // Summary methods
    // -------------------------------------------------------------------------

    public List<ReviewSummaryResponseDTO> getMonthlySummaries(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // Get all active employees
        List<ReviewEmployee> allEmployees = employeeRepository.findByIsActiveTrueOrderByCanonicalNameAsc();

        // Get summaries for employees with reviews in this period
        List<Object[]> results = dailyCountRepository.getMonthlySummaries(startDate, endDate);

        // Create a map of employeeId -> summary data
        Map<UUID, Object[]> summaryMap = results.stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> row
                ));

        // Build complete list including employees with 0 reviews
        return allEmployees.stream()
                .map(employee -> {
                    Object[] summary = summaryMap.get(employee.getId());
                    if (summary != null) {
                        // Employee has reviews this month
                        Long totalReviews = (Long) summary[2];
                        Double avgPerDay = (Double) summary[3];

                        return ReviewSummaryResponseDTO.builder()
                                .employeeId(employee.getId())
                                .employeeName(employee.getCanonicalName())
                                .totalReviews(totalReviews.intValue())
                                .averageReviewsPerDay(avgPerDay)
                                .lastReviewDate(null)
                                .build();
                    } else {
                        // Employee has no reviews this month
                        return ReviewSummaryResponseDTO.builder()
                                .employeeId(employee.getId())
                                .employeeName(employee.getCanonicalName())
                                .totalReviews(0)
                                .averageReviewsPerDay(0.0)
                                .lastReviewDate(null)
                                .build();
                    }
                })
                .sorted(Comparator.comparing(ReviewSummaryResponseDTO::getTotalReviews).reversed()
                        .thenComparing(ReviewSummaryResponseDTO::getEmployeeName))
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
