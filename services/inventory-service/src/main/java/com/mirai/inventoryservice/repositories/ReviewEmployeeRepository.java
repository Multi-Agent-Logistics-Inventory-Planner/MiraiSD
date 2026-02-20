package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.review.ReviewEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewEmployeeRepository extends JpaRepository<ReviewEmployee, UUID> {

    List<ReviewEmployee> findByIsActiveTrueOrderByCanonicalNameAsc();

    Optional<ReviewEmployee> findByCanonicalName(String canonicalName);

    boolean existsByCanonicalName(String canonicalName);
}
