package com.mirai.inventoryservice.reviews.api;

import com.mirai.inventoryservice.reviews.application.ReviewService;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import com.mirai.inventoryservice.shared.web.PageLimits;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Site-scoped review routes. {@code siteId} is declared on each handler so the generated client
 * exposes it, but the trusted value always comes from {@link AuthorizedSiteContextHolder}.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/reviews")
public class SiteReviewController {

    private final ReviewService reviewService;

    public SiteReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    private UUID contextSiteId() {
        return AuthorizedSiteContextHolder.require().siteId();
    }

    @GetMapping("/users/tracked")
    public ResponseEntity<List<ReviewUserDTO>> getSiteReviewTrackedUsers(@PathVariable UUID siteId) {
        return ResponseEntity.ok(reviewService.getReviewTrackedUsers(contextSiteId()));
    }

    @GetMapping("/users/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<ReviewUserDTO>> getSiteUsersForReviewManagement(@PathVariable UUID siteId) {
        return ResponseEntity.ok(reviewService.getAllUsersForReviewManagement(contextSiteId()));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ReviewUserDTO> getSiteUserForReviewTracking(
            @PathVariable UUID siteId, @PathVariable UUID userId) {
        return ResponseEntity.ok(reviewService.getUserForReviewTracking(contextSiteId(), userId));
    }

    @PutMapping("/users/{userId}/tracking")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ReviewUserDTO> updateSiteUserReviewTracking(
            @PathVariable UUID siteId,
            @PathVariable UUID userId,
            @Valid @RequestBody UserReviewTrackingRequestDTO request) {
        return ResponseEntity.ok(reviewService.updateUserReviewTracking(
                contextSiteId(), userId, request.getCanonicalName(), request.getNameVariants(),
                request.getIsReviewTracked()));
    }

    @GetMapping("/summaries")
    public ResponseEntity<List<ReviewSummaryResponseDTO>> getSiteMonthlySummaries(
            @PathVariable UUID siteId,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(reviewService.getMonthlySummaries(contextSiteId(), year, month));
    }

    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<Page<ReviewResponseDTO>> getSiteUserReviews(
            @PathVariable UUID siteId,
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reviewService.getUserReviews(
                contextSiteId(), userId, fromDate, toDate, PageRequest.of(page, PageLimits.clampSize(size))));
    }

    @GetMapping("/users/{userId}/stats")
    public ResponseEntity<UserReviewStatsResponseDTO> getSiteUserReviewStats(
            @PathVariable UUID siteId,
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(reviewService.getUserReviewStats(contextSiteId(), userId, year, month));
    }
}
