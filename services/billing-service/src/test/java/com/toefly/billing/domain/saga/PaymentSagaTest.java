package com.toefly.billing.domain.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentSagaTest {

    static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    static final RetryPolicy POLICY = new RetryPolicy(5, Duration.ofSeconds(1), Duration.ofSeconds(30));

    static PaymentSagaData data() {
        return new PaymentSagaData(
                null,
                "pi_1",
                "sub_1",
                "cus_1",
                1200,
                "EUR",
                NOW,
                NOW,
                NOW.plus(Duration.ofDays(30)),
                null,
                true,
                null,
                null,
                false);
    }

    static PaymentSaga saga(PlanTierCode tier) {
        return PaymentSaga.start(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), tier, PaymentProviderCode.STRIPE, data(), NOW);
    }

    @Test
    @DisplayName("A student saga skips seats and workspace and completes after its steps")
    void studentSagaCompletes() {
        PaymentSaga saga = saga(PlanTierCode.STUDENT_PRO);
        List<PaymentSagaStep> ran = new ArrayList<>();

        while (saga.next().isPresent()) {
            PaymentSagaStep step = saga.next().get().step();
            ran.add(step);
            saga = saga.succeeded(step, saga.data(), NOW);
        }

        assertThat(ran)
                .containsExactly(
                        PaymentSagaStep.RECORD_PAYMENT,
                        PaymentSagaStep.ACTIVATE_SUBSCRIPTION,
                        PaymentSagaStep.GRANT_ENTITLEMENTS,
                        PaymentSagaStep.COMPLETE);
        assertThat(saga.status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.step(PaymentSagaStep.ALLOCATE_SEATS).status()).isEqualTo(StepStatus.SKIPPED);
    }

    @Test
    @DisplayName("AC-27.03.3: a failing step is retried with backoff up to 5 times, then compensated in reverse")
    void retriesThenCompensates() {
        PaymentSaga saga = saga(PlanTierCode.TUTOR);
        for (PaymentSagaStep done : List.of(
                PaymentSagaStep.RECORD_PAYMENT,
                PaymentSagaStep.ACTIVATE_SUBSCRIPTION,
                PaymentSagaStep.ALLOCATE_SEATS)) {
            saga = saga.succeeded(done, saga.data(), NOW);
        }

        Instant now = NOW;
        for (int attempt = 1; attempt < 5; attempt++) {
            saga = saga.failed(PaymentSagaStep.ACTIVATE_TUTOR_WORKSPACE, "classroom down", now, POLICY);
            assertThat(saga.status()).isEqualTo(SagaStatus.RUNNING);
            assertThat(saga.dueAt(now)).isFalse();
            now = saga.nextRunAt();
        }
        assertThat(saga.step(PaymentSagaStep.ACTIVATE_TUTOR_WORKSPACE).attempts())
                .isEqualTo(4);
        saga = saga.failed(PaymentSagaStep.ACTIVATE_TUTOR_WORKSPACE, "classroom down", now, POLICY);
        assertThat(saga.status()).isEqualTo(SagaStatus.COMPENSATING);

        List<PaymentSagaStep> compensated = new ArrayList<>();
        while (saga.next().isPresent()) {
            PaymentSagaStep step = saga.next().get().step();
            compensated.add(step);
            saga = saga.compensated(step, now);
        }
        assertThat(compensated)
                .containsExactly(
                        PaymentSagaStep.ACTIVATE_TUTOR_WORKSPACE,
                        PaymentSagaStep.ALLOCATE_SEATS,
                        PaymentSagaStep.ACTIVATE_SUBSCRIPTION,
                        PaymentSagaStep.RECORD_PAYMENT);
        assertThat(saga.status()).isEqualTo(SagaStatus.FAILED);
        assertThat(saga.step(PaymentSagaStep.GRANT_ENTITLEMENTS).status()).isEqualTo(StepStatus.SKIPPED);
    }

    @Test
    @DisplayName("A failed compensation is retried later; the admin retry runs the waiting step now")
    void compensationRetry() {
        PaymentSaga saga = saga(PlanTierCode.STUDENT_PRO).succeeded(PaymentSagaStep.RECORD_PAYMENT, data(), NOW);
        saga = saga.failed(
                PaymentSagaStep.ACTIVATE_SUBSCRIPTION, "db", NOW, new RetryPolicy(1, Duration.ZERO, Duration.ZERO));

        saga = saga.compensationFailed(PaymentSagaStep.ACTIVATE_SUBSCRIPTION, "still", NOW, POLICY);
        assertThat(saga.dueAt(NOW)).isFalse();
        assertThat(saga.retryNow(NOW).dueAt(NOW)).isTrue();

        PaymentSaga failed = saga.compensated(PaymentSagaStep.ACTIVATE_SUBSCRIPTION, NOW)
                .compensated(PaymentSagaStep.RECORD_PAYMENT, NOW);
        assertThat(failed.status()).isEqualTo(SagaStatus.FAILED);
        assertThatThrownBy(() -> failed.retryNow(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void backoffDoublesUpToTheCap() {
        assertThat(POLICY.delayAfter(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(POLICY.delayAfter(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(POLICY.delayAfter(10)).isEqualTo(Duration.ofSeconds(30));
    }
}
