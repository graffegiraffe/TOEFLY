package com.toefly.billing.application.service;

import com.toefly.billing.application.port.in.BillingException;
import com.toefly.billing.application.port.in.EntitlementQueryUseCase;
import com.toefly.billing.application.port.in.EntitlementView;
import com.toefly.billing.application.port.out.BillingEventPublisherPort;
import com.toefly.billing.application.port.out.Cause;
import com.toefly.billing.application.port.out.EntitlementRepository;
import com.toefly.billing.application.port.out.PaymentRepository;
import com.toefly.billing.application.port.out.PlanTierRepository;
import com.toefly.billing.application.port.out.SeatRepository;
import com.toefly.billing.application.port.out.SubscriptionRepository;
import com.toefly.billing.domain.entitlement.EntitlementCalculator;
import com.toefly.billing.domain.entitlement.EntitlementSet;
import com.toefly.billing.domain.seat.SeatPool;
import com.toefly.billing.domain.subscription.Subscription;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recalculates rights from all sources and publishes a new snapshot only when they changed (INV-BIL-04); every change
 * of a subscription, seat, trial or Free usage ends here.
 */
@Service
public class EntitlementService implements EntitlementQueryUseCase {

    private final SubscriptionRepository subscriptions;
    private final SeatRepository seats;
    private final PaymentRepository payments;
    private final EntitlementRepository entitlements;
    private final BillingEventPublisherPort events;
    private final EntitlementCalculator calculator;
    private final Clock clock;

    public EntitlementService(
            SubscriptionRepository subscriptions,
            SeatRepository seats,
            PaymentRepository payments,
            EntitlementRepository entitlements,
            PlanTierRepository tiers,
            BillingEventPublisherPort events,
            Clock clock) {
        this.subscriptions = subscriptions;
        this.seats = seats;
        this.payments = payments;
        this.entitlements = entitlements;
        this.events = events;
        this.calculator = new EntitlementCalculator(tiers.findAll());
        this.clock = clock;
    }

    public EntitlementCalculator calculator() {
        return calculator;
    }

    /** Must run inside the caller's transaction, after its changes. */
    public EntitlementView recalculate(UUID userId, Cause cause) {
        Instant now = clock.instant();
        Optional<EntitlementRepository.Snapshot> previous = entitlements.lockSnapshot(userId);
        EntitlementSet set = calculate(userId, now);
        if (previous.isPresent() && previous.get().fingerprint().equals(set.fingerprint())) {
            return new EntitlementView(
                    set, previous.get().version(), previous.get().changedAt());
        }
        long version = previous.map(snapshot -> snapshot.version() + 1).orElse(1L);
        entitlements.store(set, version, now);
        events.entitlementsChanged(set, version, now, cause);
        return new EntitlementView(set, version, now);
    }

    EntitlementSet calculate(UUID userId, Instant now) {
        Subscription own = subscriptions.findLive(userId).orElse(null);
        return calculator.calculate(userId, own, seatOf(userId, now), entitlements.freeUsed(userId), now);
    }

    private EntitlementCalculator.@Nullable SeatHolding seatOf(UUID userId, Instant now) {
        return seats.findByStudent(userId)
                .map(holding -> new EntitlementCalculator.SeatHolding(
                        holding.tutorId(),
                        holding.subscriptionId(),
                        subscriptions
                                .findById(holding.subscriptionId())
                                .map(subscription -> subscription.grantsAccess(now))
                                .orElse(false)))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public EntitlementView entitlements(UUID userId) {
        Instant now = clock.instant();
        EntitlementSet set = calculate(userId, now);
        return entitlements
                .findSnapshot(userId)
                .map(snapshot -> new EntitlementView(set, snapshot.version(), snapshot.changedAt()))
                .orElse(new EntitlementView(set, 0, now));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EntitlementView> snapshot(UUID userId) {
        return entitlements.findSnapshot(userId).map(snapshot -> entitlements(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public TutorSeats tutorSeats(UUID tutorId) {
        Instant now = clock.instant();
        return subscriptions
                .findLive(tutorId)
                .filter(subscription -> subscription.tier().tutor() && subscription.grantsAccess(now))
                .map(subscription -> {
                    SeatPool pool = seats.load(subscription.id());
                    return new TutorSeats(tutorId, subscription.tier(), pool.limit(), pool.used());
                })
                .orElse(new TutorSeats(tutorId, null, 0, 0));
    }

    @Override
    @Transactional(readOnly = true)
    public Export export(UUID userId) {
        return new Export(userId, subscriptions.findByUser(userId), payments.findByUser(userId), entitlements(userId));
    }

    static BillingException noSubscription() {
        return BillingException.conflict("no-subscription", "There is no subscription to change.");
    }
}
