package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.review.ReviewDailyCount;
import com.mirai.inventoryservice.models.review.ReviewEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewDailyCountRepository extends JpaRepository<ReviewDailyCount, UUID> {

    List<ReviewDailyCount> findByDateOrderByReviewCountDesc(LocalDate date);

    // Legacy methods using employee relationship
    List<ReviewDailyCount> findByEmployeeAndDateBetweenOrderByDateAsc(
            ReviewEmployee employee,
            LocalDate startDate,
            LocalDate endDate);

    @Query("SELECT c.employee.canonicalName, SUM(c.reviewCount) " +
            "FROM ReviewDailyCount c " +
            "WHERE c.date >= :startDate AND c.date <= :endDate " +
            "GROUP BY c.employee.canonicalName " +
            "ORDER BY SUM(c.reviewCount) DESC")
    List<Object[]> getMonthlyTotals(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT c.employee.id, c.employee.canonicalName, SUM(c.reviewCount), AVG(c.reviewCount) " +
            "FROM ReviewDailyCount c " +
            "WHERE c.date >= :startDate AND c.date <= :endDate " +
            "GROUP BY c.employee.id, c.employee.canonicalName " +
            "ORDER BY SUM(c.reviewCount) DESC")
    List<Object[]> getMonthlySummaries(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // New methods using user relationship
    List<ReviewDailyCount> findByUserAndDateBetweenOrderByDateAsc(
            User user,
            LocalDate startDate,
            LocalDate endDate);

    @Query("SELECT c.user.id, c.user.fullName, SUM(c.reviewCount), AVG(c.reviewCount) " +
            "FROM ReviewDailyCount c " +
            "WHERE c.user IS NOT NULL AND c.date >= :startDate AND c.date <= :endDate " +
            "GROUP BY c.user.id, c.user.fullName " +
            "ORDER BY SUM(c.reviewCount) DESC")
    List<Object[]> getMonthlySummariesByUser(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // All-time stats for a specific user: [totalReviews, firstDate, lastDate]
    @Query("SELECT SUM(c.reviewCount), MIN(c.date), MAX(c.date) " +
            "FROM ReviewDailyCount c " +
            "WHERE c.user.id = :userId")
    List<Object[]> getAllTimeStatsByUser(@Param("userId") UUID userId);

    // All-time totals by user for ranking: [userId, totalReviews]
    @Query("SELECT c.user.id, SUM(c.reviewCount) " +
            "FROM ReviewDailyCount c " +
            "WHERE c.user IS NOT NULL " +
            "GROUP BY c.user.id " +
            "ORDER BY SUM(c.reviewCount) DESC")
    List<Object[]> getAllTimeTotalsByUser();
}
