package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.requests.UserReviewTrackingRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ReviewResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ReviewSummaryResponseDTO;
import com.mirai.inventoryservice.dtos.responses.UserResponseDTO;
import com.mirai.inventoryservice.dtos.responses.UserReviewStatsResponseDTO;
import com.mirai.inventoryservice.services.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // -------------------------------------------------------------------------
    // User-based review tracking endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/users/tracked")
    public ResponseEntity<List<UserResponseDTO>> getReviewTrackedUsers() {
        return ResponseEntity.ok(reviewService.getReviewTrackedUsers());
    }

    @GetMapping("/users/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponseDTO>> getAllUsersForReviewManagement() {
        return ResponseEntity.ok(reviewService.getAllUsersForReviewManagement());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponseDTO> getUserForReviewTracking(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewService.getUserForReviewTracking(id));
    }

    @PutMapping("/users/{id}/tracking")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> updateUserReviewTracking(
            @PathVariable UUID id,
            @RequestBody UserReviewTrackingRequestDTO request) {
        UserResponseDTO user = reviewService.updateUserReviewTracking(
                id,
                request.getCanonicalName(),
                request.getNameVariants(),
                request.getIsReviewTracked());
        return ResponseEntity.ok(user);
    }

    // -------------------------------------------------------------------------
    // Summary endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/summaries")
    public ResponseEntity<List<ReviewSummaryResponseDTO>> getMonthlySummaries(
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(reviewService.getMonthlySummaries(year, month));
    }

    // -------------------------------------------------------------------------
    // Individual review endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<Page<ReviewResponseDTO>> getUserReviews(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(reviewService.getUserReviews(userId, fromDate, toDate, pageable));
    }

    // -------------------------------------------------------------------------
    // User stats endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/users/{userId}/stats")
    public ResponseEntity<UserReviewStatsResponseDTO> getUserReviewStats(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(reviewService.getUserReviewStats(userId, year, month));
    }
}
