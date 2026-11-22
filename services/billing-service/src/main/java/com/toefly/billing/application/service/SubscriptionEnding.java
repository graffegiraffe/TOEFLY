package com.toefly.billing.application.service;

import com.toefly.billing.application.port.out.BillingEventPublisherPort;
import com.toefly.billing.application.port.out.Cause;
import com.toefly.billing.application.port.out.ClassroomPort;
import com.toefly.billing.application.port.out.SeatRepository;
import com.toefly.billing.application.port.out.SubscriptionRepository;
import com.toefly.billing.domain.seat.SeatPool;
import com.toefly.billing.domain.subscription.EndReason;
import com.toefly.billing.domain.subscription.Subscription;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ends a subscription: EXPIRED, {@code subscription-canceled}, rights recalculated. A tutor plan also frees its seats,
 * takes the seat rights from the students and suspends the workspace (INV-BIL-07, AC-26.03.1).
 */
@Component
public class SubscriptionEnding {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEnding.class);

    private final SubscriptionRepository subscriptions;
    private final SeatRepository seats;
    private final ClassroomPort classroom;
    private final EntitlementService entitlements;
    private final BillingEventPublisherPort events;
    private final Clock clock;

    public SubscriptionEnding(
            SubscriptionRepository subscriptions,
            SeatRepository seats,
            ClassroomPort classroom,
            EntitlementService entitlements,
            BillingEventPublisherPort events,
            Clock clock) {
        this.subscriptions = subscriptions;
        this.seats = seats;
        this.classroom = classroom;
        this.entitlements = entitlements;
        this.events = events;
        this.clock = clock;
    }

    /** Runs in the caller's transaction. */
    public Subscription end(Subscription subscription, EndReason reason, Cause cause) {
        if (!subscription.live()) {
            return subscription;
        }
        Instant now = clock.instant();
        Subscription ended = subscriptions.save(subscription.expire(now, reason));
        events.subscriptionCanceled(ended, reason, now, cause);
        if (ended.tier().tutor()) {
            releaseSeats(ended, cause);
            try {
                classroom.putWorkspace(ended.userId(), false, 0, ended.tier());
            } catch (RuntimeException unavailable) {
                // The workspace follows subscription-canceled too; the call only makes it immediate.
                log.warn("Workspace of tutor {} not suspended now: {}", ended.userId(), unavailable.getMessage());
            }
        }
        entitlements.recalculate(ended.userId(), cause);
        return ended;
    }

    /** Frees every seat of a tutor subscription and recalculates the rights of its students. */
    public void releaseSeats(Subscription tutorSubscription, Cause cause) {
        SeatPool pool = seats.load(tutorSubscription.id());
        List<UUID> students = pool.students();
        seats.save(pool.releaseAll().resize(0, UUID::randomUUID));
        students.forEach(student -> entitlements.recalculate(student, cause));
    }
}
