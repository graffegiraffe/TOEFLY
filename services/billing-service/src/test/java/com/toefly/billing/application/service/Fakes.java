package com.toefly.billing.application.service;

import com.toefly.billing.application.port.out.BillingEventPublisherPort;
import com.toefly.billing.application.port.out.Cause;
import com.toefly.billing.application.port.out.CheckoutSessionRepository;
import com.toefly.billing.application.port.out.ClassroomPort;
import com.toefly.billing.application.port.out.EntitlementRepository;
import com.toefly.billing.application.port.out.PaymentProvider;
import com.toefly.billing.application.port.out.PaymentRepository;
import com.toefly.billing.application.port.out.PlanTierRepository;
import com.toefly.billing.application.port.out.ProviderEvent;
import com.toefly.billing.application.port.out.SagaRepository;
import com.toefly.billing.application.port.out.SeatRepository;
import com.toefly.billing.application.port.out.SubscriptionRepository;
import com.toefly.billing.application.port.out.TrialRegistryRepository;
import com.toefly.billing.application.port.out.WebhookEventRepository;
import com.toefly.billing.domain.checkout.CheckoutSession;
import com.toefly.billing.domain.entitlement.EntitlementCalculatorTest;
import com.toefly.billing.domain.entitlement.EntitlementSet;
import com.toefly.billing.domain.payment.Payment;
import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.saga.PaymentSaga;
import com.toefly.billing.domain.saga.SagaStatus;
import com.toefly.billing.domain.seat.Seat;
import com.toefly.billing.domain.seat.SeatPool;
import com.toefly.billing.domain.subscription.EndReason;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.Subscription;
import com.toefly.billing.domain.subscription.SubscriptionStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** In-memory adapters; {@link Faults} makes a named operation throw to test the saga compensations. */
final class Fakes {

    private Fakes() {}

    static final class Faults {
        final Set<String> failing = new HashSet<>();

        void check(String operation) {
            if (failing.contains(operation)) {
                throw new IllegalStateException("injected failure of " + operation);
            }
        }
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-21T10:00:00Z");

        void shift(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final class NoTransactions extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }

    static final class Tiers implements PlanTierRepository {
        @Override
        public List<PlanTier> findAll() {
            return EntitlementCalculatorTest.tiers();
        }
    }

    static final class Subscriptions implements SubscriptionRepository {
        final Map<UUID, Subscription> rows = new LinkedHashMap<>();
        final Faults faults;

        Subscriptions(Faults faults) {
            this.faults = faults;
        }

        @Override
        public Optional<Subscription> findLive(UUID userId) {
            return rows.values().stream()
                    .filter(s -> s.userId().equals(userId) && s.live())
                    .findFirst();
        }

        @Override
        public Optional<Subscription> lockLive(UUID userId) {
            faults.check("subscriptions.lockLive");
            return findLive(userId);
        }

        @Override
        public Optional<Subscription> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<Subscription> lockById(UUID id) {
            return findById(id);
        }

        @Override
        public Optional<Subscription> findByProviderRef(PaymentProviderCode provider, String ref) {
            return rows.values().stream()
                    .filter(s -> s.provider() == provider && ref.equals(s.providerSubscriptionId()))
                    .findFirst();
        }

        @Override
        public List<Subscription> findByUser(UUID userId) {
            return rows.values().stream().filter(s -> s.userId().equals(userId)).toList();
        }

        @Override
        public Subscription save(Subscription subscription) {
            faults.check("subscriptions.save");
            if (subscription.live()
                    && findLive(subscription.userId())
                            .filter(other -> !other.id().equals(subscription.id()))
                            .isPresent()) {
                throw new IllegalStateException("INV-BIL-01 violated");
            }
            rows.put(subscription.id(), subscription);
            return subscription;
        }

        @Override
        public List<UUID> findTimerCandidates(Instant horizon, int limit) {
            return rows.values().stream()
                    .filter(Subscription::live)
                    .map(Subscription::id)
                    .toList();
        }

        @Override
        public Page page(
                @Nullable SubscriptionStatus status,
                @Nullable PlanTierCode tier,
                @Nullable PaymentProviderCode provider,
                @Nullable Instant beforeCreatedAt,
                @Nullable UUID beforeId,
                int limit) {
            return new Page(List.copyOf(rows.values()), false);
        }

        @Override
        public void deleteByUser(UUID userId) {
            rows.values().removeIf(s -> s.userId().equals(userId));
        }
    }

    static final class Seats implements SeatRepository {
        final Map<UUID, SeatPool> pools = new HashMap<>();
        final Map<UUID, UUID> owners = new HashMap<>();
        final Faults faults;
        final Subscriptions subscriptions;

        Seats(Faults faults, Subscriptions subscriptions) {
            this.faults = faults;
            this.subscriptions = subscriptions;
        }

        @Override
        public SeatPool load(UUID subscriptionId) {
            faults.check("seats.load");
            return pools.getOrDefault(subscriptionId, SeatPool.empty(subscriptionId));
        }

        @Override
        public void save(SeatPool pool) {
            pools.put(pool.subscriptionId(), pool);
        }

        @Override
        public Optional<Holding> findByStudent(UUID studentId) {
            for (SeatPool pool : pools.values()) {
                for (Seat seat : pool.seats()) {
                    if (studentId.equals(seat.studentId())) {
                        return Optional.of(new Holding(
                                pool.subscriptionId(),
                                subscriptions.rows.get(pool.subscriptionId()).userId()));
                    }
                }
            }
            return Optional.empty();
        }
    }

    static final class Payments implements PaymentRepository {
        final Map<UUID, Payment> rows = new LinkedHashMap<>();
        final Faults faults;

        Payments(Faults faults) {
            this.faults = faults;
        }

        @Override
        public Payment insertIfAbsent(Payment payment) {
            faults.check("payments.insert");
            return findByProviderRef(payment.provider(), payment.providerPaymentId())
                    .orElseGet(() -> {
                        rows.put(payment.id(), payment);
                        return payment;
                    });
        }

        @Override
        public void update(Payment payment) {
            rows.put(payment.id(), payment);
        }

        @Override
        public Optional<Payment> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<Payment> findByProviderRef(PaymentProviderCode provider, String providerPaymentId) {
            return rows.values().stream()
                    .filter(p ->
                            p.provider() == provider && p.providerPaymentId().equals(providerPaymentId))
                    .findFirst();
        }

        @Override
        public List<Payment> findByUser(UUID userId) {
            return rows.values().stream()
                    .filter(p -> userId.equals(p.userId()))
                    .sorted(Comparator.comparing(Payment::createdAt).reversed())
                    .toList();
        }

        @Override
        public void anonymize(UUID userId) {
            rows.replaceAll((id, p) -> userId.equals(p.userId()) ? p.anonymized() : p);
        }
    }

    static final class Sessions implements CheckoutSessionRepository {
        final Map<UUID, CheckoutSession> rows = new HashMap<>();

        @Override
        public boolean insert(CheckoutSession session) {
            if (findByKey(session.userId(), session.idempotencyKey()).isPresent()) {
                return false;
            }
            rows.put(session.id(), session);
            return true;
        }

        @Override
        public void update(CheckoutSession session) {
            rows.put(session.id(), session);
        }

        @Override
        public Optional<CheckoutSession> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<CheckoutSession> findByKey(UUID userId, String key) {
            return rows.values().stream()
                    .filter(s -> s.userId().equals(userId) && s.idempotencyKey().equals(key))
                    .findFirst();
        }

        @Override
        public Optional<CheckoutSession> findByProviderSession(PaymentProviderCode provider, String providerSessionId) {
            return rows.values().stream()
                    .filter(s -> s.provider() == provider && providerSessionId.equals(s.providerSessionId()))
                    .findFirst();
        }

        @Override
        public void deleteByUser(UUID userId) {
            rows.values().removeIf(s -> s.userId().equals(userId));
        }
    }

    static final class Entitlements implements EntitlementRepository {
        final Map<UUID, Snapshot> snapshots = new HashMap<>();
        final Map<UUID, EntitlementSet> sets = new HashMap<>();
        final Map<UUID, Integer> free = new HashMap<>();

        @Override
        public Optional<Snapshot> lockSnapshot(UUID userId) {
            return findSnapshot(userId);
        }

        @Override
        public Optional<Snapshot> findSnapshot(UUID userId) {
            return Optional.ofNullable(snapshots.get(userId));
        }

        @Override
        public void store(EntitlementSet set, long version, Instant changedAt) {
            snapshots.put(set.userId(), new Snapshot(version, set.fingerprint(), changedAt));
            sets.put(set.userId(), set);
        }

        @Override
        public int freeUsed(UUID userId) {
            return free.getOrDefault(userId, 0);
        }

        @Override
        public void countFreeCheck(UUID userId, Instant now) {
            free.merge(userId, 1, Integer::sum);
        }

        @Override
        public void deleteUser(UUID userId) {
            snapshots.remove(userId);
            sets.remove(userId);
            free.remove(userId);
        }
    }

    static final class Trials implements TrialRegistryRepository {
        final Map<String, Instant> rows = new HashMap<>();

        @Override
        public boolean register(String identityHash, Instant now, Instant since) {
            Instant earlier = rows.get(identityHash);
            if (earlier != null && earlier.isAfter(since)) {
                return false;
            }
            rows.put(identityHash, now);
            return true;
        }
    }

    static final class Webhooks implements WebhookEventRepository {
        final Map<String, Stored> rows = new LinkedHashMap<>();
        final Set<String> processed = new HashSet<>();

        @Override
        public boolean insertIfAbsent(PaymentProviderCode provider, ProviderEvent event, Instant receivedAt) {
            return rows.putIfAbsent(provider + ":" + event.eventId(), new Stored(provider, event, 0)) == null;
        }

        @Override
        public List<Stored> lockPending(int limit, Instant now) {
            return rows.entrySet().stream()
                    .filter(e -> !processed.contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();
        }

        @Override
        public Optional<Stored> lock(PaymentProviderCode provider, String eventId) {
            String key = provider + ":" + eventId;
            return processed.contains(key) ? Optional.empty() : Optional.ofNullable(rows.get(key));
        }

        @Override
        public void markProcessed(PaymentProviderCode provider, String eventId, Instant now) {
            processed.add(provider + ":" + eventId);
        }

        @Override
        public void markFailed(PaymentProviderCode provider, String eventId, String error, Instant retryAfter) {}
    }

    static final class Sagas implements SagaRepository {
        final Map<UUID, PaymentSaga> rows = new LinkedHashMap<>();

        @Override
        public boolean insertIfAbsent(PaymentSaga saga) {
            if (findByCorrelation(saga.correlationId()).isPresent()) {
                return false;
            }
            rows.put(saga.id(), saga);
            return true;
        }

        @Override
        public void update(PaymentSaga saga) {
            rows.put(saga.id(), saga);
        }

        @Override
        public Optional<PaymentSaga> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<PaymentSaga> findByCorrelation(UUID correlationId) {
            return rows.values().stream()
                    .filter(s -> s.correlationId().equals(correlationId))
                    .findFirst();
        }

        @Override
        public Optional<PaymentSaga> lockDue(Instant now) {
            return rows.values().stream().filter(s -> s.dueAt(now)).findFirst();
        }

        @Override
        public Optional<PaymentSaga> lock(UUID id) {
            return findById(id);
        }

        @Override
        public Page page(
                @Nullable SagaStatus status, @Nullable Instant beforeUpdatedAt, @Nullable UUID beforeId, int limit) {
            return new Page(List.copyOf(rows.values()), false);
        }
    }

    /** Records events by topic-like names. */
    static final class Events implements BillingEventPublisherPort {
        final List<String> log = new ArrayList<>();
        final Map<UUID, Long> versions = new HashMap<>();
        final List<EndReason> endReasons = new ArrayList<>();
        final Faults faults;

        Events(Faults faults) {
            this.faults = faults;
        }

        long count(String name) {
            return log.stream().filter(name::equals).count();
        }

        @Override
        public List<String> topics() {
            return List.of();
        }

        @Override
        public void subscriptionActivated(Subscription s, @Nullable Integer seatLimit, Instant at, Cause cause) {
            faults.check("events.subscriptionActivated");
            log.add("subscription-activated");
        }

        @Override
        public void subscriptionCanceled(Subscription s, EndReason reason, Instant at, Cause cause) {
            log.add("subscription-canceled");
            endReasons.add(reason);
        }

        @Override
        public void entitlementsChanged(EntitlementSet set, long version, Instant changedAt, Cause cause) {
            Long previous = versions.put(set.userId(), version);
            if (previous != null && previous >= version) {
                throw new IllegalStateException("entitlementsVersion must grow");
            }
            log.add("entitlements-changed");
        }

        @Override
        public void entitlementsErased(UUID userId) {
            versions.remove(userId); // consumers drop the projection on the tombstone
            log.add("entitlements-tombstone");
        }

        @Override
        public void paymentSucceeded(Payment payment, Instant start, Instant end, boolean renewal, Cause cause) {
            log.add(renewal ? "payment-succeeded:renewal" : "payment-succeeded");
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
            log.add("payment-failed:" + reason);
        }

        @Override
        public void trialEnding(Subscription trial, Instant at, Cause cause) {
            log.add("trial-ending");
        }
    }

    static final class Classroom implements ClassroomPort {
        final Faults faults;
        final Map<UUID, Boolean> workspaces = new HashMap<>();
        final Map<UUID, List<UUID>> students = new HashMap<>();

        Classroom(Faults faults) {
            this.faults = faults;
        }

        @Override
        public void putWorkspace(UUID tutorId, boolean active, int seatLimit, PlanTierCode tier) {
            faults.check("classroom.putWorkspace");
            workspaces.put(tutorId, active);
        }

        @Override
        public List<UUID> enrollments(UUID tutorId) {
            faults.check("classroom.enrollments");
            return students.getOrDefault(tutorId, List.of());
        }
    }

    static final class Provider implements PaymentProvider {
        final PaymentProviderCode code;
        final List<String> refunds = new ArrayList<>();
        final List<String> calls = new ArrayList<>();

        Provider(PaymentProviderCode code) {
            this.code = code;
        }

        @Override
        public PaymentProviderCode code() {
            return code;
        }

        @Override
        public boolean testMode() {
            return true;
        }

        @Override
        public Checkout createCheckout(CheckoutRequest request) {
            calls.add("checkout:" + request.tier().code());
            return new Checkout(
                    "cs_" + request.checkoutSessionId(),
                    "https://checkout.example.test/" + request.checkoutSessionId(),
                    Instant.parse("2026-11-21T11:00:00Z"));
        }

        @Override
        public ProviderEvent parseWebhook(Map<String, String> headers, byte[] body) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void refund(String providerPaymentId, @Nullable Integer amountMinor, @Nullable String subscription) {
            refunds.add(providerPaymentId);
        }

        @Override
        public void cancelAtPeriodEnd(String providerSubscriptionId) {
            calls.add("cancel:" + providerSubscriptionId);
        }

        @Override
        public void resume(String providerSubscriptionId) {
            calls.add("resume:" + providerSubscriptionId);
        }

        @Override
        public void changePlan(String providerSubscriptionId, PlanTier target, boolean now) {
            calls.add("change:" + target.code() + ":" + now);
        }

        @Override
        public @Nullable String portalUrl(String providerCustomerId, String returnUrl) {
            return "https://billing.example.test/" + providerCustomerId;
        }
    }
}
