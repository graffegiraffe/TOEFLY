package com.toefly.billing.domain.saga;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record SagaStep(
        PaymentSagaStep step,
        StepStatus status,
        int attempts,
        @Nullable Instant nextAttemptAt,
        @Nullable String lastError) {

    public SagaStep {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(status, "status");
    }

    public static SagaStep pending(PaymentSagaStep step) {
        return new SagaStep(step, StepStatus.PENDING, 0, null, null);
    }

    public static SagaStep skipped(PaymentSagaStep step) {
        return new SagaStep(step, StepStatus.SKIPPED, 0, null, null);
    }

    SagaStep with(StepStatus next, int tries, @Nullable Instant nextAttempt, @Nullable String error) {
        return new SagaStep(step, next, tries, nextAttempt, error);
    }
}
