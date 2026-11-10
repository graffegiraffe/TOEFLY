package com.toefly.planning.application.service;

import com.toefly.planning.application.port.out.ConcurrencyConflictException;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a command in its own transaction. Server-side commands repeat up to three times when another writer
 * changed the aggregate first; commands with If-Match fail at once so the client reloads.
 */
@Component
class Transactions {

    static final int ATTEMPTS = 3;

    private final TransactionTemplate template;

    Transactions(PlatformTransactionManager transactions) {
        this.template = new TransactionTemplate(transactions);
    }

    <T> T once(Supplier<T> work) {
        return Objects.requireNonNull(template.execute(status -> work.get()));
    }

    <T> T retryOnConflict(Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return once(work);
            } catch (ConcurrencyConflictException conflict) {
                if (attempt >= ATTEMPTS) {
                    throw conflict;
                }
            }
        }
    }
}
