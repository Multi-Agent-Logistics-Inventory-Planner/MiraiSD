package com.mirai.inventoryservice.shared.idempotency;

import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * .specs/phase-6-inventory T-6c-10 (Q-6c-3): real-Postgres proof that
 * {@link CommandIdempotencyService#executeIdempotent} commits the command's effect and the idempotency
 * record atomically, and that at most one committed effect survives a genuine concurrent replay.
 */
class CommandIdempotencyServiceIT extends BaseKafkaIntegrationTest {

    record SampleBody(String value) {
    }

    @Autowired
    private CommandIdempotencyService idempotencyService;

    @Autowired
    private CommandIdempotencyRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID siteId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void createEffectsTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS idempotency_test_effects (marker UUID)");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM idempotency_test_effects");
        repository.deleteAll();
    }

    @Test
    @DisplayName("concurrent duplicate submissions: at most one committed effect, exactly one stored record")
    void concurrentDuplicateSubmissions_produceAtMostOneCommittedEffect() throws Exception {
        String key = "concurrent-key-" + UUID.randomUUID();
        AtomicInteger commandInvocations = new AtomicInteger();

        java.util.function.Supplier<CommandIdempotencyService.CommandResult<SampleBody>> command = () -> {
            commandInvocations.incrementAndGet();
            // Participates in this call's ambient @Transactional (same bound connection), so a
            // losing transaction's rollback removes this row too - proving one committed effect,
            // not just one surviving idempotency record.
            jdbcTemplate.update("INSERT INTO idempotency_test_effects (marker) VALUES (?)", UUID.randomUUID());
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new CommandIdempotencyService.CommandResult<>(201, new SampleBody("race-result"));
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CommandIdempotencyService.CommandResult<SampleBody>> futureA = executor.submit(() ->
                    idempotencyService.executeIdempotent(siteId, userId, key, "test-command", "fp", SampleBody.class, command));
            Future<CommandIdempotencyService.CommandResult<SampleBody>> futureB = executor.submit(() ->
                    idempotencyService.executeIdempotent(siteId, userId, key, "test-command", "fp", SampleBody.class, command));

            int successes = 0;
            int failures = 0;
            for (Future<CommandIdempotencyService.CommandResult<SampleBody>> future : List.of(futureA, futureB)) {
                try {
                    future.get(15, TimeUnit.SECONDS);
                    successes++;
                } catch (Exception e) {
                    failures++;
                }
            }

            assertThat(commandInvocations.get())
                    .as("both threads must have raced past the initial read before either committed")
                    .isEqualTo(2);
            assertThat(successes + failures).isEqualTo(2);
            assertThat(successes)
                    .as("exactly one of the two genuinely concurrent submissions wins")
                    .isEqualTo(1);

            Integer effectCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM idempotency_test_effects", Integer.class);
            assertThat(effectCount)
                    .as("the loser's business effect must roll back with its failed idempotency insert")
                    .isEqualTo(1);

            List<CommandIdempotency> stored = repository.findAll().stream()
                    .filter(r -> r.getIdempotencyKey().equals(key))
                    .toList();
            assertThat(stored).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("rollback-then-retry: a failed command leaves no idempotency row, and the same key can be retried")
    void rollbackThenRetry_leavesNoRowAndAllowsRetryWithSameKey() {
        String key = "rollback-key-" + UUID.randomUUID();

        java.util.function.Supplier<CommandIdempotencyService.CommandResult<SampleBody>> failingCommand = () -> {
            throw new RuntimeException("simulated business failure");
        };

        assertThatThrownBy(() -> idempotencyService.executeIdempotent(
                siteId, userId, key, "test-command", "fp", SampleBody.class, failingCommand))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated business failure");

        assertThat(repository.findBySiteIdAndUserIdAndIdempotencyKey(siteId, userId, key)).isEmpty();

        java.util.function.Supplier<CommandIdempotencyService.CommandResult<SampleBody>> succeedingCommand =
                () -> new CommandIdempotencyService.CommandResult<>(201, new SampleBody("retried-ok"));

        CommandIdempotencyService.CommandResult<SampleBody> result = idempotencyService.executeIdempotent(
                siteId, userId, key, "test-command", "fp", SampleBody.class, succeedingCommand);

        assertThat(result.body()).isEqualTo(new SampleBody("retried-ok"));
        assertThat(repository.findBySiteIdAndUserIdAndIdempotencyKey(siteId, userId, key)).isPresent();
    }

    @Test
    @DisplayName("restart-then-replay: a record persisted by an earlier call replays without re-invoking the command")
    void restartThenReplay_returnsStoredResultAcrossSeparateCalls() {
        String key = "restart-key-" + UUID.randomUUID();
        AtomicInteger invocations = new AtomicInteger();
        java.util.function.Supplier<CommandIdempotencyService.CommandResult<SampleBody>> command = () -> {
            invocations.incrementAndGet();
            return new CommandIdempotencyService.CommandResult<>(201, new SampleBody("first-attempt"));
        };

        CommandIdempotencyService.CommandResult<SampleBody> first = idempotencyService.executeIdempotent(
                siteId, userId, key, "test-command", "fp", SampleBody.class, command);

        // A second, independent call (simulating a process restart, or a retried HTTP request
        // hitting a different pooled connection) with the same key/fingerprint.
        CommandIdempotencyService.CommandResult<SampleBody> replay = idempotencyService.executeIdempotent(
                siteId, userId, key, "test-command", "fp", SampleBody.class, command);

        assertThat(invocations.get()).isEqualTo(1);
        assertThat(replay.body()).isEqualTo(first.body());
    }
}
