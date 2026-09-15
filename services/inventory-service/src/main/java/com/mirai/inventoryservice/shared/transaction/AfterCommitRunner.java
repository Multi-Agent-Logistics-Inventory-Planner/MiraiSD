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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
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
