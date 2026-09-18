package com.mirai.inventoryservice.shared.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Durable, site/user-scoped command idempotency (.specs/phase-6-inventory Q-6c-3, T-6c-10). A v1
 * mutation controller (T-6c-12) calls {@link #executeIdempotent} with the trusted
 * {@code AuthorizedSiteContext}'s site/user (never a client-supplied value) and the caller-
 * supplied {@code Idempotency-Key} header. Authorization is rechecked before every call reaches
 * this method by Spring Security's {@code @PreAuthorize} on the controller method itself, so a
 * replayed request is re-authorized exactly like a first attempt - a role/membership change
 * between the original call and a retry is not bypassed, because {@code @PreAuthorize} runs
 * before this method's body regardless of whether the outcome ends up being a stored replay.
 */
@Service
@Slf4j
public class CommandIdempotencyService {

    /**
     * Retention policy (T-6c-10 requires this be explicit): rows older than this are eligible for
     * {@link #cleanupExpiredRecords()}. See V65's migration header for the rationale.
     */
    static final int RETENTION_DAYS = 7;

    private final CommandIdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    public CommandIdempotencyService(CommandIdempotencyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** The outcome of an idempotent command: the HTTP status and body a controller should return. */
    public record CommandResult<T>(int status, T body) {
    }

    /**
     * Runs {@code command} at most once per (site, user, idempotencyKey). A replay with the same
     * {@code requestFingerprint} returns the original stored result without re-invoking
     * {@code command}. A replay with a different fingerprint throws
     * {@link IdempotencyConflictException} (409) rather than either re-running the command or
     * silently returning the old result for a different request.
     * <p>
     * Deliberately does not catch a unique-constraint violation on the final insert (the residual
     * concurrent-race case: two overlapping calls both miss the initial read). Per Q-6c-3, "no
     * idempotency row survives a failed attempt" - letting that exception propagate rolls back
     * this whole {@code @Transactional} method, including whatever {@code command} already did,
     * so the loser's request cleanly fails and the caller's retry (itself idempotent) replays
     * against the winner's now-committed row instead.
     */
    @Transactional
    public <T> CommandResult<T> executeIdempotent(
            UUID siteId,
            UUID userId,
            String idempotencyKey,
            String commandType,
            String requestFingerprint,
            Class<T> resultType,
            Supplier<CommandResult<T>> command) {

        Optional<CommandIdempotency> existing =
                repository.findBySiteIdAndUserIdAndIdempotencyKey(siteId, userId, idempotencyKey);
        if (existing.isPresent()) {
            CommandIdempotency record = existing.get();
            // Compare commandType too, not just the fingerprint: two different command types can
            // legitimately produce the same fingerprint (a hash/serialization of a request body
            // that happens to collide, or a caller that fingerprints only a subset of fields), and
            // replaying the wrong command type's stored result risks deserializing it into a
            // resultType it was never shaped for. A key reused for a different command entirely
            // is exactly the "different request" case this conflict check exists for.
            if (!record.getCommandType().equals(commandType)
                    || !record.getRequestFingerprint().equals(requestFingerprint)) {
                throw new IdempotencyConflictException(
                        "Idempotency-Key '" + idempotencyKey + "' was already used for a different request");
            }
            log.info("Replaying stored result for idempotency key {} (site={}, user={})",
                    idempotencyKey, siteId, userId);
            return new CommandResult<>(record.getResultStatus(), deserialize(record.getResultBody(), resultType));
        }

        CommandResult<T> result = command.get();

        CommandIdempotency record = CommandIdempotency.builder()
                .siteId(siteId)
                .userId(userId)
                .idempotencyKey(idempotencyKey)
                .commandType(commandType)
                .requestFingerprint(requestFingerprint)
                .resultStatus(result.status())
                .resultBody(serialize(result.body()))
                .build();
        repository.saveAndFlush(record);

        return result;
    }

    /** Deletes idempotency records older than {@link #RETENTION_DAYS}. Runs daily at 03:00. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredRecords() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = repository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} expired idempotency records older than {}", deleted, cutoff);
        }
    }

    private <T> String serialize(T body) {
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent command result", e);
        }
    }

    private <T> T deserialize(String json, Class<T> resultType) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, resultType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize stored idempotent command result", e);
        }
    }
}
