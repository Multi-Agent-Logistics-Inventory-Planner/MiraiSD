package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findBySiteIdOrderByCreatedAtDesc(UUID siteId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.siteId = :siteId AND n.resolvedAt IS NULL ORDER BY n.createdAt DESC")
    Page<Notification> findUnresolvedBySiteIdOrderByCreatedAtDesc(@Param("siteId") UUID siteId, Pageable pageable);
}
