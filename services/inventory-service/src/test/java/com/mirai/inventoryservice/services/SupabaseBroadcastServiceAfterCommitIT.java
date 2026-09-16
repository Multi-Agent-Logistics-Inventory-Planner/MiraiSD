package com.mirai.inventoryservice.services;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * .specs/phase-6-inventory 6e independent review, R-4: a permanent, real-Postgres proof of two
 * things the review verified only by hand with a throwaway test context.
 * <p>
 * Observes the actual network-dispatch attempt via its own WARN log line ("Failed to send
 * broadcast", emitted by {@link SupabaseBroadcastService#broadcast}) captured on a
 * {@link ListAppender}, rather than spying on {@code dispatchInventoryUpdated} directly --
 * that method is deliberately package-private, and a Mockito spy wrapping the class's existing
 * Spring AOP (@Async) CGLIB proxy proved too fragile to stub reliably for a package-private
 * target in practice. The log line is only ever emitted from inside the real, dispatched call
 * (no test infrastructure emits it), so its presence/absence and its thread name are an
 * equally direct, more robust proof of the same two things: whether dispatch happened at all,
 * and whether it happened off the calling thread.
 */
class SupabaseBroadcastServiceAfterCommitIT extends BaseKafkaIntegrationTest {

    @Autowired private SupabaseBroadcastService broadcastService;
    @Autowired private PlatformTransactionManager transactionManager;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(SupabaseBroadcastService.class)).addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        ((Logger) LoggerFactory.getLogger(SupabaseBroadcastService.class)).detachAppender(appender);
    }

    @Test
    void forcedRollback_neverReachesTheNetworkDispatch() throws InterruptedException {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            broadcastService.broadcastInventoryUpdated(UUID.randomUUID(), "BOX_BINS", List.of("p1"), null);
            status.setRollbackOnly();
            return null;
        });

        // Give the (correctly never-scheduled) async task a real window to have fired if the
        // after-commit deferral were broken, rather than asserting immediately.
        Thread.sleep(1500);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void committedTransaction_dispatchesExactlyOnce_offTheCallingThread() throws InterruptedException {
        Thread callingThread = Thread.currentThread();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            broadcastService.broadcastInventoryUpdated(UUID.randomUUID(), "BOX_BINS", List.of("p1"), null);
            return null;
        });

        long deadline = System.currentTimeMillis() + 5000;
        while (appender.list.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).contains("Failed to send broadcast");
        assertThat(event.getThreadName())
                .as("dispatch must still go through the @Lazy self-proxy's @Async advice, not run"
                        + " synchronously on the caller's thread")
                .isNotEqualTo(callingThread.getName());
    }
}
