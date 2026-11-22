package com.toefly.billing.domain.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.billing.domain.plan.PlanTierCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    static final Instant MONTH = Instant.parse("2026-12-21T10:00:00Z");

    static Subscription paid(PaymentProviderCode provider) {
        return Subscription.paid(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PlanTierCode.STUDENT_PRO,
                provider,
                "sub_1",
                "cus_1",
                NOW,
                MONTH,
                true,
                NOW);
    }

    @Test
    @DisplayName("AC-27.02.1: the trial gives access for 7 × 24 h and ends by timer (AC-27.02.3)")
    void trialLastsSevenDays() {
        Subscription trial = Subscription.trial(
                UUID.randomUUID(), UUID.randomUUID(), PlanTierCode.STUDENT_PRO, Duration.ofDays(7), NOW);

        assertThat(trial.status()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(trial.grantsAccess(NOW.plus(Duration.ofDays(7)).minusSeconds(1)))
                .isTrue();
        assertThat(trial.grantsAccess(NOW.plus(Duration.ofDays(7)))).isFalse();
        assertThat(trial.dueEnd(NOW.plus(Duration.ofDays(7)))).contains(EndReason.TRIAL_EXPIRED);
        assertThat(trial.dueEnd(NOW.plus(Duration.ofDays(6)))).isEmpty();
    }

    @Test
    @DisplayName("AC-27.02.2: TRIAL_ENDING is due 48 h before the end, once")
    void trialEndingNotice() {
        Subscription trial = Subscription.trial(
                UUID.randomUUID(), UUID.randomUUID(), PlanTierCode.STUDENT_PRO, Duration.ofDays(7), NOW);
        Duration notice = Duration.ofHours(48);

        assertThat(trial.trialEndingSoon(NOW.plus(Duration.ofDays(4)), notice)).isFalse();
        assertThat(trial.trialEndingSoon(NOW.plus(Duration.ofDays(5)), notice)).isTrue();
        assertThat(trial.markTrialEndingNotified().trialEndingSoon(NOW.plus(Duration.ofDays(5)), notice))
                .isFalse();
    }

    @Test
    @DisplayName("TRIALING → ACTIVE: paying during the trial replaces it")
    void payDuringTrial() {
        Subscription trial = Subscription.trial(
                UUID.randomUUID(), UUID.randomUUID(), PlanTierCode.STUDENT_PRO, Duration.ofDays(7), NOW);

        Subscription active = trial.activatePaid(
                PlanTierCode.STUDENT_PRO, PaymentProviderCode.STRIPE, "sub_1", "cus_1", NOW, MONTH, true);

        assertThat(active.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(active.trial()).isFalse();
        assertThat(active.provider()).isEqualTo(PaymentProviderCode.STRIPE);
        assertThatThrownBy(() -> active.activatePaid(
                        PlanTierCode.STUDENT_PRO, PaymentProviderCode.STRIPE, "sub_2", "cus_1", NOW, MONTH, true))
                .isInstanceOf(SubscriptionStateException.class);
    }

    @Test
    @DisplayName("AC-27.05.2: cancel keeps rights to the period end, then the subscription expires")
    void cancelAtPeriodEnd() {
        Subscription canceled = paid(PaymentProviderCode.STRIPE).cancelAtPeriodEnd(NOW);

        assertThat(canceled.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(canceled.cancelAtPeriodEnd()).isTrue();
        assertThat(canceled.grantsAccess(MONTH.minusSeconds(1))).isTrue();
        assertThat(canceled.dueEnd(MONTH)).contains(EndReason.USER_CANCELED);
        assertThat(canceled.resume(NOW).status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThatThrownBy(() -> canceled.resume(MONTH)).isInstanceOf(SubscriptionStateException.class);
    }

    @Test
    @DisplayName("AC-27.04.5: an App Store subscription is managed in the App Store")
    void appStoreIsCanceledInTheAppStore() {
        assertThatThrownBy(() -> paid(PaymentProviderCode.APP_STORE).cancelAtPeriodEnd(NOW))
                .isInstanceOfSatisfying(
                        SubscriptionStateException.class,
                        e -> assertThat(e.code()).isEqualTo("manage-in-app-store"));
    }

    @Test
    @DisplayName("AC-27.06.1, AC-27.06.2: a failed renewal keeps rights for the grace period, then expires")
    void pastDueGrace() {
        Subscription pastDue = paid(PaymentProviderCode.STRIPE).pastDue(MONTH, Duration.ofDays(7));

        assertThat(pastDue.graceUntil()).isEqualTo(MONTH.plus(Duration.ofDays(7)));
        assertThat(pastDue.grantsAccess(MONTH.plus(Duration.ofDays(6)))).isTrue();
        assertThat(pastDue.pastDue(MONTH.plus(Duration.ofDays(3)), Duration.ofDays(7))
                        .graceUntil())
                .as("a repeated failure does not move the grace")
                .isEqualTo(pastDue.graceUntil());
        assertThat(pastDue.dueEnd(MONTH.plus(Duration.ofDays(7)))).contains(EndReason.PAYMENT_FAILED);
        Subscription renewed = pastDue.renew(MONTH, MONTH.plus(Duration.ofDays(30)));
        assertThat(renewed.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(renewed.graceUntil()).isNull();
    }

    @Test
    @DisplayName("AC-27.05.5: a scheduled downgrade applies at the renewal")
    void downgradeAtRenewal() {
        Subscription tutorPlus = Subscription.paid(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PlanTierCode.TUTOR_PLUS,
                PaymentProviderCode.BEPAID,
                null,
                null,
                NOW,
                MONTH,
                true,
                NOW);

        Subscription scheduled = tutorPlus.scheduleTier(PlanTierCode.TUTOR);
        assertThat(scheduled.tier()).isEqualTo(PlanTierCode.TUTOR_PLUS);
        assertThat(scheduled.pendingTier()).isEqualTo(PlanTierCode.TUTOR);
        Subscription renewed = scheduled.renew(MONTH, MONTH.plus(Duration.ofDays(30)));
        assertThat(renewed.tier()).isEqualTo(PlanTierCode.TUTOR);
        assertThat(renewed.pendingTier()).isNull();
    }

    @Test
    @DisplayName("A manual grant ends at its date")
    void manualGrantEnds() {
        Subscription grant = paid(PaymentProviderCode.MANUAL);

        assertThat(grant.grantsAccess(MONTH.minusSeconds(1))).isTrue();
        assertThat(grant.grantsAccess(MONTH)).isFalse();
        assertThat(grant.dueEnd(MONTH)).contains(EndReason.GRANT_ENDED);
        assertThat(grant.expire(MONTH, EndReason.GRANT_ENDED).live()).isFalse();
    }
}
