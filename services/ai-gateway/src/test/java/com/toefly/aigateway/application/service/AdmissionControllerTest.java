package com.toefly.aigateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.quota.QuotaDemand;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdmissionControllerTest {

    final Gateway gateway = new Gateway();
    final UUID learner = UUID.randomUUID();

    @Test
    void callThatDoesNotFitWaitsInTheQueueUntilTheBucketRefills() {
        gateway.quotas.save(Fakes.tpm(Gateway.BIG, 6000));
        Instant deadline = gateway.clock.instant().plusSeconds(120);
        assertThat(gateway.admission
                        .admit(Gateway.BIG, QuotaDemand.tokens(5000), Priority.GRADING, learner, deadline)
                        .admitted())
                .isTrue();
        Instant before = gateway.clock.instant();

        var second =
                gateway.admission.admit(Gateway.BIG, QuotaDemand.tokens(5000), Priority.GRADING, learner, deadline);

        assertThat(second.admitted()).isTrue();
        assertThat(Duration.between(before, gateway.clock.instant()))
                .isBetween(Duration.ofSeconds(40), Duration.ofSeconds(42));
        assertThat(gateway.store.queues.get(learner + ":GRADING")).isZero();
    }

    @Test
    void interactiveCallGivesUpAfterItsShortWaitAndSaysWhenToComeBack() {
        gateway.quotas.save(Fakes.tpm(Gateway.SMALL, 6000));
        gateway.admission.admit(
                Gateway.SMALL, QuotaDemand.tokens(6000), Priority.INTERACTIVE, learner, gateway.clock.instant());

        var refused = gateway.admission.admit(
                Gateway.SMALL,
                QuotaDemand.tokens(3000),
                Priority.INTERACTIVE,
                learner,
                gateway.clock.instant().plusSeconds(3));

        assertThat(refused.admitted()).isFalse();
        assertThat(refused.retryAfterMillis()).isPositive();
    }

    @Test
    void learnerWaitsWithAtMostTwoGradingCalls() {
        gateway.quotas.save(Fakes.tpm(Gateway.BIG, 6000));
        gateway.admission.admit(
                Gateway.BIG, QuotaDemand.tokens(6000), Priority.GRADING, learner, gateway.clock.instant());
        gateway.store.queues.put(learner + ":GRADING", 2);
        Instant before = gateway.clock.instant();

        var third = gateway.admission.admit(
                Gateway.BIG, QuotaDemand.tokens(1000), Priority.GRADING, learner, before.plusSeconds(120));

        assertThat(third.admitted()).isFalse();
        assertThat(gateway.clock.instant()).isEqualTo(before);
    }

    @Test
    void requestLargerThanTheMinuteBudgetNeverFits() {
        gateway.quotas.save(Fakes.tpm(Gateway.BIG, 6000));

        var tooBig = gateway.admission.admit(
                Gateway.BIG,
                QuotaDemand.tokens(9000),
                Priority.GRADING,
                learner,
                gateway.clock.instant().plusSeconds(120));

        assertThat(tooBig.admitted()).isFalse();
        assertThat(tooBig.retryAfterMillis()).isEqualTo(-1);
    }

    @Test
    void modelWithoutLimitsIsAdmittedAtOnce() {
        var local = gateway.admission.admit(
                Gateway.LOCAL, QuotaDemand.tokens(100_000), Priority.BACKGROUND, null, gateway.clock.instant());

        assertThat(local.admitted()).isTrue();
    }
}
