package com.mirai.inventoryservice.shipments.infrastructure;

import com.mirai.inventoryservice.shipments.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    boolean existsByEventIdAndSource(String eventId, String source);
}
