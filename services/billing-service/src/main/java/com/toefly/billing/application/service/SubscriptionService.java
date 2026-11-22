package com.toefly.billing.application.service;

import com.toefly.billing.application.port.in.AdminBillingUseCase;
import com.toefly.billing.application.port.in.BillingException;
import com.toefly.billing.application.port.in.Caller;
import com.toefly.billing.application.port.in.EntitlementView;
import com.toefly.billing.application.port.in.SubscriptionUseCase;
import com.toefly.billing.application.port.in.SubscriptionView;
import com.toefly.billing.application.port.out.BillingEventPublisherPort;
import com.toefly.billing.application.port.out.Cause;
import com.toefly.billing.application.port.out.ClassroomPort;
import com.toefly.billing.application.port.out.PaymentProvider;
import com.toefly.billing.application.port.out.PaymentRepository;
import com.toefly.billing.application.port.out.SagaRepository;
import com.toefly.billing.application.port.out.SeatRepository;
import com.toefly.billing.application.port.out.SubscriptionRepository;
import com.toefly.billing.domain.entitlement.EntitlementSet;
import com.toefly.billing.domain.entitlement.EntitlementSource;
import com.toefly.billing.domain.payment.Payment;
import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.saga.PaymentSaga;
import com.toefly.billing.domain.seat.SeatPool;
import com.toefly.billing.domain.seat.SeatsInUseException;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.Subscription;
import com.toefly.billing.domain.subscription.SubscriptionStateException;
import com.toefly.billing.domain.subscription.SubscriptionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Billing screen: the subscription, cancel and resume, tutor plan changes, payments; admin listing and grants. */
@Service
public class SubscriptionService implements SubscriptionUseCase, AdminBillingUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptions;
    private final SeatRepository seats;
    private final PaymentRepository payments;
    private final SagaRepository sagas;
    private final ClassroomPort classroom;
    private final PaymentProviders providers;
    private final EntitlementService entitlements;
    private final BillingEventPublisherPort events;
    private final Clock clock;

    public SubscriptionService(
            SubscriptionRepository subscriptions,
            SeatRepository seats,
            PaymentRepository payments,
            SagaRepository sagas,
            ClassroomPort classroom,
            PaymentProviders providers,
            EntitlementService entitlements,
            BillingEventPublisherPort events,
            Clock clock) {
        this.subscriptions = subscriptions;
        this.seats = seats;
        this.payments = payments;
        this.sagas = sagas;
        this.classroom = classroom;
        this.providers = providers;
        this.entitlements = entitlements;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionView current(Caller caller) {
        return view(caller.userId());
    }

    SubscriptionView view(UUID userId) {
        Instant now = clock.instant();
        EntitlementSet set = entitlements.calculate(userId, now);
        Subscription own = subscriptions.findLive(userId).orElse(null);
        SeatPool pool = own != null && own.tier().tutor() ? seats.load(own.id()) : null;
        EntitlementSource source = null;
        if (own != null && own.grantsAccess(now)) {
            source = own.trial()
                    ? EntitlementSource.TRIAL
                    : own.provider() == PaymentProviderCode.MANUAL
                            ? EntitlementSource.MANUAL
                            : EntitlementSource.SUBSCRIPTION;
        } else if (set.tutorId() != null) {
            source = EntitlementSource.SEAT;
        }
        var saga = own == null || own.provider() == null || own.trial()
                ? null
                : payments.findByUser(userId).stream()
                        .filter(payment -> own.id().equals(payment.subscriptionId()))
                        .findFirst()
                        .flatMap(payment -> sagas.findByCorrelation(
                                PaymentSagaService.correlation(payment.provider(), payment.providerPaymentId())))
                        .map(PaymentSaga::status)
                        .orElse(null);
        return new SubscriptionView(set.planTierCode(), source, own, set.tutorId(), pool, saga);
    }

    @Override
    @Transactional
    public SubscriptionView cancel(Caller caller) {
        Subscription subscription = lockLive(caller.userId());
        Subscription canceled = subscription.cancelAtPeriodEnd(clock.instant());
        provider(subscription).ifPresent(provider -> provider.cancelAtPeriodEnd(ref(subscription)));
        subscriptions.save(canceled);
        entitlements.recalculate(caller.userId(), Cause.none());
        return view(caller.userId());
    }

    @Override
    @Transactional
    public SubscriptionView resume(Caller caller) {
        Subscription subscription = lockLive(caller.userId());
        Subscription resumed = subscription.resume(clock.instant());
        provider(subscription).ifPresent(provider -> provider.resume(ref(subscription)));
        subscriptions.save(resumed);
        entitlements.recalculate(caller.userId(), Cause.none());
        return view(caller.userId());
    }

    @Override
    @Transactional
    public SubscriptionView changePlan(Caller caller, PlanTierCode target) {
        if (!caller.is(Caller.Role.TUTOR) || !target.tutor()) {
            throw BillingException.forbidden("Only a tutor changes between TUTOR and TUTOR_PLUS.");
        }
        Subscription subscription = lockLive(caller.userId());
        if (!subscription.tier().tutor() || subscription.status() != SubscriptionStatus.ACTIVE) {
            throw BillingException.conflict("no-subscription", "There is no active tutor subscription.");
        }
        if (target == subscription.tier()) {
            return view(caller.userId());
        }
        PlanTier next = entitlements.calculator().tier(target);
        SeatPool pool = seats.load(subscription.id());
        boolean upgrade = next.seats() > pool.limit();
        Cause cause = Cause.none();
        if (upgrade) {
            provider(subscription).ifPresent(provider -> provider.changePlan(ref(subscription), next, true));
            seats.save(pool.resize(next.seats(), UUID::randomUUID));
            Subscription changed = subscriptions.save(subscription.changeTierNow(target));
            events.subscriptionActivated(changed, next.seats(), clock.instant(), cause);
            try {
                classroom.putWorkspace(caller.userId(), true, next.seats(), target);
            } catch (RuntimeException unavailable) {
                log.warn(
                        "Workspace of {} keeps the old seat limit until subscription-activated: {}",
                        caller.userId(),
                        unavailable.getMessage());
            }
        } else {
            try {
                pool.requireFits(next.seats());
            } catch (SeatsInUseException crowded) {
                throw BillingException.conflict(
                        "seats-in-use", "Remove students to " + next.seats() + " or fewer to switch to Tutor.");
            }
            provider(subscription).ifPresent(provider -> provider.changePlan(ref(subscription), next, false));
            subscriptions.save(subscription.scheduleTier(target));
        }
        entitlements.recalculate(caller.userId(), cause);
        return view(caller.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> payments(Caller caller) {
        return payments.findByUser(caller.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPage subscriptions(
            @Nullable SubscriptionStatus status,
            @Nullable PlanTierCode tier,
            @Nullable PaymentProviderCode provider,
            @Nullable Instant beforeCreatedAt,
            @Nullable UUID beforeId,
            int limit) {
        SubscriptionRepository.Page page = subscriptions.page(status, tier, provider, beforeCreatedAt, beforeId, limit);
        return new SubscriptionPage(page.items(), page.more());
    }

    @Override
    @Transactional
    public EntitlementView grant(UUID userId, PlanTierCode tier, Instant until, String reason) {
        Instant now = clock.instant();
        if (!until.isAfter(now) || tier == PlanTierCode.STUDENT_FREE) {
            throw BillingException.invalid("A grant needs a paid tier and a date in the future.");
        }
        Optional<Subscription> live = subscriptions.lockLive(userId);
        if (live.isPresent() && live.get().status() != SubscriptionStatus.TRIALING) {
            throw BillingException.conflict("subscription-exists", "The user already has a subscription.");
        }
        live.ifPresent(trial ->
                subscriptions.save(trial.expire(now, com.toefly.billing.domain.subscription.EndReason.GRANT_ENDED)));
        Subscription granted = subscriptions.save(Subscription.paid(
                UUID.randomUUID(), userId, tier, PaymentProviderCode.MANUAL, null, null, now, until, false, now));
        log.info("Manual grant of {} to {} until {}: {}", tier, userId, until, reason);
        Integer seatLimit = null;
        if (tier.tutor()) {
            PlanTier plan = entitlements.calculator().tier(tier);
            seats.save(seats.load(granted.id()).resize(plan.seats(), UUID::randomUUID));
            seatLimit = plan.seats();
        }
        events.subscriptionActivated(granted, seatLimit, now, Cause.none());
        return entitlements.recalculate(userId, Cause.none());
    }

    private Subscription lockLive(UUID userId) {
        Subscription subscription = subscriptions.lockLive(userId).orElseThrow(EntitlementService::noSubscription);
        if (subscription.status() == SubscriptionStatus.TRIALING) {
            throw BillingException.conflict("no-subscription", "A trial has nothing to cancel or change.");
        }
        return subscription;
    }

    private Optional<PaymentProvider> provider(Subscription subscription) {
        if (subscription.provider() == null
                || subscription.provider() == PaymentProviderCode.MANUAL
                || subscription.providerSubscriptionId() == null) {
            return Optional.empty();
        }
        return providers.find(subscription.provider());
    }

    private static String ref(Subscription subscription) {
        if (subscription.providerSubscriptionId() == null) {
            throw new SubscriptionStateException("conflict", "The subscription has no provider reference.");
        }
        return subscription.providerSubscriptionId();
    }
}
