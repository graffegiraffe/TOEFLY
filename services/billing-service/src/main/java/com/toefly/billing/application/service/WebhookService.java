package com.toefly.billing.application.service;

import com.toefly.billing.application.port.in.WebhookUseCase;
import com.toefly.billing.application.port.out.BillingEventPublisherPort;
import com.toefly.billing.application.port.out.Cause;
import com.toefly.billing.application.port.out.CheckoutSessionRepository;
import com.toefly.billing.application.port.out.PaymentRepository;
import com.toefly.billing.application.port.out.ProviderEvent;
import com.toefly.billing.application.port.out.SeatRepository;
import com.toefly.billing.application.port.out.SubscriptionRepository;
import com.toefly.billing.application.port.out.WebhookEventRepository;
import com.toefly.billing.domain.checkout.CheckoutSession;
import com.toefly.billing.domain.payment.Payment;
import com.toefly.billing.domain.payment.PaymentStatus;
import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.saga.PaymentSaga;
import com.toefly.billing.domain.saga.PaymentSagaData;
import com.toefly.billing.domain.seat.SeatPool;
import com.toefly.billing.domain.subscription.EndReason;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.Subscription;
import com.toefly.billing.domain.subscription.SubscriptionStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Webhooks (docs/10 §17.3): the signature is checked on the raw body, the event is stored once by its provider id and
 * acknowledged; processing follows at once and is retried by the poller when it fails. Every provider maps to the same
 * {@link ProviderEvent}s, so Stripe, bePaid and the App Store reach the same saga (ADR-0021).
 */
@Service
public class WebhookService implements WebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final PaymentProviders providers;
    private final WebhookEventRepository webhooks;
    private final CheckoutSessionRepository sessions;
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final SeatRepository seats;
    private final PaymentSagaService sagas;
    private final EntitlementService entitlements;
    private final SubscriptionEnding ending;
    private final BillingEventPublisherPort events;
    private final BillingPolicy policy;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public WebhookService(
            PaymentProviders providers,
            WebhookEventRepository webhooks,
            CheckoutSessionRepository sessions,
            SubscriptionRepository subscriptions,
            PaymentRepository payments,
            SeatRepository seats,
            PaymentSagaService sagas,
            EntitlementService entitlements,
            SubscriptionEnding ending,
            BillingEventPublisherPort events,
            BillingPolicy policy,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.providers = providers;
        this.webhooks = webhooks;
        this.sessions = sessions;
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.seats = seats;
        this.sagas = sagas;
        this.entitlements = entitlements;
        this.ending = ending;
        this.events = events;
        this.policy = policy;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public void receive(PaymentProviderCode provider, Map<String, String> headers, byte[] body) {
        ProviderEvent event = providers.require(provider).parseWebhook(headers, body);
        accept(provider, event);
    }

    /** Stores a verified event once and processes it; a repeated delivery changes nothing (INV-BIL-03). */
    public void accept(PaymentProviderCode provider, ProviderEvent event) {
        Boolean stored = transactions.execute(status -> webhooks.insertIfAbsent(provider, event, clock.instant()));
        log.info("Webhook {} {} {} stored={}", provider, event.type(), event.eventId(), stored);
        if (Boolean.TRUE.equals(stored)) {
            process(provider, event.eventId());
        }
    }

    @Override
    public int processPending() {
        int handled = 0;
        for (WebhookEventRepository.Stored stored :
                transactions.execute(status -> webhooks.lockPending(20, clock.instant()))) {
            process(stored.provider(), stored.event().eventId());
            handled++;
        }
        return handled;
    }

    private void process(PaymentProviderCode provider, String eventId) {
        UUID sagaId;
        try {
            sagaId = transactions.execute(status -> {
                Optional<WebhookEventRepository.Stored> locked = webhooks.lock(provider, eventId);
                if (locked.isEmpty()) {
                    return null;
                }
                UUID started = handle(provider, locked.get().event());
                webhooks.markProcessed(provider, eventId, clock.instant());
                return started;
            });
        } catch (RuntimeException failure) {
            log.warn("Webhook {} {} failed, retried later: {}", provider, eventId, failure.toString());
            transactions.executeWithoutResult(status -> webhooks.markFailed(
                    provider, eventId, failure.toString(), clock.instant().plus(Duration.ofSeconds(30))));
            return;
        }
        if (sagaId != null) {
            sagas.drive(sagaId);
        }
    }

    /** Returns the id of a started saga. */
    private @Nullable UUID handle(PaymentProviderCode provider, ProviderEvent event) {
        Cause cause = Cause.none();
        return switch (event) {
            case ProviderEvent.CheckoutCompleted completed -> checkoutCompleted(provider, completed);
            case ProviderEvent.CheckoutFailed failed -> {
                checkoutFailed(provider, failed, cause);
                yield null;
            }
            case ProviderEvent.RenewalPaid renewal -> {
                renewalPaid(provider, renewal, cause);
                yield null;
            }
            case ProviderEvent.RenewalFailed failed -> {
                renewalFailed(provider, failed, cause);
                yield null;
            }
            case ProviderEvent.SubscriptionEnded ended -> {
                subscriptions
                        .findByProviderRef(provider, ended.providerSubscriptionId())
                        .flatMap(subscription -> subscriptions.lockById(subscription.id()))
                        .ifPresent(subscription -> ending.end(subscription, EndReason.PROVIDER_CANCELED, cause));
                yield null;
            }
            case ProviderEvent.Refunded refunded -> {
                refunded(provider, refunded, cause);
                yield null;
            }
            case ProviderEvent.Ignored ignored -> null;
        };
    }

    private @Nullable UUID checkoutCompleted(PaymentProviderCode provider, ProviderEvent.CheckoutCompleted event) {
        Optional<CheckoutSession> session = session(provider, event.checkoutSessionId(), event.providerSessionId());
        UUID userId = session.map(CheckoutSession::userId).orElse(event.userId());
        PlanTierCode tier = session.map(CheckoutSession::tier).orElse(event.tier());
        if (userId == null || tier == null) {
            log.error(
                    "ALERT webhook {} {} has no known user or tier; refund it in the provider console",
                    provider,
                    event.eventId());
            return null;
        }
        Instant start = event.periodStart() == null ? event.paidAt() : event.periodStart();
        Instant end = event.periodEnd() == null ? PlanTier.periodEnd(start) : event.periodEnd();
        PaymentSagaData data = new PaymentSagaData(
                session.map(CheckoutSession::id).orElse(null),
                event.providerPaymentId(),
                event.providerSubscriptionId(),
                event.providerCustomerId(),
                event.amountMinor(),
                event.currency(),
                event.paidAt(),
                start,
                end,
                event.receiptUrl(),
                event.testMode(),
                null,
                null,
                false);
        PaymentSaga saga = sagas.start(userId, tier, provider, data);
        return saga.id();
    }

    private void checkoutFailed(PaymentProviderCode provider, ProviderEvent.CheckoutFailed event, Cause cause) {
        Optional<CheckoutSession> session = session(provider, event.checkoutSessionId(), event.providerSessionId());
        if (session.isEmpty()) {
            log.warn("Declined checkout {} {} matches no session", provider, event.eventId());
            return;
        }
        CheckoutSession declined = session.get().failed(event.reason(), event.message());
        sessions.update(declined);
        UUID paymentId = null;
        if (event.providerPaymentId() != null) {
            paymentId = payments.insertIfAbsent(Payment.failed(
                            UUID.randomUUID(),
                            null,
                            declined.userId(),
                            declined.tier(),
                            provider,
                            event.providerPaymentId(),
                            null,
                            null,
                            event.reason(),
                            declined.testMode(),
                            clock.instant()))
                    .id();
        }
        events.paymentFailed(
                declined.userId(),
                paymentId,
                null,
                declined.tier(),
                provider,
                event.reason(),
                event.message(),
                null,
                clock.instant(),
                cause);
    }

    private void renewalPaid(PaymentProviderCode provider, ProviderEvent.RenewalPaid event, Cause cause) {
        Optional<Subscription> found = subscriptions
                .findByProviderRef(provider, event.providerSubscriptionId())
                .flatMap(subscription -> subscriptions.lockById(subscription.id()));
        if (found.isEmpty() || !found.get().live()) {
            log.warn("Renewal {} {} matches no live subscription", provider, event.eventId());
            return;
        }
        Subscription subscription = found.get();
        Payment candidate = Payment.succeeded(
                UUID.randomUUID(),
                subscription.id(),
                subscription.userId(),
                subscription.tier(),
                provider,
                event.providerPaymentId(),
                event.amountMinor(),
                event.currency(),
                event.paidAt(),
                event.receiptUrl(),
                event.testMode());
        Payment payment = payments.insertIfAbsent(candidate);
        if (!payment.id().equals(candidate.id())) {
            return; // the first payment of the subscription (also reported as an invoice) or a repeat
        }
        boolean wasPastDue = subscription.status() == SubscriptionStatus.PAST_DUE;
        Subscription renewed = subscriptions.save(subscription.renew(event.periodStart(), event.periodEnd()));
        if (renewed.tier() != subscription.tier() && renewed.tier().tutor()) {
            SeatPool pool = seats.load(renewed.id());
            seats.save(
                    pool.resize(entitlements.calculator().tier(renewed.tier()).seats(), UUID::randomUUID));
        }
        events.paymentSucceeded(payment, event.periodStart(), event.periodEnd(), true, cause);
        if (wasPastDue || renewed.tier() != subscription.tier()) {
            events.subscriptionActivated(
                    renewed,
                    renewed.tier().tutor()
                            ? entitlements.calculator().tier(renewed.tier()).seats()
                            : null,
                    clock.instant(),
                    cause);
        }
        entitlements.recalculate(renewed.userId(), cause);
    }

    private void renewalFailed(PaymentProviderCode provider, ProviderEvent.RenewalFailed event, Cause cause) {
        Optional<Subscription> found = subscriptions
                .findByProviderRef(provider, event.providerSubscriptionId())
                .flatMap(subscription -> subscriptions.lockById(subscription.id()));
        if (found.isEmpty()
                || (found.get().status() != SubscriptionStatus.ACTIVE
                        && found.get().status() != SubscriptionStatus.PAST_DUE)) {
            return;
        }
        Subscription pastDue = subscriptions.save(found.get().pastDue(clock.instant(), policy.grace()));
        UUID paymentId = null;
        if (event.providerPaymentId() != null) {
            paymentId = payments.insertIfAbsent(Payment.failed(
                            UUID.randomUUID(),
                            pastDue.id(),
                            pastDue.userId(),
                            pastDue.tier(),
                            provider,
                            event.providerPaymentId(),
                            null,
                            null,
                            event.reason(),
                            pastDue.testMode(),
                            clock.instant()))
                    .id();
        }
        events.paymentFailed(
                pastDue.userId(),
                paymentId,
                pastDue.id(),
                pastDue.tier(),
                provider,
                event.reason(),
                event.message(),
                pastDue.graceUntil(),
                clock.instant(),
                cause);
        entitlements.recalculate(pastDue.userId(), cause);
    }

    private void refunded(PaymentProviderCode provider, ProviderEvent.Refunded event, Cause cause) {
        payments.findByProviderRef(provider, event.providerPaymentId())
                .filter(payment -> payment.status() == PaymentStatus.SUCCEEDED)
                .ifPresent(payment -> {
                    payments.update(payment.refunded());
                    if (payment.subscriptionId() != null) {
                        subscriptions
                                .lockById(payment.subscriptionId())
                                .ifPresent(subscription -> ending.end(subscription, EndReason.REFUNDED, cause));
                    }
                });
    }

    private Optional<CheckoutSession> session(
            PaymentProviderCode provider, @Nullable UUID checkoutSessionId, @Nullable String providerSessionId) {
        Optional<CheckoutSession> byId =
                checkoutSessionId == null ? Optional.empty() : sessions.findById(checkoutSessionId);
        if (byId.isPresent()) {
            return byId;
        }
        return providerSessionId == null
                ? Optional.empty()
                : sessions.findByProviderSession(provider, providerSessionId);
    }
}
