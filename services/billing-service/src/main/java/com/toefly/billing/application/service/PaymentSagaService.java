package com.toefly.billing.application.service;

import com.toefly.billing.application.port.in.BillingException;
import com.toefly.billing.application.port.in.PaymentSagaUseCase;
import com.toefly.billing.application.port.out.CheckoutSessionRepository;
import com.toefly.billing.application.port.out.SagaRepository;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.saga.PaymentSaga;
import com.toefly.billing.domain.saga.PaymentSagaData;
import com.toefly.billing.domain.saga.PaymentSagaStep;
import com.toefly.billing.domain.saga.SagaStatus;
import com.toefly.billing.domain.saga.SagaStep;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrator of the payment saga (ADR-0006): state in {@code saga_instance}/{@code saga_step}, one executor per saga
 * through {@code FOR UPDATE SKIP LOCKED}. A step runs in one transaction with the saga state; a failure is recorded in
 * a new transaction and retried with backoff, then compensated.
 */
@Service
public class PaymentSagaService implements PaymentSagaUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaService.class);
    private static final int MAX_STEPS_PER_RUN = 200;

    private final SagaRepository sagas;
    private final CheckoutSessionRepository sessions;
    private final PaymentSagaSteps steps;
    private final BillingPolicy policy;
    private final TransactionTemplate transactions;
    private final MeterRegistry meters;
    private final Clock clock;

    public PaymentSagaService(
            SagaRepository sagas,
            CheckoutSessionRepository sessions,
            PaymentSagaSteps steps,
            BillingPolicy policy,
            PlatformTransactionManager transactionManager,
            MeterRegistry meters,
            Clock clock) {
        this.sagas = sagas;
        this.sessions = sessions;
        this.steps = steps;
        this.policy = policy;
        this.transactions = new TransactionTemplate(transactionManager);
        this.meters = meters;
        this.clock = clock;
    }

    /** One saga per confirmed provider payment; a repeated start returns the existing saga. Runs in the caller's tx. */
    public PaymentSaga start(UUID userId, PlanTierCode tier, PaymentProviderCode provider, PaymentSagaData data) {
        UUID correlation = correlation(provider, data.providerPaymentId());
        Optional<PaymentSaga> existing = sagas.findByCorrelation(correlation);
        if (existing.isPresent()) {
            return existing.get();
        }
        PaymentSaga saga =
                PaymentSaga.start(UUID.randomUUID(), correlation, userId, tier, provider, data, clock.instant());
        if (!sagas.insertIfAbsent(saga)) {
            return sagas.findByCorrelation(correlation).orElseThrow();
        }
        if (data.checkoutSessionId() != null) {
            sessions.findById(data.checkoutSessionId())
                    .ifPresent(session -> sessions.update(session.processing(saga.id())));
        }
        meters.counter("toefly.billing.saga.started", "tier", tier.name()).increment();
        return saga;
    }

    public static UUID correlation(PaymentProviderCode provider, String providerPaymentId) {
        return UUID.nameUUIDFromBytes((provider + ":" + providerPaymentId).getBytes(StandardCharsets.UTF_8));
    }

    /** Runs the steps of one saga that are due now (after a webhook, so rights appear without waiting a poll). */
    public void drive(UUID sagaId) {
        for (int i = 0; i < 20; i++) {
            if (!runStep(sagaId)) {
                return;
            }
        }
    }

    @Override
    public int runDue() {
        int ran = 0;
        while (ran < MAX_STEPS_PER_RUN) {
            Optional<UUID> due = transactions.execute(
                    status -> sagas.lockDue(clock.instant()).map(PaymentSaga::id));
            if (due == null || due.isEmpty() || !runStep(due.get())) {
                return ran;
            }
            ran++;
        }
        return ran;
    }

    /** Runs the next step of the saga if it is due; returns whether a step ran. */
    boolean runStep(UUID sagaId) {
        StepRef ref;
        try {
            ref = transactions.execute(status -> {
                Optional<PaymentSaga> locked = sagas.lock(sagaId);
                if (locked.isEmpty() || !locked.get().dueAt(clock.instant())) {
                    return null;
                }
                PaymentSaga saga = locked.get();
                Optional<SagaStep> next = saga.next();
                if (next.isEmpty()) {
                    return null;
                }
                PaymentSagaStep step = next.get().step();
                if (saga.status() == SagaStatus.RUNNING) {
                    PaymentSagaData data = steps.run(saga, step);
                    sagas.update(saga.succeeded(step, data, clock.instant()));
                } else {
                    steps.compensate(saga, step);
                    PaymentSaga after = saga.compensated(step, clock.instant());
                    if (after.status() == SagaStatus.FAILED) {
                        steps.failed(after);
                        meters.counter(
                                        "toefly.billing.saga.compensated",
                                        "tier",
                                        saga.tier().name())
                                .increment();
                    }
                    sagas.update(after);
                }
                return new StepRef(step, saga.status());
            });
        } catch (RuntimeException failure) {
            recordFailure(sagaId, failure);
            return true;
        }
        return ref != null;
    }

    private void recordFailure(UUID sagaId, RuntimeException failure) {
        transactions.executeWithoutResult(status -> sagas.lock(sagaId).ifPresent(saga -> {
            Optional<SagaStep> next = saga.next();
            if (next.isEmpty()) {
                return;
            }
            PaymentSagaStep step = next.get().step();
            String error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            Instant now = clock.instant();
            PaymentSaga after = saga.status() == SagaStatus.RUNNING
                    ? saga.failed(step, error, now, policy.sagaRetry())
                    : saga.compensationFailed(step, error, now, policy.sagaRetry());
            if (after.status() == SagaStatus.COMPENSATING && saga.status() == SagaStatus.RUNNING) {
                log.error("ALERT payment saga {} step {} failed for good, compensating: {}", sagaId, step, error);
            } else {
                log.warn(
                        "Payment saga {} step {} failed (attempt {}): {}",
                        sagaId,
                        step,
                        after.step(step).attempts(),
                        error);
            }
            sagas.update(after);
        }));
    }

    @Override
    public PaymentSaga retry(UUID sagaId) {
        PaymentSaga saga = transactions.execute(status -> {
            PaymentSaga locked = sagas.lock(sagaId).orElseThrow(() -> BillingException.notFound("No saga " + sagaId));
            if (!locked.status().active()) {
                throw BillingException.conflict("conflict", "Saga " + sagaId + " is " + locked.status() + ".");
            }
            PaymentSaga now = locked.retryNow(clock.instant());
            sagas.update(now);
            return now;
        });
        drive(sagaId);
        return sagas.findById(sagaId).orElse(saga);
    }

    @Override
    public SagaPage page(
            @Nullable SagaStatus status, @Nullable Instant beforeUpdatedAt, @Nullable UUID beforeId, int limit) {
        SagaRepository.Page page = sagas.page(status, beforeUpdatedAt, beforeId, limit);
        return new SagaPage(page.items(), page.more());
    }

    private record StepRef(PaymentSagaStep step, SagaStatus status) {}
}
