package com.toefly.billing.adapter.out.messaging;

import com.toefly.billing.application.port.out.BillingEventPublisherPort;
import com.toefly.billing.application.port.out.Cause;
import com.toefly.billing.domain.entitlement.EntitlementSet;
import com.toefly.billing.domain.entitlement.Grant;
import com.toefly.billing.domain.payment.Payment;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.EndReason;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.Subscription;
import com.toefly.common.outbox.IntegrationEvent;
import com.toefly.common.outbox.IntegrationEventPublisher;
import com.toefly.events.billing.EntitlementGrantV1;
import com.toefly.events.billing.EntitlementsChangedV1;
import com.toefly.events.billing.PaymentFailedV1;
import com.toefly.events.billing.PaymentSucceededV1;
import com.toefly.events.billing.SubscriptionActivatedV1;
import com.toefly.events.billing.SubscriptionCanceledV1;
import com.toefly.events.billing.SubscriptionEndReason;
import com.toefly.events.billing.TrialEndingV1;
import com.toefly.events.common.Entitlement;
import com.toefly.events.common.EntitlementSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Billing events through the transactional outbox; the key of every topic is {@code userId}. */
@Component
public class OutboxBillingEventPublisherAdapter implements BillingEventPublisherPort {

    static final String SUBSCRIPTION_ACTIVATED = "toefly.billing.subscription-activated.v1";
    static final String SUBSCRIPTION_CANCELED = "toefly.billing.subscription-canceled.v1";
    static final String ENTITLEMENTS_CHANGED = "toefly.billing.entitlements-changed.v1";
    static final String PAYMENT_FAILED = "toefly.billing.payment-failed.v1";
    static final String PAYMENT_SUCCEEDED = "toefly.billing.payment-succeeded.v1";
    static final String TRIAL_ENDING = "toefly.billing.trial-ending.v1";

    private static final Logger log = LoggerFactory.getLogger(OutboxBillingEventPublisherAdapter.class);

    private final IntegrationEventPublisher publisher;

    public OutboxBillingEventPublisherAdapter(IntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public List<String> topics() {
        return List.of(
                SUBSCRIPTION_ACTIVATED,
                SUBSCRIPTION_CANCELED,
                ENTITLEMENTS_CHANGED,
                PAYMENT_FAILED,
                PAYMENT_SUCCEEDED,
                TRIAL_ENDING);
    }

    @Override
    public void subscriptionActivated(Subscription s, @Nullable Integer seatLimit, Instant at, Cause cause) {
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                SUBSCRIPTION_ACTIVATED,
                "Subscription",
                s.id(),
                s.userId(),
                "subscriptions/" + s.id(),
                at,
                cause,
                SubscriptionActivatedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(at)
                        .setUserId(s.userId())
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setSubscriptionId(s.id())
                        .setPlanTierCode(tier(s.tier()))
                        .setProvider(provider(s.provider()))
                        .setTrial(s.trial())
                        .setPeriodStart(s.periodStart())
                        .setPeriodEnd(s.periodEnd())
                        .setSeatLimit(seatLimit)
                        .setTestMode(s.testMode())
                        .build());
    }

    @Override
    public void subscriptionCanceled(Subscription s, EndReason reason, Instant at, Cause cause) {
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                SUBSCRIPTION_CANCELED,
                "Subscription",
                s.id(),
                s.userId(),
                "subscriptions/" + s.id(),
                at,
                cause,
                SubscriptionCanceledV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(at)
                        .setUserId(s.userId())
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setSubscriptionId(s.id())
                        .setPlanTierCode(tier(s.tier()))
                        .setProvider(provider(s.provider()))
                        .setReason(SubscriptionEndReason.valueOf(reason.name()))
                        .setEndedAt(s.endedAt() == null ? at : s.endedAt())
                        .build());
    }

    @Override
    public void entitlementsChanged(EntitlementSet set, long version, Instant changedAt, Cause cause) {
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                ENTITLEMENTS_CHANGED,
                "Entitlements",
                set.userId(),
                set.userId(),
                "users/" + set.userId() + "/entitlements",
                changedAt,
                cause,
                EntitlementsChangedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(changedAt)
                        .setUserId(set.userId())
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setEntitlementsVersion(version)
                        .setPlanTierCode(tier(set.planTierCode()))
                        .setEntitlements(set.entitlements().stream()
                                .sorted()
                                .map(entitlement -> Entitlement.valueOf(entitlement.name()))
                                .toList())
                        .setGrants(set.grants().stream()
                                .map(OutboxBillingEventPublisherAdapter::grant)
                                .toList())
                        .setTutorId(set.tutorId())
                        .setTrialEndsAt(set.trialEndsAt())
                        .setChangedAt(changedAt)
                        .build());
    }

    /**
     * docs/05 §8.5.6 asks for a tombstone on the compacted topic; the outbox of toefly-common-outbox has no tombstone
     * row yet, so the erasure is only logged until the library supports it (phase 11a notes).
     */
    @Override
    public void entitlementsErased(UUID userId) {
        log.info("Entitlements of {} erased; tombstone pending outbox support", userId);
    }

    @Override
    public void paymentSucceeded(
            Payment payment, Instant periodStart, Instant periodEnd, boolean renewal, Cause cause) {
        UUID eventId = UUID.randomUUID();
        UUID user = requireUser(payment);
        Instant paidAt = payment.paidAt() == null ? payment.createdAt() : payment.paidAt();
        publish(
                eventId,
                PAYMENT_SUCCEEDED,
                "Payment",
                payment.id(),
                user,
                "payments/" + payment.id(),
                paidAt,
                cause,
                PaymentSucceededV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(paidAt)
                        .setUserId(user)
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setPaymentId(payment.id())
                        .setSubscriptionId(payment.subscriptionId() == null ? payment.id() : payment.subscriptionId())
                        .setPlanTierCode(tier(payment.tier()))
                        .setProvider(provider(payment.provider()))
                        .setAmountMinor(
                                payment.provider() == PaymentProviderCode.APP_STORE ? null : payment.amountMinor())
                        .setCurrency(payment.provider() == PaymentProviderCode.APP_STORE ? null : payment.currency())
                        .setPaidAt(paidAt)
                        .setPeriodStart(periodStart)
                        .setPeriodEnd(periodEnd)
                        .setReceiptUrl(payment.receiptUrl())
                        .setRenewal(renewal)
                        .setTestMode(payment.testMode())
                        .build());
    }

    @Override
    public void paymentFailed(
            UUID userId,
            @Nullable UUID paymentId,
            @Nullable UUID subscriptionId,
            PlanTierCode tier,
            PaymentProviderCode provider,
            String reason,
            @Nullable String message,
            @Nullable Instant graceUntil,
            Instant at,
            Cause cause) {
        UUID eventId = UUID.randomUUID();
        UUID aggregate = paymentId != null ? paymentId : subscriptionId != null ? subscriptionId : eventId;
        publish(
                eventId,
                PAYMENT_FAILED,
                "Payment",
                aggregate,
                userId,
                "payments/" + aggregate,
                at,
                cause,
                PaymentFailedV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(at)
                        .setUserId(userId)
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setPaymentId(paymentId)
                        .setSubscriptionId(subscriptionId)
                        .setPlanTierCode(tier(tier))
                        .setProvider(provider(provider))
                        .setReason(reason)
                        .setMessage(message)
                        .setGraceUntil(graceUntil)
                        .build());
    }

    @Override
    public void trialEnding(Subscription trial, Instant at, Cause cause) {
        UUID eventId = UUID.randomUUID();
        publish(
                eventId,
                TRIAL_ENDING,
                "Subscription",
                trial.id(),
                trial.userId(),
                "subscriptions/" + trial.id(),
                at,
                cause,
                TrialEndingV1.newBuilder()
                        .setEventId(eventId)
                        .setOccurredAt(at)
                        .setUserId(trial.userId())
                        .setCorrelationId(cause.correlationId())
                        .setCausationId(cause.causationId())
                        .setSubscriptionId(trial.id())
                        .setTrialEndsAt(trial.trialEndsAt() == null ? trial.periodEnd() : trial.trialEndsAt())
                        .build());
    }

    private void publish(
            UUID eventId,
            String topic,
            String aggregateType,
            UUID aggregateId,
            UUID userId,
            String subject,
            Instant at,
            Cause cause,
            SpecificRecord payload) {
        publisher.publish(new IntegrationEvent(
                eventId,
                topic,
                aggregateType,
                aggregateId.toString(),
                userId.toString(),
                subject,
                at,
                userId,
                cause.correlationId(),
                cause.causationId(),
                payload));
    }

    private static EntitlementGrantV1 grant(Grant grant) {
        return EntitlementGrantV1.newBuilder()
                .setEntitlement(Entitlement.valueOf(grant.entitlement().name()))
                .setSource(EntitlementSource.valueOf(grant.source().name()))
                .setLimit(grant.limit())
                .setUsed(grant.used())
                .setValidUntil(grant.validUntil())
                .build();
    }

    private static com.toefly.events.common.PlanTierCode tier(PlanTierCode code) {
        return com.toefly.events.common.PlanTierCode.valueOf(code.name());
    }

    private static com.toefly.events.common.@Nullable PaymentProviderCode provider(@Nullable PaymentProviderCode code) {
        return code == null ? null : com.toefly.events.common.PaymentProviderCode.valueOf(code.name());
    }

    private static UUID requireUser(Payment payment) {
        if (payment.userId() == null) {
            throw new IllegalStateException("payment " + payment.id() + " has no user");
        }
        return payment.userId();
    }
}
