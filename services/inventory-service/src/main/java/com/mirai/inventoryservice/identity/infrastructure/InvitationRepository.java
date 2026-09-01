package com.mirai.inventoryservice.identity.infrastructure;

import com.mirai.inventoryservice.identity.domain.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findByEmail(String email);

    List<Invitation> findByAcceptedAtIsNull();

    boolean existsByEmail(String email);

    void deleteByEmail(String email);
}
