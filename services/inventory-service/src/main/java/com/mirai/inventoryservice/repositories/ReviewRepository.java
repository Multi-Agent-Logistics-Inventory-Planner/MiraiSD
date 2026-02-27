package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.review.Review;
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

    List<Review> findByReviewDateOrderByCreatedAtDesc(LocalDate date);

    @Query("SELECT r FROM Review r WHERE r.user.id = :userId ORDER BY r.reviewDate DESC")
    Page<Review> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.user.id = :userId " +
            "AND r.reviewDate >= :startDate AND r.reviewDate <= :endDate " +
            "ORDER BY r.reviewDate DESC")
    Page<Review> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);
}
