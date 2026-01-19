package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.EventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutbox, UUID> {
    // Find unpublished events (top 100)
    List<EventOutbox> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
    
    // Find unpublished events
    List<EventOutbox> findByPublishedAtIsNullOrderByCreatedAtAsc();
    
    // Find events that failed to publish (with retry limit check)
    List<EventOutbox> findByPublishedAtIsNullAndPublishAttemptsLessThanOrderByCreatedAtAsc(int maxAttempts);
    
    // Find published events after a certain timestamp
    List<EventOutbox> findByPublishedAtAfter(OffsetDateTime timestamp);
}

