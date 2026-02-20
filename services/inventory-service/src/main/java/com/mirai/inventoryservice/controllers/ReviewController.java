package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.requests.ReviewEmployeeRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ReviewEmployeeResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ReviewResponseDTO;
import com.mirai.inventoryservice.dtos.responses.ReviewSummaryResponseDTO;
import com.mirai.inventoryservice.services.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
    // Employee endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/employees")
    public ResponseEntity<List<ReviewEmployeeResponseDTO>> getAllEmployees() {
        return ResponseEntity.ok(reviewService.getAllEmployees());
    }

    @GetMapping("/employees/{id}")
    public ResponseEntity<ReviewEmployeeResponseDTO> getEmployeeById(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewService.getEmployeeById(id));
    }

    @PostMapping("/employees")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewEmployeeResponseDTO> createEmployee(
            @Valid @RequestBody ReviewEmployeeRequestDTO request) {
        ReviewEmployeeResponseDTO employee = reviewService.createEmployee(
                request.getCanonicalName(),
                request.getNameVariants());
        return ResponseEntity.status(HttpStatus.CREATED).body(employee);
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewEmployeeResponseDTO> updateEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewEmployeeRequestDTO request) {
        ReviewEmployeeResponseDTO employee = reviewService.updateEmployee(
                id,
                request.getCanonicalName(),
                request.getNameVariants(),
                request.getIsActive());
        return ResponseEntity.ok(employee);
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

    @GetMapping("/employees/{employeeId}/reviews")
    public ResponseEntity<Page<ReviewResponseDTO>> getEmployeeReviews(
            @PathVariable UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(reviewService.getEmployeeReviews(employeeId, fromDate, toDate, pageable));
    }
}
