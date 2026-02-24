package com.mirai.inventoryservice.models.review;

import com.mirai.inventoryservice.models.audit.User;
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
@Table(name = "review_daily_counts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDailyCount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Legacy relationship - kept for backward compatibility during migration
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private ReviewEmployee employee;

    // New relationship - daily counts linked to users
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

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
