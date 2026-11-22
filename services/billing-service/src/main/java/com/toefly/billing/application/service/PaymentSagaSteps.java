package com.toefly.billing.application.service;

import com.toefly.billing.application.port.in.BillingException;
import com.toefly.billing.application.port.out.BillingEventPublisherPort;
import com.toefly.billing.application.port.out.Cause;
import com.toefly.billing.application.port.out.CheckoutSessionRepository;
import com.toefly.billing.application.port.out.ClassroomPort;
import com.toefly.billing.application.port.out.PaymentRepository;
import com.toefly.billing.application.port.out.SeatRepository;
import com.toefly.billing.application.port.out.SubscriptionRepository;
import com.toefly.billing.domain.payment.Payment;
import com.toefly.billing.domain.payment.PaymentStatus;
import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.saga.PaymentSaga;
import com.toefly.billing.domain.saga.PaymentSagaData;
import com.toefly.billing.domain.saga.PaymentSagaStep;
import com.toefly.billing.domain.seat.NoFreeSeatException;
import com.toefly.billing.domain.seat.SeatPool;
import com.toefly.billing.domain.subscription.EndReason;
import com.toefly.billing.domain.subscription.Subscription;
import com.toefly.billing.domain.subscription.SubscriptionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The actions and compensations of the payment saga (docs/04 §5.10). Each runs in the transaction of the
 * orchestrator together with the saga state, so a local step happens exactly once; remote calls are idempotent.
 */
@Component
public class PaymentSagaSteps {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaSteps.class);

    private final PaymentRepository payments;
    private final SubscriptionRepository subscriptions;
    private final SeatRepository seats;
    private final CheckoutSessionRepository sessions;
    private final ClassroomPort classroom;
    private final PaymentProviders providers;
    private final EntitlementService entitlements;
    private final SubscriptionEnding ending;
    private final BillingEventPublisherPort events;
    private final Clock clock;

    public PaymentSagaSteps(
            PaymentRepository payments,
            SubscriptionRepository subscriptions,
            SeatRepository seats,
            CheckoutSessionRepository sessions,
            ClassroomPort classroom,
            PaymentProviders providers,
            EntitlementService entitlements,
            SubscriptionEnding ending,
            BillingEventPublisherPort events,
            Clock clock) {
        this.payments = payments;
        this.subscriptions = subscriptions;
        this.seats = seats;
        this.sessions = sessions;
        this.classroom = classroom;
        this.providers = providers;
        this.entitlements = entitlements;
        this.ending = ending;
        this.events = events;
        this.clock = clock;
    }

    PaymentSagaData run(PaymentSaga saga, PaymentSagaStep step) {
        PaymentSagaData data = saga.data();
        Cause cause = Cause.of(saga.id());
        return switch (step) {
            case RECORD_PAYMENT -> recordPayment(saga, data);
            case ACTIVATE_SUBSCRIPTION -> activateSubscription(saga, data);
            case ALLOCATE_SEATS -> {
                SeatPool pool = seats.load(subscriptionId(data));
                seats.save(pool.resize(tier(saga).seats(), UUID::randomUUID));
                yield data;
            }
            case ACTIVATE_TUTOR_WORKSPACE -> {
                classroom.putWorkspace(saga.userId(), true, tier(saga).seats(), saga.tier());
                yield data;
            }
            case GRANT_ENTITLEMENTS -> {
                if (saga.tier().tutor()) {
                    grantStudents(saga, data, cause);
                }
                entitlements.recalculate(saga.userId(), cause);
                yield data;
            }
            case COMPLETE -> {
                complete(saga, data, cause);
                yield data;
            }
        };
    }

    void compensate(PaymentSaga saga, PaymentSagaStep step) {
        PaymentSagaData data = saga.data();
        Cause cause = Cause.of(saga.id());
        switch (step) {
            case RECORD_PAYMENT -> refund(saga, data);
            case ACTIVATE_SUBSCRIPTION -> {
                if (data.subscriptionId() != null) {
                    subscriptions
                            .lockById(data.subscriptionId())
                            .ifPresent(subscription -> ending.end(subscription, EndReason.SAGA_COMPENSATED, cause));
                }
            }
            case ALLOCATE_SEATS -> {
                if (data.subscriptionId() != null) {
                    SeatPool pool = seats.load(data.subscriptionId());
                    seats.save(pool.releaseAll().resize(0, UUID::randomUUID));
                }
            }
            case ACTIVATE_TUTOR_WORKSPACE -> classroom.putWorkspace(saga.userId(), false, 0, saga.tier());
            case GRANT_ENTITLEMENTS -> {
                if (saga.tier().tutor() && data.subscriptionId() != null) {
                    subscriptions
                            .findById(data.subscriptionId())
                            .ifPresent(subscription -> ending.releaseSeats(subscription, cause));
                }
                entitlements.recalculate(saga.userId(), cause);
            }
            case COMPLETE -> {
                // subscription-activated is not taken back; compensation of the subscription publishes canceled
            }
        }
    }

    /** The saga ended FAILED: the user and the admin learn about it (AC-27.03.3). */
    void failed(PaymentSaga saga) {
        Instant now = clock.instant();
        PaymentSagaData data = saga.data();
        log.error(
                "ALERT payment saga {} compensated: user {}, tier {}, provider {}, payment {} refunded",
                saga.id(),
                saga.userId(),
                saga.tier(),
                saga.provider(),
                data.providerPaymentId());
        events.paymentFailed(
                saga.userId(),
                data.paymentId(),
                data.subscriptionId(),
                saga.tier(),
                saga.provider(),
                "SAGA_COMPENSATED",
                "We could not activate your plan. The payment was refunded.",
                null,
                now,
                Cause.of(saga.id()));
        if (data.checkoutSessionId() != null) {
            sessions.findById(data.checkoutSessionId())
                    .ifPresent(session -> sessions.update(session.failed(
                            "SAGA_COMPENSATED", "We could not activate your plan. The payment was refunded.")));
        }
    }

    private PaymentSagaData recordPayment(PaymentSaga saga, PaymentSagaData data) {
        Payment payment = payments.insertIfAbsent(Payment.succeeded(
                UUID.randomUUID(),
                null,
                saga.userId(),
                saga.tier(),
                saga.provider(),
                data.providerPaymentId(),
                data.amountMinor(),
                data.currency(),
                data.paidAt(),
                data.receiptUrl(),
                data.testMode()));
        return data.withPayment(payment.id());
    }

    private PaymentSagaData activateSubscription(PaymentSaga saga, PaymentSagaData data) {
        Optional<Subscription> live = subscriptions.lockLive(saga.userId());
        Subscription active;
        boolean replacedTrial = false;
        if (live.isPresent() && live.get().id().equals(data.subscriptionId())) {
            return data; // already done by an earlier attempt that committed
        }
        if (live.isPresent() && live.get().status() == SubscriptionStatus.TRIALING) {
            active = live.get()
                    .activatePaid(
                            saga.tier(),
                            saga.provider(),
                            data.providerSubscriptionId(),
                            data.providerCustomerId(),
                            data.periodStart(),
                            data.periodEnd(),
                            data.testMode());
            replacedTrial = true;
        } else if (live.isPresent()) {
            throw BillingException.conflict("subscription-exists", "The user already has a live subscription.");
        } else {
            active = Subscription.paid(
                    UUID.randomUUID(),
                    saga.userId(),
                    saga.tier(),
                    saga.provider(),
                    data.providerSubscriptionId(),
                    data.providerCustomerId(),
                    data.periodStart(),
                    data.periodEnd(),
                    data.testMode(),
                    clock.instant());
        }
        Subscription saved = subscriptions.save(active);
        if (data.paymentId() != null) {
            payments.findById(data.paymentId())
                    .ifPresent(payment -> payments.update(payment.forSubscription(saved.id())));
        }
        return data.withSubscription(saved.id(), replacedTrial);
    }

    /** Step 5 of a tutor: students connected in classroom get seats (docs/04 §5.10). */
    private void grantStudents(PaymentSaga saga, PaymentSagaData data, Cause cause) {
        SeatPool pool = seats.load(subscriptionId(data));
        for (UUID student : classroom.enrollments(saga.userId())) {
            if (seats.findByStudent(student).isPresent()) {
                continue;
            }
            try {
                pool = pool.assign(student, clock.instant());
            } catch (NoFreeSeatException full) {
                log.warn("Tutor {} has more students than seats; {} stays without a seat", saga.userId(), student);
            }
        }
        seats.save(pool);
        pool.students().forEach(student -> entitlements.recalculate(student, cause));
    }

    private void complete(PaymentSaga saga, PaymentSagaData data, Cause cause) {
        Subscription subscription = subscriptions
                .findById(subscriptionId(data))
                .orElseThrow(() -> new IllegalStateException("subscription of saga " + saga.id() + " is gone"));
        Instant now = clock.instant();
        events.subscriptionActivated(
                subscription, saga.tier().tutor() ? tier(saga).seats() : null, now, cause);
        if (data.paymentId() != null) {
            payments.findById(data.paymentId())
                    .ifPresent(payment ->
                            events.paymentSucceeded(payment, data.periodStart(), data.periodEnd(), false, cause));
        }
        if (data.checkoutSessionId() != null) {
            sessions.findById(data.checkoutSessionId()).ifPresent(session -> sessions.update(session.completed()));
        }
    }

    private void refund(PaymentSaga saga, PaymentSagaData data) {
        providers
                .find(saga.provider())
                .ifPresent(provider ->
                        provider.refund(data.providerPaymentId(), data.amountMinor(), data.providerSubscriptionId()));
        payments.findByProviderRef(saga.provider(), data.providerPaymentId())
                .filter(payment -> payment.status() == PaymentStatus.SUCCEEDED)
                .ifPresent(payment -> payments.update(payment.refunded()));
    }

    private PlanTier tier(PaymentSaga saga) {
        return entitlements.calculator().tier(saga.tier());
    }

    private static UUID subscriptionId(PaymentSagaData data) {
        if (data.subscriptionId() == null) {
            throw new IllegalStateException("the subscription step has not run");
        }
        return data.subscriptionId();
    }
}
