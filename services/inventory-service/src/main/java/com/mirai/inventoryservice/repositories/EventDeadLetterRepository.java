package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.EventDeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventDeadLetterRepository extends JpaRepository<EventDeadLetter, UUID> {

    /**
     * Find dead letter events by topic for debugging/monitoring.
     */
    List<EventDeadLetter> findByTopic(String topic);
}
