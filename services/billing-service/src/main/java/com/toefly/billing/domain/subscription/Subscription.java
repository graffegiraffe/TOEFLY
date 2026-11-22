package com.toefly.billing.domain.subscription;

import com.toefly.billing.domain.plan.PlanTierCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A subscription of a user (docs/03 §6.13). Immutable: each transition returns the next state. Access is granted while
 * the subscription is {@code TRIALING} before the trial end, {@code ACTIVE}, {@code CANCELED} before the period end or
 * {@code PAST_DUE} before {@code graceUntil}.
 */
public record Subscription(
        UUID id,
        UUID userId,
        PlanTierCode tier,
        @Nullable PaymentProviderCode provider,
        @Nullable String providerSubscriptionId,
        @Nullable String providerCustomerId,
        SubscriptionStatus status,
        Instant periodStart,
        Instant periodEnd,
        @Nullable Instant trialEndsAt,
        boolean trialEndingNotified,
        boolean cancelAtPeriodEnd,
        @Nullable Instant canceledAt,
        @Nullable Instant graceUntil,
        @Nullable PlanTierCode pendingTier,
        boolean testMode,
        @Nullable Instant endedAt,
        @Nullable EndReason endReason,
        Instant createdAt,
        int version) {

    public Subscription {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** AC-27.02.1: 7 days of Pro without a card. */
    public static Subscription trial(UUID id, UUID userId, PlanTierCode tier, Duration length, Instant now) {
        Instant end = now.plus(length);
        return new Subscription(
                id,
                userId,
                tier,
                null,
                null,
                null,
                SubscriptionStatus.TRIALING,
                now,
                end,
                end,
                false,
                false,
                null,
                null,
                null,
                true,
                null,
                null,
                now,
                0);
    }

    /** A paid subscription confirmed by the provider. */
    public static Subscription paid(
            UUID id,
            UUID userId,
            PlanTierCode tier,
            PaymentProviderCode provider,
            @Nullable String providerSubscriptionId,
            @Nullable String providerCustomerId,
            Instant periodStart,
            Instant periodEnd,
            boolean testMode,
            Instant now) {
        return new Subscription(
                id,
                userId,
                tier,
                provider,
                providerSubscriptionId,
                providerCustomerId,
                SubscriptionStatus.ACTIVE,
                periodStart,
                periodEnd,
                null,
                false,
                false,
                null,
                null,
                null,
                testMode,
                null,
                null,
                now,
                0);
    }

    public boolean live() {
        return status != SubscriptionStatus.EXPIRED;
    }

    /** A provider subscription renews by itself; a manual grant or a one-off payment ends with its period. */
    public boolean renews() {
        return provider != PaymentProviderCode.MANUAL && providerSubscriptionId != null;
    }

    public boolean trial() {
        return status == SubscriptionStatus.TRIALING;
    }

    public boolean grantsAccess(Instant now) {
        return switch (status) {
            case TRIALING -> trialEndsAt != null && now.isBefore(trialEndsAt);
            case ACTIVE -> renews() || now.isBefore(periodEnd);
            case CANCELED -> now.isBefore(periodEnd);
            case PAST_DUE -> graceUntil != null && now.isBefore(graceUntil);
            case EXPIRED -> false;
        };
    }

    /** Until when access lasts without a further payment; {@code null} while a renewal is expected. */
    public @Nullable Instant accessUntil() {
        return switch (status) {
            case TRIALING -> trialEndsAt;
            case CANCELED -> periodEnd;
            case PAST_DUE -> graceUntil;
            case ACTIVE -> renews() ? null : periodEnd;
            case EXPIRED -> endedAt;
        };
    }

    /** The user paid during the trial or a paid plan replaces the trial: TRIALING → ACTIVE. */
    public Subscription activatePaid(
            PlanTierCode paidTier,
            PaymentProviderCode paidWith,
            @Nullable String paidSubscriptionId,
            @Nullable String customerId,
            Instant start,
            Instant end,
            boolean test) {
        if (status != SubscriptionStatus.TRIALING) {
            throw new SubscriptionStateException("subscription-exists", "A paid subscription is already active.");
        }
        return new Subscription(
                id,
                userId,
                paidTier,
                paidWith,
                paidSubscriptionId,
                customerId,
                SubscriptionStatus.ACTIVE,
                start,
                end,
                trialEndsAt,
                trialEndingNotified,
                false,
                null,
                null,
                null,
                test,
                null,
                null,
                createdAt,
                version);
    }

    /** A renewal payment went through: the next period starts; a scheduled downgrade takes effect (AC-27.05.5). */
    public Subscription renew(Instant start, Instant end) {
        if (status == SubscriptionStatus.EXPIRED || status == SubscriptionStatus.TRIALING) {
            throw new SubscriptionStateException("conflict", "Only a paid subscription renews.");
        }
        PlanTierCode next = pendingTier == null ? tier : pendingTier;
        return new Subscription(
                id,
                userId,
                next,
                provider,
                providerSubscriptionId,
                providerCustomerId,
                cancelAtPeriodEnd ? SubscriptionStatus.CANCELED : SubscriptionStatus.ACTIVE,
                start,
                end,
                trialEndsAt,
                trialEndingNotified,
                cancelAtPeriodEnd,
                canceledAt,
                null,
                null,
                testMode,
                null,
                null,
                createdAt,
                version);
    }

    /** AC-27.06.1: a renewal failed; rights stay until {@code graceUntil}. */
    public Subscription pastDue(Instant now, Duration grace) {
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.PAST_DUE) {
            throw new SubscriptionStateException("conflict", "Only an active subscription becomes past due.");
        }
        Instant until = graceUntil != null ? graceUntil : now.plus(grace);
        return new Subscription(
                id,
                userId,
                tier,
                provider,
                providerSubscriptionId,
                providerCustomerId,
                SubscriptionStatus.PAST_DUE,
                periodStart,
                periodEnd,
                trialEndsAt,
                trialEndingNotified,
                cancelAtPeriodEnd,
                canceledAt,
                until,
                pendingTier,
                testMode,
                null,
                null,
                createdAt,
                version);
    }

    /** AC-27.05.2: cancel at the end of the paid period; rights stay until then. */
    public Subscription cancelAtPeriodEnd(Instant now) {
        if (provider == PaymentProviderCode.APP_STORE) {
            throw new SubscriptionStateException(
                    "manage-in-app-store", "This subscription is managed in the App Store.");
        }
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.PAST_DUE) {
            throw new SubscriptionStateException("no-subscription", "There is no paid subscription to cancel.");
        }
        return new Subscription(
                id,
                userId,
                tier,
                provider,
                providerSubscriptionId,
                providerCustomerId,
                SubscriptionStatus.CANCELED,
                periodStart,
                periodEnd,
                trialEndsAt,
                trialEndingNotified,
                true,
                now,
                graceUntil,
                null,
                testMode,
                null,
                null,
                createdAt,
                version);
    }

    /** CANCELED → ACTIVE before the period ends. */
    public Subscription resume(Instant now) {
        if (status != SubscriptionStatus.CANCELED || !now.isBefore(periodEnd)) {
            throw new SubscriptionStateException("conflict", "Only a canceled subscription before its end resumes.");
        }
        return new Subscription(
                id,
                userId,
                tier,
                provider,
                providerSubscriptionId,
                providerCustomerId,
                SubscriptionStatus.ACTIVE,
                periodStart,
                periodEnd,
                trialEndsAt,
                trialEndingNotified,
                false,
                null,
                null,
                null,
                testMode,
                null,
                null,
                createdAt,
                version);
    }

    /** AC-27.05.3: the upgrade applies at once. */
    public Subscription changeTierNow(PlanTierCode next) {
        requirePaidActive();
        return new Subscription(
                id,
                userId,
                next,
                provider,
                providerSubscriptionId,
                providerCustomerId,
                status,
                periodStart,
                periodEnd,
                trialEndsAt,
                trialEndingNotified,
                cancelAtPeriodEnd,
                canceledAt,
                graceUntil,
                null,
                testMode,
                null,
                null,
                createdAt,
                version);
    }

    /** AC-27.05.5: the downgrade applies from the next period. */
    public Subscription scheduleTier(PlanTierCode next) {
        requirePaidActive();
        return new Subscription(
                id,
                userId,
                tier,
                provider,
                providerSubscriptionId,
                providerCustomerId,
                status,
                periodStart,
                periodEnd,
                trialEndsAt,
                trialEndingNotified,
                cancelAtPeriodEnd,
                canceledAt,
                graceUntil,
                next == tier ? null : next,
                testMode,
                null,
                null,
                createdAt,
                version);
    }

    public Subscription markTrialEndingNotified() {
        return new Subscription(
                id,
                userId,
                tier,
                provider,
                providerSubscriptionId,
                providerCustomerId,
                status,
                periodStart,
                periodEnd,
                trialEndsAt,
                true,
                cancelAtPeriodEnd,
                canceledAt,
                graceUntil,
                pendingTier,
                testMode,
                endedAt,
                endReason,
                createdAt,
                version);
    }

    public Subscription expire(Instant now, EndReason reason) {
        if (status == SubscriptionStatus.EXPIRED) {
            return this;
        }
        return new Subscription(
                id,
                userId,
                tier,
                provider,
                providerSubscriptionId,
                providerCustomerId,
                SubscriptionStatus.EXPIRED,
                periodStart,
                periodEnd,
                trialEndsAt,
                trialEndingNotified,
                cancelAtPeriodEnd,
                canceledAt,
                graceUntil,
                null,
                testMode,
                now,
                reason,
                createdAt,
                version);
    }

    /** The transition a timer should make now: trial over, paid period over after cancel, grace over, grant over. */
    public Optional<EndReason> dueEnd(Instant now) {
        return switch (status) {
            case TRIALING ->
                trialEndsAt != null && !now.isBefore(trialEndsAt)
                        ? Optional.of(EndReason.TRIAL_EXPIRED)
                        : Optional.empty();
            case CANCELED -> !now.isBefore(periodEnd) ? Optional.of(EndReason.USER_CANCELED) : Optional.empty();
            case PAST_DUE ->
                graceUntil != null && !now.isBefore(graceUntil)
                        ? Optional.of(EndReason.PAYMENT_FAILED)
                        : Optional.empty();
            case ACTIVE ->
                !renews() && !now.isBefore(periodEnd)
                        ? Optional.of(
                                provider == PaymentProviderCode.MANUAL ? EndReason.GRANT_ENDED : EndReason.PERIOD_ENDED)
                        : Optional.empty();
            case EXPIRED -> Optional.empty();
        };
    }

    /** AC-27.02.2: the trial ends within {@code notice} and nobody was told yet. */
    public boolean trialEndingSoon(Instant now, Duration notice) {
        return status == SubscriptionStatus.TRIALING
                && !trialEndingNotified
                && trialEndsAt != null
                && !now.plus(notice).isBefore(trialEndsAt)
                && now.isBefore(trialEndsAt);
    }

    private void requirePaidActive() {
        if (status != SubscriptionStatus.ACTIVE || provider == PaymentProviderCode.MANUAL) {
            throw new SubscriptionStateException("no-subscription", "There is no active paid subscription to change.");
        }
    }
}
