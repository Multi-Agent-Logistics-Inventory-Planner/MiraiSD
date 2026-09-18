package com.mirai.inventoryservice.shared.transaction;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers {@code action} to run after the enclosing transaction commits, if one is active;
 * otherwise runs it immediately. Used by best-effort, non-durable side effects (e.g. realtime
 * broadcast notifications) that must never fire for a rolled-back mutation and must always
 * observe durable state when they do fire (.specs/phase-6-inventory 6e, T-6e-be-6).
 *
 * <p>Lives in {@code shared} (not the caller's own package) so its anonymous
 * {@link TransactionSynchronization} implementation doesn't count as a new class introduced
 * into a legacy technical-layer package under {@code ArchitectureTest}'s frozen violation store.
 */
public final class AfterCommitRunner {

    private AfterCommitRunner() {
    }

    public static void run(Runnable action) {
        // .specs/phase-6-inventory 6e independent review, A-10: isSynchronizationActive() alone
        // is true even for a non-transactional TransactionTemplate/read-only synchronization
        // context (synchronization can be active without an actual, commit-capable transaction
        // underneath it -- see Spring's TransactionSynchronizationManager javadoc). Guarding on
        // isActualTransactionActive() too ensures a synchronization is only ever registered when
        // there is a real transaction to defer to; otherwise dispatch runs immediately, matching
        // the "no active transaction" behavior this class already had.
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
