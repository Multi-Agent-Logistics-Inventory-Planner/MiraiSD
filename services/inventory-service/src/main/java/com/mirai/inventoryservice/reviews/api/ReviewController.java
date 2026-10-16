package com.mirai.inventoryservice.reviews.api;

import com.mirai.inventoryservice.identity.application.LegacyMainSiteContextResolver;
import com.mirai.inventoryservice.reviews.application.ReviewService;
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

/** Legacy unversioned review routes; they keep meaning MAIN and are deprecated in favour of the site routes. */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final LegacyMainSiteContextResolver legacyMainSiteContextResolver;

    public ReviewController(ReviewService reviewService, LegacyMainSiteContextResolver legacyMainSiteContextResolver) {
        this.reviewService = reviewService;
        this.legacyMainSiteContextResolver = legacyMainSiteContextResolver;
    }

    private UUID mainSiteId() {
        return legacyMainSiteContextResolver.requireMain().siteId();
    }

    @GetMapping("/users/tracked")
    public ResponseEntity<List<ReviewUserDTO>> getReviewTrackedUsers() {
        return ResponseEntity.ok(reviewService.getReviewTrackedUsers(mainSiteId()));
    }

    @GetMapping("/users/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<ReviewUserDTO>> getAllUsersForReviewManagement() {
        return ResponseEntity.ok(reviewService.getAllUsersForReviewManagement(mainSiteId()));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ReviewUserDTO> getUserForReviewTracking(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewService.getUserForReviewTracking(mainSiteId(), id));
    }

    @PutMapping("/users/{id}/tracking")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<ReviewUserDTO> updateUserReviewTracking(
            @PathVariable UUID id,
            @Valid @RequestBody UserReviewTrackingRequestDTO request) {
        return ResponseEntity.ok(reviewService.updateUserReviewTracking(
                mainSiteId(), id, request.getCanonicalName(), request.getNameVariants(), request.getIsReviewTracked()));
    }

    @GetMapping("/summaries")
    public ResponseEntity<List<ReviewSummaryResponseDTO>> getMonthlySummaries(
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(reviewService.getMonthlySummaries(mainSiteId(), year, month));
    }

    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<Page<ReviewResponseDTO>> getUserReviews(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reviewService.getUserReviews(
                mainSiteId(), userId, fromDate, toDate, PageRequest.of(page, PageLimits.clampSize(size))));
    }

    @GetMapping("/users/{userId}/stats")
    public ResponseEntity<UserReviewStatsResponseDTO> getUserReviewStats(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(reviewService.getUserReviewStats(mainSiteId(), userId, year, month));
    }
}
