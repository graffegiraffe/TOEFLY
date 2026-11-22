package com.toefly.billing.domain.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.Subscription;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class EntitlementCalculatorTest {

    static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    static final Set<Entitlement> PRO = EnumSet.of(
            Entitlement.UNLIMITED_WEEKS,
            Entitlement.AI_WRITING_CHECK,
            Entitlement.AI_SPEAKING_CHECK,
            Entitlement.SPEAKING,
            Entitlement.MOCK_EXAM,
            Entitlement.AI_TUTOR_CHAT,
            Entitlement.WEEKLY_REVIEW);

    /** The reference data of the migration (docs/02 §1.3). */
    public static List<PlanTier> tiers() {
        return List.of(
                new PlanTier(
                        PlanTierCode.STUDENT_FREE,
                        0,
                        "EUR",
                        null,
                        null,
                        0,
                        10,
                        EnumSet.of(
                                Entitlement.AI_WRITING_CHECK,
                                Entitlement.AI_SPEAKING_CHECK,
                                Entitlement.SPEAKING,
                                Entitlement.WEEKLY_REVIEW)),
                new PlanTier(PlanTierCode.STUDENT_PRO, 1200, "EUR", null, null, 7, null, PRO),
                new PlanTier(
                        PlanTierCode.TUTOR,
                        2900,
                        "EUR",
                        10,
                        2L << 30,
                        0,
                        null,
                        EnumSet.of(Entitlement.TUTOR_WORKSPACE, Entitlement.TUTOR_MATERIALS)),
                new PlanTier(
                        PlanTierCode.TUTOR_PLUS,
                        5900,
                        "EUR",
                        30,
                        10L << 30,
                        0,
                        null,
                        EnumSet.of(Entitlement.TUTOR_WORKSPACE, Entitlement.TUTOR_MATERIALS)));
    }

    final EntitlementCalculator calculator = new EntitlementCalculator(tiers());
    final UUID user = UUID.randomUUID();

    @Test
    @DisplayName("docs/02 §1.3: Free has the base set with 10 shared AI checks")
    void freeBaseSet() {
        EntitlementSet set = calculator.calculate(user, null, null, 3, NOW);

        assertThat(set.planTierCode()).isEqualTo(PlanTierCode.STUDENT_FREE);
        assertThat(set.entitlements())
                .containsExactlyInAnyOrder(
                        Entitlement.AI_WRITING_CHECK,
                        Entitlement.AI_SPEAKING_CHECK,
                        Entitlement.SPEAKING,
                        Entitlement.WEEKLY_REVIEW);
        assertThat(set.freeAiChecks().remaining()).isEqualTo(7);
        assertThat(set.grants())
                .filteredOn(grant -> grant.entitlement() == Entitlement.AI_WRITING_CHECK)
                .singleElement()
                .satisfies(grant -> {
                    assertThat(grant.limit()).isEqualTo(10);
                    assertThat(grant.used()).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("AC-27.02.1: the trial gives all Pro rights without limits and shows its end")
    void trialIsPro() {
        Subscription trial =
                Subscription.trial(UUID.randomUUID(), user, PlanTierCode.STUDENT_PRO, Duration.ofDays(7), NOW);

        EntitlementSet set = calculator.calculate(user, trial, null, 0, NOW);

        assertThat(set.planTierCode()).isEqualTo(PlanTierCode.STUDENT_PRO);
        assertThat(set.entitlements()).containsExactlyInAnyOrderElementsOf(PRO);
        assertThat(set.trialEndsAt()).isEqualTo(trial.trialEndsAt());
        assertThat(set.grants()).allSatisfy(grant -> {
            assertThat(grant.source()).isEqualTo(EntitlementSource.TRIAL);
            assertThat(grant.limit()).isNull();
        });
    }

    @Test
    @DisplayName("phase 11a DoD: rights are revoked when the subscription ends")
    void expiredSubscriptionGivesFree() {
        Subscription trial =
                Subscription.trial(UUID.randomUUID(), user, PlanTierCode.STUDENT_PRO, Duration.ofDays(7), NOW);

        EntitlementSet set = calculator.calculate(user, trial, null, 0, NOW.plus(Duration.ofDays(8)));

        assertThat(set.planTierCode()).isEqualTo(PlanTierCode.STUDENT_FREE);
        assertThat(set.has(Entitlement.MOCK_EXAM)).isFalse();
        assertThat(set.fingerprint())
                .isNotEqualTo(calculator.calculate(user, trial, null, 0, NOW).fingerprint());
    }

    @Test
    @DisplayName("docs/02 §1.3: a seat at a tutor gives Pro; the tutor gets no student rights")
    void seatAndTutor() {
        UUID tutor = UUID.randomUUID();
        UUID tutorSubscription = UUID.randomUUID();

        EntitlementSet student = calculator.calculate(
                user, null, new EntitlementCalculator.SeatHolding(tutor, tutorSubscription, true), 10, NOW);
        assertThat(student.planTierCode()).isEqualTo(PlanTierCode.STUDENT_PRO);
        assertThat(student.tutorId()).isEqualTo(tutor);
        assertThat(student.entitlements()).containsExactlyInAnyOrderElementsOf(PRO);

        Subscription tutorPlan = Subscription.paid(
                tutorSubscription,
                tutor,
                PlanTierCode.TUTOR,
                PaymentProviderCode.STRIPE,
                "sub",
                "cus",
                NOW,
                NOW.plus(Duration.ofDays(30)),
                true,
                NOW);
        EntitlementSet tutorSet = calculator.calculate(tutor, tutorPlan, null, 0, NOW);
        assertThat(tutorSet.planTierCode()).isEqualTo(PlanTierCode.TUTOR);
        assertThat(tutorSet.entitlements())
                .containsExactlyInAnyOrder(Entitlement.TUTOR_WORKSPACE, Entitlement.TUTOR_MATERIALS);
    }

    @Test
    @DisplayName("The fingerprint is stable for the same rights")
    void fingerprintIsStable() {
        assertThat(calculator.calculate(user, null, null, 2, NOW).fingerprint())
                .isEqualTo(calculator
                        .calculate(user, null, null, 2, NOW.plusSeconds(60))
                        .fingerprint())
                .isNotEqualTo(calculator.calculate(user, null, null, 3, NOW).fingerprint());
    }
}
