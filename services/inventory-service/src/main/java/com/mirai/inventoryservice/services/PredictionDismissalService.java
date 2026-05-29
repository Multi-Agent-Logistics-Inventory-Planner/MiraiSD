package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.PredictionDismissalRequest;
import com.mirai.inventoryservice.dtos.responses.PredictionDismissalDTO;
import com.mirai.inventoryservice.models.PredictionDismissal;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.repositories.PredictionDismissalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Org-wide forecast dismissals. Backs the predictions tab "Resolved" state so
 * dismissals follow the user across browsers/sessions instead of living in
 * each browser's localStorage.
 */
@Service
@RequiredArgsConstructor
public class PredictionDismissalService {

    private final PredictionDismissalRepository repository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<PredictionDismissalDTO> listActive() {
        return repository
                .findActiveSince(PredictionDismissalRepository.defaultCutoff())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public PredictionDismissalDTO upsert(PredictionDismissalRequest request, Authentication authentication) {
        User user = currentUser(authentication);
        PredictionDismissal entity = repository.findById(request.itemId())
                .orElseGet(() -> PredictionDismissal.builder().itemId(request.itemId()).build());
        entity.setDismissedAt(OffsetDateTime.now());
        entity.setDismissedBy(user.getId());
        entity.setComputedAt(request.computedAt());
        entity.setReason(request.reason());
        return toDTO(repository.save(entity));
    }

    @Transactional
    public void delete(UUID itemId) {
        repository.deleteByItemId(itemId);
    }

    private User currentUser(Authentication authentication) {
        @SuppressWarnings("unchecked")
        Map<String, String> principal = (Map<String, String>) authentication.getPrincipal();
        String email = principal.get("email");
        if (email == null) {
            throw new IllegalStateException("Authenticated request has no email claim");
        }
        return userService.getUserByEmail(email);
    }

    private PredictionDismissalDTO toDTO(PredictionDismissal entity) {
        return new PredictionDismissalDTO(
                entity.getItemId(),
                entity.getDismissedAt(),
                entity.getDismissedBy(),
                entity.getComputedAt(),
                entity.getReason()
        );
    }
}
