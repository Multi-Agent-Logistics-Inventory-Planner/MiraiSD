package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.review.Review;
import com.mirai.inventoryservice.models.review.ReviewEmployee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);

    Page<Review> findByEmployeeOrderByReviewDateDesc(ReviewEmployee employee, Pageable pageable);

    Page<Review> findByEmployeeAndReviewDateBetweenOrderByReviewDateDesc(
            ReviewEmployee employee,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable);

    List<Review> findByReviewDateOrderByCreatedAtDesc(LocalDate date);

    @Query("SELECT r FROM Review r WHERE r.employee.id = :employeeId ORDER BY r.reviewDate DESC")
    Page<Review> findByEmployeeId(@Param("employeeId") UUID employeeId, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.employee.id = :employeeId " +
            "AND r.reviewDate >= :startDate AND r.reviewDate <= :endDate " +
            "ORDER BY r.reviewDate DESC")
    Page<Review> findByEmployeeIdAndDateRange(
            @Param("employeeId") UUID employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("SELECT MAX(r.reviewDate) FROM Review r WHERE r.employee.id = :employeeId")
    Optional<LocalDate> findLastReviewDateByEmployeeId(@Param("employeeId") UUID employeeId);
}
