package com.toefly.billing.application.service;

import com.toefly.billing.application.port.in.Caller;
import com.toefly.billing.application.port.in.LifecycleUseCase;
import com.toefly.billing.application.port.out.BillingEventPublisherPort;
import com.toefly.billing.application.port.out.Cause;
import com.toefly.billing.application.port.out.CheckoutSessionRepository;
import com.toefly.billing.application.port.out.EntitlementRepository;
import com.toefly.billing.application.port.out.PaymentRepository;
import com.toefly.billing.application.port.out.SeatRepository;
import com.toefly.billing.application.port.out.SubscriptionRepository;
import com.toefly.billing.application.port.out.TrialRegistryRepository;
import com.toefly.billing.domain.entitlement.EntitlementSet;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.EndReason;
import com.toefly.billing.domain.subscription.Subscription;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LifecycleService implements LifecycleUseCase {

    private static final Logger log = LoggerFactory.getLogger(LifecycleService.class);

    private final SubscriptionRepository subscriptions;
    private final SeatRepository seats;
    private final PaymentRepository payments;
    private final CheckoutSessionRepository sessions;
    private final EntitlementRepository entitlementStore;
    private final TrialRegistryRepository trials;
    private final EntitlementService entitlements;
    private final SubscriptionEnding ending;
    private final BillingEventPublisherPort events;
    private final BillingPolicy policy;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public LifecycleService(
            SubscriptionRepository subscriptions,
            SeatRepository seats,
            PaymentRepository payments,
            CheckoutSessionRepository sessions,
            EntitlementRepository entitlementStore,
            TrialRegistryRepository trials,
            EntitlementService entitlements,
            SubscriptionEnding ending,
            BillingEventPublisherPort events,
            BillingPolicy policy,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.subscriptions = subscriptions;
        this.seats = seats;
        this.payments = payments;
        this.sessions = sessions;
        this.entitlementStore = entitlementStore;
        this.trials = trials;
        this.entitlements = entitlements;
        this.ending = ending;
        this.events = events;
        this.policy = policy;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    @Transactional
    public void profileCreated(UUID userId, Set<Caller.Role> roles, @Nullable UUID correlationId, UUID eventId) {
        Cause cause = new Cause(correlationId, eventId);
        Instant now = clock.instant();
        boolean student = roles.contains(Caller.Role.STUDENT) && !roles.contains(Caller.Role.TUTOR);
        if (student && subscriptions.findByUser(userId).isEmpty()) {
            boolean first = trials.register(identityHash(userId), now, now.minus(policy.trialReuseWindow()));
            if (first) {
                Subscription trial = subscriptions.save(Subscription.trial(
                        UUID.randomUUID(), userId, PlanTierCode.STUDENT_PRO, policy.trialLength(), now));
                events.subscriptionActivated(trial, null, now, cause);
            } else {
                log.info("No second trial for {} (AC-27.02.4)", userId);
            }
        }
        entitlements.recalculate(userId, cause);
    }

    @Override
    @Transactional
    public void aiCheckEvaluated(UUID userId, boolean diagnostic, UUID eventId) {
        if (diagnostic) {
            return; // docs/02 §1.3 rule 2: the diagnostic needs no right and no limit
        }
        EntitlementSet set = entitlements.calculate(userId, clock.instant());
        boolean free =
                set.grants().stream().anyMatch(grant -> grant.entitlement().aiCheck() && grant.limited());
        if (free) {
            entitlementStore.countFreeCheck(userId, clock.instant());
            entitlements.recalculate(userId, new Cause(null, eventId));
        }
    }

    @Override
    @Transactional
    public void accountDeletionRequested(UUID userId) {
        Cause cause = Cause.none();
        subscriptions.lockLive(userId).ifPresent(live -> ending.end(live, EndReason.ACCOUNT_DELETED, cause));
        seats.findByStudent(userId)
                .ifPresent(holding ->
                        seats.save(seats.load(holding.subscriptionId()).release(userId)));
        payments.anonymize(userId);
        sessions.deleteByUser(userId);
        subscriptions.deleteByUser(userId);
        entitlementStore.deleteUser(userId);
        events.entitlementsErased(userId);
    }

    @Override
    public int runTimers() {
        Instant now = clock.instant();
        int handled = 0;
        for (UUID id : subscriptions.findTimerCandidates(now.plus(policy.trialEndingNotice()), 200)) {
            Boolean changed = transactions.execute(status -> subscriptions
                    .lockById(id)
                    .map(subscription -> timer(subscription, clock.instant()))
                    .orElse(false));
            if (Boolean.TRUE.equals(changed)) {
                handled++;
            }
        }
        return handled;
    }

    private boolean timer(Subscription subscription, Instant now) {
        Cause cause = Cause.none();
        var end = subscription.dueEnd(now);
        if (end.isPresent()) {
            ending.end(subscription, end.get(), cause);
            return true;
        }
        if (subscription.trialEndingSoon(now, policy.trialEndingNotice())) {
            Subscription notified = subscriptions.save(subscription.markTrialEndingNotified());
            events.trialEnding(notified, now, cause);
            return true;
        }
        return false;
    }

    /** HMAC of the identity; irreversible without the pepper (AC-27.02.4). */
    String identityHash(UUID userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(policy.identityPepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(userId.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
