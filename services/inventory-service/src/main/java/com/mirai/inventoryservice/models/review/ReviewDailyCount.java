package com.mirai.inventoryservice.models.review;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "review_daily_counts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDailyCount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private ReviewEmployee employee;

    @NotNull
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "review_count")
    @Builder.Default
    private Integer reviewCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
