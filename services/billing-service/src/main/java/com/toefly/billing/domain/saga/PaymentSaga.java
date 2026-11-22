package com.toefly.billing.domain.saga;

import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The orchestrated payment saga (docs/04 §5.10, ADR-0006): payment confirmed → subscription active → seats → tutor
 * workspace → rights → complete. A step that keeps failing after the retries starts compensations of every step that
 * ran, in reverse order; the saga then ends {@code FAILED}.
 */
public record PaymentSaga(
        UUID id,
        UUID correlationId,
        SagaStatus status,
        int currentStep,
        UUID userId,
        PlanTierCode tier,
        PaymentProviderCode provider,
        PaymentSagaData data,
        Instant nextRunAt,
        List<SagaStep> steps,
        Instant createdAt,
        Instant updatedAt) {

    public static final String TYPE = "PaymentSaga";

    public PaymentSaga {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        steps = steps.stream()
                .sorted(Comparator.comparingInt(step -> step.step().no()))
                .toList();
    }

    public static PaymentSaga start(
            UUID id,
            UUID correlationId,
            UUID userId,
            PlanTierCode tier,
            PaymentProviderCode provider,
            PaymentSagaData data,
            Instant now) {
        List<SagaStep> steps = new ArrayList<>();
        for (PaymentSagaStep step : PaymentSagaStep.values()) {
            steps.add(step.tutorOnly() && !tier.tutor() ? SagaStep.skipped(step) : SagaStep.pending(step));
        }
        return new PaymentSaga(
                id, correlationId, SagaStatus.RUNNING, 1, userId, tier, provider, data, now, steps, now, now);
    }

    public boolean dueAt(Instant now) {
        return status.active() && !nextRunAt.isAfter(now);
    }

    /** The step to run (or to compensate while compensating). */
    public Optional<SagaStep> next() {
        if (status == SagaStatus.RUNNING) {
            return steps.stream()
                    .filter(step -> step.status() == StepStatus.PENDING)
                    .findFirst();
        }
        if (status == SagaStatus.COMPENSATING) {
            return steps.stream()
                    .filter(step -> step.step().compensable())
                    .filter(step -> step.status() == StepStatus.DONE || step.status() == StepStatus.FAILED)
                    .max(Comparator.comparingInt(step -> step.step().no()));
        }
        return Optional.empty();
    }

    public SagaStep step(PaymentSagaStep name) {
        return steps.stream().filter(step -> step.step() == name).findFirst().orElseThrow();
    }

    public PaymentSaga succeeded(PaymentSagaStep name, PaymentSagaData updated, Instant now) {
        requireStatus(SagaStatus.RUNNING);
        SagaStep done = step(name).with(StepStatus.DONE, step(name).attempts() + 1, null, null);
        List<SagaStep> next = replace(done);
        boolean finished = next.stream().noneMatch(step -> step.status() == StepStatus.PENDING);
        int current = next.stream()
                .filter(step -> step.status() == StepStatus.PENDING)
                .mapToInt(step -> step.step().no())
                .findFirst()
                .orElse(name.no());
        return copy(finished ? SagaStatus.COMPLETED : SagaStatus.RUNNING, current, updated, now, next, now);
    }

    /** A failed attempt: retry later, or start compensating once the attempts are used up. */
    public PaymentSaga failed(PaymentSagaStep name, String error, Instant now, RetryPolicy policy) {
        requireStatus(SagaStatus.RUNNING);
        SagaStep current = step(name);
        int attempts = current.attempts() + 1;
        if (attempts < policy.maxAttempts()) {
            Instant retryAt = now.plus(policy.delayAfter(attempts));
            return copy(
                    SagaStatus.RUNNING,
                    name.no(),
                    data,
                    retryAt,
                    replace(current.with(StepStatus.PENDING, attempts, retryAt, trim(error))),
                    now);
        }
        List<SagaStep> next = new ArrayList<>(replace(current.with(StepStatus.FAILED, attempts, null, trim(error))));
        next.replaceAll(
                step -> step.status() == StepStatus.PENDING ? step.with(StepStatus.SKIPPED, 0, null, null) : step);
        return copy(SagaStatus.COMPENSATING, name.no(), data, now, next, now);
    }

    public PaymentSaga compensated(PaymentSagaStep name, Instant now) {
        requireStatus(SagaStatus.COMPENSATING);
        SagaStep current = step(name);
        List<SagaStep> next =
                replace(current.with(StepStatus.COMPENSATED, current.attempts(), null, current.lastError()));
        boolean left = next.stream()
                .anyMatch(step -> step.step().compensable()
                        && (step.status() == StepStatus.DONE || step.status() == StepStatus.FAILED));
        return copy(left ? SagaStatus.COMPENSATING : SagaStatus.FAILED, name.no(), data, now, next, now);
    }

    /** Compensations must finish: they are retried with the backoff without a limit (an alert fires meanwhile). */
    public PaymentSaga compensationFailed(PaymentSagaStep name, String error, Instant now, RetryPolicy policy) {
        requireStatus(SagaStatus.COMPENSATING);
        SagaStep current = step(name);
        Instant retryAt = now.plus(policy.delayAfter(current.attempts() + 1));
        return copy(
                SagaStatus.COMPENSATING,
                name.no(),
                data,
                retryAt,
                replace(current.with(current.status(), current.attempts() + 1, retryAt, trim(error))),
                now);
    }

    /** {@code POST /admin/sagas/{id}/retry}: the waiting step runs now. */
    public PaymentSaga retryNow(Instant now) {
        if (!status.active()) {
            throw new IllegalStateException("Saga " + id + " is " + status + "; nothing to retry.");
        }
        return copy(status, currentStep, data, now, steps, now);
    }

    private List<SagaStep> replace(SagaStep changed) {
        return steps.stream()
                .map(step -> step.step() == changed.step() ? changed : step)
                .toList();
    }

    private PaymentSaga copy(
            SagaStatus next,
            int current,
            PaymentSagaData nextData,
            Instant runAt,
            List<SagaStep> nextSteps,
            Instant now) {
        return new PaymentSaga(
                id, correlationId, next, current, userId, tier, provider, nextData, runAt, nextSteps, createdAt, now);
    }

    private void requireStatus(SagaStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Saga " + id + " is " + status + ", expected " + expected);
        }
    }

    private static @Nullable String trim(@Nullable String error) {
        return error == null || error.length() <= 2000 ? error : error.substring(0, 2000);
    }
}
