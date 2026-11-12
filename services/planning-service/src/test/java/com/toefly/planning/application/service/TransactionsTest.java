package com.toefly.planning.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.planning.application.port.out.ConcurrencyConflictException;
import com.toefly.planning.application.port.out.DependencyUnavailableException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@DisplayName("commands repeat on a concurrent write and report an unavailable dependency")
class TransactionsTest {

    private final Transactions transactions = new Transactions(new NoOpTransactions());

    @Test
    @DisplayName("a server-side command repeats after a concurrent write and succeeds on fresh state")
    void retriesOnConflict() {
        AtomicInteger calls = new AtomicInteger();

        String result = transactions.retryOnConflict(() -> {
            if (calls.incrementAndGet() < Transactions.ATTEMPTS) {
                throw new ConcurrencyConflictException("week changed");
            }
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(calls).hasValue(Transactions.ATTEMPTS);
    }

    @Test
    @DisplayName("after three conflicts the command gives up with the conflict")
    void givesUpAfterThreeConflicts() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> transactions.retryOnConflict(() -> {
                    calls.incrementAndGet();
                    throw new ConcurrencyConflictException("week changed");
                }))
                .isInstanceOf(ConcurrencyConflictException.class)
                .hasMessage("week changed");
        assertThat(calls).hasValue(Transactions.ATTEMPTS);
    }

    @Test
    @DisplayName("a command with If-Match fails at once so the client reloads")
    void onceDoesNotRetry() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> transactions.once(() -> {
                    calls.incrementAndGet();
                    throw new ConcurrencyConflictException("stale");
                }))
                .isInstanceOf(ConcurrencyConflictException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("an unavailable dependency names itself in the problem")
    void dependencyUnavailable() {
        var failure = new IllegalStateException("connection refused");

        var exception = new DependencyUnavailableException("content-service", failure);

        assertThat(exception).hasMessageContaining("content-service").hasCause(failure);
        assertThat(exception.problemProperties()).containsEntry("dependency", "content-service");
    }

    private static final class NoOpTransactions extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
