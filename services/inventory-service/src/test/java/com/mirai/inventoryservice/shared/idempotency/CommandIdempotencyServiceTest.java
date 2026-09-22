package com.mirai.inventoryservice.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommandIdempotencyService")
class CommandIdempotencyServiceTest {

    record SampleBody(String value) {
    }

    @Mock
    private CommandIdempotencyRepository repository;

    private CommandIdempotencyService service;

    private final UUID siteId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CommandIdempotencyService(repository, new ObjectMapper());
    }

    @Test
    @DisplayName("first call with a new key invokes the command and stores its result")
    void executeIdempotent_newKey_invokesCommandAndStores() {
        when(repository.findBySiteIdAndUserIdAndIdempotencyKey(siteId, userId, "key-1"))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        Supplier<CommandIdempotencyService.CommandResult<SampleBody>> command = mock(Supplier.class);
        when(command.get()).thenReturn(new CommandIdempotencyService.CommandResult<>(201, new SampleBody("created")));

        CommandIdempotencyService.CommandResult<SampleBody> result = service.executeIdempotent(
                siteId, userId, "key-1", "batch-adjust", "fp-1", SampleBody.class, command);

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body()).isEqualTo(new SampleBody("created"));
        verify(command, times(1)).get();

        ArgumentCaptor<CommandIdempotency> captor = ArgumentCaptor.forClass(CommandIdempotency.class);
        verify(repository).saveAndFlush(captor.capture());
        CommandIdempotency saved = captor.getValue();
        assertThat(saved.getSiteId()).isEqualTo(siteId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(saved.getCommandType()).isEqualTo("batch-adjust");
        assertThat(saved.getRequestFingerprint()).isEqualTo("fp-1");
        assertThat(saved.getResultStatus()).isEqualTo(201);
        assertThat(saved.getResultBody()).contains("created");
    }

    @Test
    @DisplayName("replay with the same fingerprint returns the stored result without re-invoking the command")
    void executeIdempotent_replaySameFingerprint_returnsStoredResult_doesNotReinvoke() {
        CommandIdempotency existing = CommandIdempotency.builder()
                .id(UUID.randomUUID())
                .siteId(siteId)
                .userId(userId)
                .idempotencyKey("key-2")
                .commandType("batch-adjust")
                .requestFingerprint("fp-2")
                .resultStatus(201)
                .resultBody("{\"value\":\"first-result\"}")
                .createdAt(OffsetDateTime.now())
                .build();
        when(repository.findBySiteIdAndUserIdAndIdempotencyKey(siteId, userId, "key-2"))
                .thenReturn(Optional.of(existing));

        @SuppressWarnings("unchecked")
        Supplier<CommandIdempotencyService.CommandResult<SampleBody>> command = mock(Supplier.class);

        CommandIdempotencyService.CommandResult<SampleBody> result = service.executeIdempotent(
                siteId, userId, "key-2", "batch-adjust", "fp-2", SampleBody.class, command);

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body()).isEqualTo(new SampleBody("first-result"));
        verifyNoInteractions(command);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("replay with a different fingerprint throws IdempotencyConflictException (409) and does not invoke the command")
    void executeIdempotent_replayDifferentFingerprint_throwsConflict() {
        CommandIdempotency existing = CommandIdempotency.builder()
                .id(UUID.randomUUID())
                .siteId(siteId)
                .userId(userId)
                .idempotencyKey("key-3")
                .commandType("batch-adjust")
                .requestFingerprint("fp-original")
                .resultStatus(201)
                .resultBody("{\"value\":\"first-result\"}")
                .createdAt(OffsetDateTime.now())
                .build();
        when(repository.findBySiteIdAndUserIdAndIdempotencyKey(siteId, userId, "key-3"))
                .thenReturn(Optional.of(existing));

        @SuppressWarnings("unchecked")
        Supplier<CommandIdempotencyService.CommandResult<SampleBody>> command = mock(Supplier.class);

        assertThatThrownBy(() -> service.executeIdempotent(
                siteId, userId, "key-3", "batch-adjust", "fp-different", SampleBody.class, command))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("key-3");
        verifyNoInteractions(command);
    }

    @Test
    @DisplayName("replay with the same fingerprint but a different commandType throws IdempotencyConflictException and does not invoke the command")
    void executeIdempotent_replaySameFingerprintDifferentCommandType_throwsConflict() {
        CommandIdempotency existing = CommandIdempotency.builder()
                .id(UUID.randomUUID())
                .siteId(siteId)
                .userId(userId)
                .idempotencyKey("key-6")
                .commandType("batch-adjust")
                .requestFingerprint("fp-shared")
                .resultStatus(201)
                .resultBody("{\"value\":\"first-result\"}")
                .createdAt(OffsetDateTime.now())
                .build();
        when(repository.findBySiteIdAndUserIdAndIdempotencyKey(siteId, userId, "key-6"))
                .thenReturn(Optional.of(existing));

        @SuppressWarnings("unchecked")
        Supplier<CommandIdempotencyService.CommandResult<SampleBody>> command = mock(Supplier.class);

        assertThatThrownBy(() -> service.executeIdempotent(
                siteId, userId, "key-6", "batch-transfer", "fp-shared", SampleBody.class, command))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("key-6");
        verifyNoInteractions(command);
    }

    @Test
    @DisplayName("same key at a different site is not treated as a replay")
    void executeIdempotent_sameKeyDifferentSite_isNotAReplay() {
        UUID otherSite = UUID.randomUUID();
        when(repository.findBySiteIdAndUserIdAndIdempotencyKey(otherSite, userId, "key-4"))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        Supplier<CommandIdempotencyService.CommandResult<SampleBody>> command = mock(Supplier.class);
        when(command.get()).thenReturn(new CommandIdempotencyService.CommandResult<>(201, new SampleBody("site-b-result")));

        CommandIdempotencyService.CommandResult<SampleBody> result = service.executeIdempotent(
                otherSite, userId, "key-4", "batch-adjust", "fp-4", SampleBody.class, command);

        assertThat(result.body()).isEqualTo(new SampleBody("site-b-result"));
        verify(command, times(1)).get();
    }

    @Test
    @DisplayName("same key for a different user is not treated as a replay")
    void executeIdempotent_sameKeyDifferentUser_isNotAReplay() {
        UUID otherUser = UUID.randomUUID();
        when(repository.findBySiteIdAndUserIdAndIdempotencyKey(siteId, otherUser, "key-5"))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        Supplier<CommandIdempotencyService.CommandResult<SampleBody>> command = mock(Supplier.class);
        when(command.get()).thenReturn(new CommandIdempotencyService.CommandResult<>(201, new SampleBody("user-b-result")));

        CommandIdempotencyService.CommandResult<SampleBody> result = service.executeIdempotent(
                siteId, otherUser, "key-5", "batch-adjust", "fp-5", SampleBody.class, command);

        assertThat(result.body()).isEqualTo(new SampleBody("user-b-result"));
        verify(command, times(1)).get();
    }

    @Test
    @DisplayName("cleanupExpiredRecords deletes rows older than the retention window")
    void cleanupExpiredRecords_deletesRowsOlderThanRetentionWindow() {
        when(repository.deleteByCreatedAtBefore(any())).thenReturn(3);

        service.cleanupExpiredRecords();

        ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteByCreatedAtBefore(cutoffCaptor.capture());
        OffsetDateTime expectedCutoff = OffsetDateTime.now().minusDays(CommandIdempotencyService.RETENTION_DAYS);
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff, within(5, java.time.temporal.ChronoUnit.SECONDS));
    }
}
