package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    boolean existsByEventIdAndSource(String eventId, String source);
}
