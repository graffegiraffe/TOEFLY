package com.toefly.billing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.billing.application.port.in.BillingException;
import com.toefly.billing.application.port.in.Caller;
import com.toefly.billing.application.port.in.CheckoutUseCase;
import com.toefly.billing.application.port.out.ProviderEvent;
import com.toefly.billing.domain.checkout.CheckoutSession;
import com.toefly.billing.domain.checkout.CheckoutStatus;
import com.toefly.billing.domain.entitlement.Entitlement;
import com.toefly.billing.domain.payment.Payment;
import com.toefly.billing.domain.payment.PaymentStatus;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.saga.PaymentSaga;
import com.toefly.billing.domain.saga.SagaStatus;
import com.toefly.billing.domain.subscription.EndReason;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.Subscription;
import com.toefly.billing.domain.subscription.SubscriptionStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The application services over in-memory adapters, by acceptance criteria of EP-27 and the DoD of phase 11a. */
class BillingScenarioTest {

    static final String RETURN = "http://app.toefly.localhost:5173/billing/checkout/result";

    final Fakes.Faults faults = new Fakes.Faults();
    final Fakes.MutableClock clock = new Fakes.MutableClock();
    final Fakes.Subscriptions subscriptions = new Fakes.Subscriptions(faults);
    final Fakes.Seats seats = new Fakes.Seats(faults, subscriptions);
    final Fakes.Payments payments = new Fakes.Payments(faults);
    final Fakes.Sessions sessions = new Fakes.Sessions();
    final Fakes.Entitlements entitlementStore = new Fakes.Entitlements();
    final Fakes.Trials trials = new Fakes.Trials();
    final Fakes.Webhooks webhookStore = new Fakes.Webhooks();
    final Fakes.Sagas sagaStore = new Fakes.Sagas();
    final Fakes.Events events = new Fakes.Events(faults);
    final Fakes.Classroom classroom = new Fakes.Classroom(faults);
    final Fakes.Provider stripe = new Fakes.Provider(PaymentProviderCode.STRIPE);
    final Fakes.NoTransactions tx = new Fakes.NoTransactions();
    final BillingPolicy policy = new BillingPolicy(
            Duration.ofDays(7),
            Duration.ofHours(48),
            Duration.ofDays(7),
            Duration.ofDays(365),
            Duration.ofHours(1),
            new com.toefly.billing.domain.saga.RetryPolicy(5, Duration.ofSeconds(1), Duration.ofMinutes(1)),
            List.of("http://app.toefly.localhost:5173/billing"),
            List.of(PaymentProviderCode.STRIPE, PaymentProviderCode.BEPAID),
            java.util.Map.of("app.toefly.pro.monthly", PlanTierCode.STUDENT_PRO),
            "pepper");
    final PaymentProviders providers = new PaymentProviders(List.of(stripe));
    final EntitlementService entitlements =
            new EntitlementService(subscriptions, seats, payments, entitlementStore, new Fakes.Tiers(), events, clock);
    final SubscriptionEnding ending =
            new SubscriptionEnding(subscriptions, seats, classroom, entitlements, events, clock);
    final PaymentSagaSteps steps = new PaymentSagaSteps(
            payments, subscriptions, seats, sessions, classroom, providers, entitlements, ending, events, clock);
    final PaymentSagaService sagas =
            new PaymentSagaService(sagaStore, sessions, steps, policy, tx, new SimpleMeterRegistry(), clock);
    final WebhookService webhooks = new WebhookService(
            providers,
            webhookStore,
            sessions,
            subscriptions,
            payments,
            seats,
            sagas,
            entitlements,
            ending,
            events,
            policy,
            tx,
            clock);
    final CheckoutService checkout =
            new CheckoutService(providers, sessions, subscriptions, entitlements, policy, tx, clock);
    final SubscriptionService subscriptionService = new SubscriptionService(
            subscriptions, seats, payments, sagaStore, classroom, providers, entitlements, events, clock);
    final LifecycleService lifecycle = new LifecycleService(
            subscriptions,
            seats,
            payments,
            sessions,
            entitlementStore,
            trials,
            entitlements,
            ending,
            events,
            policy,
            tx,
            clock);

    final UUID student = UUID.randomUUID();
    final UUID tutor = UUID.randomUUID();

    Caller studentCaller() {
        return new Caller(student, Set.of(Caller.Role.STUDENT), "s@example.test");
    }

    Caller tutorCaller() {
        return new Caller(tutor, Set.of(Caller.Role.TUTOR), "t@example.test");
    }

    CheckoutSession checkout(Caller caller, PlanTierCode tier) {
        return checkout.create(
                        caller,
                        tier,
                        PaymentProviderCode.STRIPE,
                        RETURN,
                        UUID.randomUUID().toString())
                .session();
    }

    ProviderEvent.CheckoutCompleted paid(CheckoutSession session, String paymentId) {
        return new ProviderEvent.CheckoutCompleted(
                "evt_" + paymentId,
                "checkout.session.completed",
                session.id(),
                session.providerSessionId(),
                session.userId(),
                session.tier(),
                paymentId,
                "sub_" + paymentId,
                "cus_1",
                1200,
                "EUR",
                clock.instant(),
                null,
                null,
                "https://pay.example.test/" + paymentId,
                true);
    }

    PaymentSaga onlySaga() {
        assertThat(sagaStore.rows).hasSize(1);
        return sagaStore.rows.values().iterator().next();
    }

    // ---- checkout and webhook ----

    @Test
    @DisplayName(
            "AC-27.03.2, T-F6-02: a signed payment runs the saga once; a repeated webhook creates no second payment")
    void paymentActivatesProOnce() {
        CheckoutSession session = checkout(studentCaller(), PlanTierCode.STUDENT_PRO);
        assertThat(session.redirectUrl()).startsWith("https://checkout.example.test/");
        assertThat(session.testMode()).isTrue();

        ProviderEvent.CheckoutCompleted event = paid(session, "pi_1");
        webhooks.accept(PaymentProviderCode.STRIPE, event);
        webhooks.accept(PaymentProviderCode.STRIPE, event);
        webhooks.accept(
                PaymentProviderCode.STRIPE,
                new ProviderEvent.CheckoutCompleted(
                        "evt_other_delivery",
                        event.type(),
                        event.checkoutSessionId(),
                        event.providerSessionId(),
                        event.userId(),
                        event.tier(),
                        "pi_1",
                        event.providerSubscriptionId(),
                        event.providerCustomerId(),
                        1200,
                        "EUR",
                        event.paidAt(),
                        null,
                        null,
                        null,
                        true));

        assertThat(onlySaga().status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(payments.rows.values()).singleElement().satisfies(payment -> {
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(payment.subscriptionId()).isNotNull();
        });
        Subscription active = subscriptions.findLive(student).orElseThrow();
        assertThat(active.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(entitlements.entitlements(student).set().has(Entitlement.MOCK_EXAM))
                .isTrue();
        assertThat(sessions.rows.get(session.id()).status()).isEqualTo(CheckoutStatus.COMPLETED);
        assertThat(events.count("subscription-activated")).isEqualTo(1);
        assertThat(events.count("payment-succeeded")).isEqualTo(1);
        assertThat(events.count("entitlements-changed")).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-27.03.5: a declined card fails the session with the provider reason; no subscription")
    void declinedCard() {
        CheckoutSession session = checkout(studentCaller(), PlanTierCode.STUDENT_PRO);

        webhooks.accept(
                PaymentProviderCode.STRIPE,
                new ProviderEvent.CheckoutFailed(
                        "evt_d",
                        "checkout.session.async_payment_failed",
                        session.id(),
                        null,
                        "pi_d",
                        "card_declined",
                        "Your card was declined."));

        CheckoutSession failed = checkout.session(studentCaller(), session.id());
        assertThat(failed.status()).isEqualTo(CheckoutStatus.FAILED);
        assertThat(failed.failureMessage()).isEqualTo("Your card was declined.");
        assertThat(subscriptions.findLive(student)).isEmpty();
        assertThat(events.log).containsExactly("payment-failed:card_declined");
    }

    @Test
    @DisplayName("T-F6-03, docs/10 §17.2: return URL from the allow list, tier for the role, no second subscription")
    void checkoutRules() {
        assertThatThrownBy(() -> checkout.create(
                        studentCaller(),
                        PlanTierCode.STUDENT_PRO,
                        PaymentProviderCode.STRIPE,
                        "https://evil.example/billing",
                        "k1"))
                .isInstanceOfSatisfying(
                        BillingException.class, e -> assertThat(e.code()).isEqualTo("return-url-not-allowed"));
        assertThatThrownBy(() ->
                        checkout.create(studentCaller(), PlanTierCode.TUTOR, PaymentProviderCode.STRIPE, RETURN, "k2"))
                .isInstanceOfSatisfying(
                        BillingException.class, e -> assertThat(e.kind()).isEqualTo(BillingException.Kind.FORBIDDEN));
        assertThatThrownBy(() -> checkout.create(
                        studentCaller(), PlanTierCode.STUDENT_PRO, PaymentProviderCode.BEPAID, RETURN, "k3"))
                .isInstanceOfSatisfying(
                        BillingException.class, e -> assertThat(e.code()).isEqualTo("provider-disabled"));

        CheckoutUseCase.Result first =
                checkout.create(studentCaller(), PlanTierCode.STUDENT_PRO, PaymentProviderCode.STRIPE, RETURN, "k4");
        CheckoutUseCase.Result replay =
                checkout.create(studentCaller(), PlanTierCode.STUDENT_PRO, PaymentProviderCode.STRIPE, RETURN, "k4");
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.session().id()).isEqualTo(first.session().id());
        assertThat(stripe.calls).containsExactly("checkout:STUDENT_PRO");

        webhooks.accept(PaymentProviderCode.STRIPE, paid(first.session(), "pi_4"));
        assertThatThrownBy(() -> checkout(studentCaller(), PlanTierCode.STUDENT_PRO))
                .isInstanceOfSatisfying(
                        BillingException.class, e -> assertThat(e.code()).isEqualTo("subscription-exists"));
    }

    // ---- saga compensations ----

    static Stream<Arguments> failingSteps() {
        return Stream.of(
                Arguments.of("RECORD_PAYMENT", "payments.insert"),
                Arguments.of("ACTIVATE_SUBSCRIPTION", "subscriptions.lockLive"),
                Arguments.of("ALLOCATE_SEATS", "seats.load"),
                Arguments.of("ACTIVATE_TUTOR_WORKSPACE", "classroom.putWorkspace"),
                Arguments.of("GRANT_ENTITLEMENTS", "classroom.enrollments"),
                Arguments.of("COMPLETE", "events.subscriptionActivated"));
    }

    @ParameterizedTest(name = "{0} fails")
    @MethodSource("failingSteps")
    @DisplayName("phase 11a DoD, AC-27.03.3: a step failing after 5 attempts is compensated; money back, no rights")
    void failureOnEveryStepIsCompensated(String step, String operation) {
        UUID enrolled = UUID.randomUUID();
        classroom.students.put(tutor, List.of(enrolled));
        CheckoutSession session = checkout(tutorCaller(), PlanTierCode.TUTOR);
        faults.failing.add(operation);

        webhooks.accept(PaymentProviderCode.STRIPE, paid(session, "pi_fail"));
        for (int i = 0; i < 10 && onlySaga().status() == SagaStatus.RUNNING; i++) {
            clock.shift(Duration.ofMinutes(1));
            sagas.runDue();
        }
        PaymentSaga compensating = onlySaga();
        assertThat(compensating.status()).isIn(SagaStatus.COMPENSATING, SagaStatus.FAILED);
        assertThat(compensating
                        .step(com.toefly.billing.domain.saga.PaymentSagaStep.valueOf(step))
                        .attempts())
                .isGreaterThanOrEqualTo(5);
        faults.failing.clear(); // the dependency is back; compensations go through
        for (int i = 0; i < 10 && onlySaga().status() == SagaStatus.COMPENSATING; i++) {
            clock.shift(Duration.ofMinutes(1));
            sagas.runDue();
        }

        assertThat(onlySaga().status()).isEqualTo(SagaStatus.FAILED);
        assertThat(stripe.refunds).containsExactly("pi_fail");
        assertThat(payments.rows.values())
                .allSatisfy(payment -> assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED));
        assertThat(subscriptions.findLive(tutor)).isEmpty();
        assertThat(entitlements.entitlements(tutor).set().has(Entitlement.TUTOR_WORKSPACE))
                .isFalse();
        assertThat(entitlements.entitlements(enrolled).set().has(Entitlement.MOCK_EXAM))
                .isFalse();
        assertThat(seats.findByStudent(enrolled)).isEmpty();
        assertThat(classroom.workspaces.getOrDefault(tutor, false)).isFalse();
        assertThat(sessions.rows.get(session.id()).status()).isEqualTo(CheckoutStatus.FAILED);
        assertThat(events.log).contains("payment-failed:SAGA_COMPENSATED");
        if (!step.equals("RECORD_PAYMENT")) {
            assertThat(payments.rows).hasSize(1);
        }
        if (List.of("ALLOCATE_SEATS", "ACTIVATE_TUTOR_WORKSPACE", "GRANT_ENTITLEMENTS", "COMPLETE")
                .contains(step)) {
            assertThat(events.endReasons).containsExactly(EndReason.SAGA_COMPENSATED);
        }
    }

    @Test
    @DisplayName("docs/04 §5.10: a tutor payment activates the workspace and gives seats to enrolled students")
    void tutorSagaGivesSeats() {
        UUID enrolled = UUID.randomUUID();
        classroom.students.put(tutor, List.of(enrolled));
        CheckoutSession session = checkout(tutorCaller(), PlanTierCode.TUTOR);

        webhooks.accept(PaymentProviderCode.STRIPE, paid(session, "pi_t"));

        assertThat(onlySaga().status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(classroom.workspaces).containsEntry(tutor, true);
        assertThat(entitlements.tutorSeats(tutor).seatLimit()).isEqualTo(10);
        assertThat(entitlements.tutorSeats(tutor).seatsUsed()).isEqualTo(1);
        assertThat(entitlements.entitlements(enrolled).set().tutorId()).isEqualTo(tutor);
        assertThat(entitlements.entitlements(tutor).set().entitlements())
                .containsExactlyInAnyOrder(Entitlement.TUTOR_WORKSPACE, Entitlement.TUTOR_MATERIALS);
    }

    // ---- trial ----

    @Test
    @DisplayName("AC-27.02.1, AC-27.02.2, AC-27.02.3: 7 days of Pro, TRIAL_ENDING 48 h before, then Free")
    void trialLifecycle() {
        lifecycle.profileCreated(student, Set.of(Caller.Role.STUDENT), null, UUID.randomUUID());
        lifecycle.profileCreated(student, Set.of(Caller.Role.STUDENT), null, UUID.randomUUID());

        assertThat(subscriptions.findByUser(student))
                .singleElement()
                .satisfies(trial -> assertThat(trial.status()).isEqualTo(SubscriptionStatus.TRIALING));
        assertThat(entitlements.entitlements(student).set().trialEndsAt()).isNotNull();
        assertThat(events.log).containsExactly("subscription-activated", "entitlements-changed");

        clock.shift(Duration.ofDays(5).plusHours(1));
        lifecycle.runTimers();
        lifecycle.runTimers();
        assertThat(events.count("trial-ending")).isEqualTo(1);

        clock.shift(Duration.ofDays(2));
        lifecycle.runTimers();
        assertThat(subscriptions.findLive(student)).isEmpty();
        assertThat(events.endReasons).containsExactly(EndReason.TRIAL_EXPIRED);
        assertThat(entitlements.entitlements(student).set().planTierCode()).isEqualTo(PlanTierCode.STUDENT_FREE);
        assertThat(events.count("entitlements-changed")).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-27.02.5, AC-27.02.4: no trial for a tutor; no second trial for the same identity")
    void noTrial() {
        lifecycle.profileCreated(tutor, Set.of(Caller.Role.TUTOR), null, UUID.randomUUID());
        assertThat(subscriptions.findByUser(tutor)).isEmpty();

        lifecycle.profileCreated(student, Set.of(Caller.Role.STUDENT), null, UUID.randomUUID());
        lifecycle.accountDeletionRequested(student);
        assertThat(events.log).contains("entitlements-tombstone");
        lifecycle.profileCreated(student, Set.of(Caller.Role.STUDENT), null, UUID.randomUUID());
        assertThat(subscriptions.findByUser(student)).isEmpty();
    }

    @Test
    @DisplayName("TRIALING → ACTIVE: paying during the trial keeps one subscription")
    void payDuringTrial() {
        lifecycle.profileCreated(student, Set.of(Caller.Role.STUDENT), null, UUID.randomUUID());
        CheckoutSession session = checkout(studentCaller(), PlanTierCode.STUDENT_PRO);

        webhooks.accept(PaymentProviderCode.STRIPE, paid(session, "pi_trial"));

        assertThat(subscriptions.findByUser(student)).singleElement().satisfies(subscription -> {
            assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(subscription.provider()).isEqualTo(PaymentProviderCode.STRIPE);
        });
    }

    // ---- Free AI checks ----

    @Test
    @DisplayName("docs/02 §1.3 rules 1-2: Free checks count, the diagnostic and trial checks do not")
    void freeChecksCount() {
        lifecycle.aiCheckEvaluated(student, false, UUID.randomUUID());
        lifecycle.aiCheckEvaluated(student, true, UUID.randomUUID());
        assertThat(entitlements.entitlements(student).set().freeAiChecks().used())
                .isEqualTo(1);

        lifecycle.profileCreated(student, Set.of(Caller.Role.STUDENT), null, UUID.randomUUID());
        lifecycle.aiCheckEvaluated(student, false, UUID.randomUUID());
        assertThat(entitlements.entitlements(student).set().freeAiChecks().used())
                .isEqualTo(1);
    }

    // ---- renewals, cancel, plan change ----

    @Test
    @DisplayName("AC-27.06.1, AC-27.06.2: a failed renewal keeps rights for 7 days, then ends the subscription")
    void failedRenewal() {
        webhooks.accept(PaymentProviderCode.STRIPE, paid(checkout(studentCaller(), PlanTierCode.STUDENT_PRO), "pi_r"));

        webhooks.accept(
                PaymentProviderCode.STRIPE,
                new ProviderEvent.RenewalFailed(
                        "evt_rf", "invoice.payment_failed", "sub_pi_r", "in_2", "card_declined", "Insufficient funds"));
        assertThat(subscriptions.findLive(student).orElseThrow().status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(events.log).contains("payment-failed:card_declined");
        assertThat(entitlements.entitlements(student).set().has(Entitlement.MOCK_EXAM))
                .isTrue();

        clock.shift(Duration.ofDays(7));
        lifecycle.runTimers();
        assertThat(subscriptions.findLive(student)).isEmpty();
        assertThat(events.endReasons).containsExactly(EndReason.PAYMENT_FAILED);
        assertThat(entitlements.entitlements(student).set().has(Entitlement.MOCK_EXAM))
                .isFalse();
    }

    @Test
    @DisplayName("AC-27.07.1: a paid renewal extends the period and sends a receipt")
    void paidRenewal() {
        webhooks.accept(PaymentProviderCode.STRIPE, paid(checkout(studentCaller(), PlanTierCode.STUDENT_PRO), "pi_p"));
        Instant next = clock.instant().plus(Duration.ofDays(30));

        webhooks.accept(
                PaymentProviderCode.STRIPE,
                new ProviderEvent.RenewalPaid(
                        "evt_rp",
                        "invoice.paid",
                        "sub_pi_p",
                        "in_3",
                        1200,
                        "EUR",
                        next,
                        next,
                        next.plus(Duration.ofDays(30)),
                        null,
                        true));

        assertThat(subscriptions.findLive(student).orElseThrow().periodEnd()).isEqualTo(next.plus(Duration.ofDays(30)));
        assertThat(events.count("payment-succeeded:renewal")).isEqualTo(1);
        assertThat(payments.findByUser(student))
                .extracting(Payment::providerPaymentId)
                .contains("in_3", "pi_p");
    }

    @Test
    @DisplayName("AC-27.05.2: cancel at period end keeps Pro until the end, then subscription-canceled")
    void cancelThenExpire() {
        webhooks.accept(PaymentProviderCode.STRIPE, paid(checkout(studentCaller(), PlanTierCode.STUDENT_PRO), "pi_c"));

        subscriptionService.cancel(studentCaller());
        assertThat(stripe.calls).contains("cancel:sub_pi_c");
        assertThat(entitlements.entitlements(student).set().has(Entitlement.MOCK_EXAM))
                .isTrue();

        clock.shift(Duration.ofDays(32));
        lifecycle.runTimers();
        assertThat(events.endReasons).containsExactly(EndReason.USER_CANCELED);
        assertThat(entitlements.entitlements(student).set().planTierCode()).isEqualTo(PlanTierCode.STUDENT_FREE);
    }

    @Test
    @DisplayName("AC-27.05.3, AC-27.05.4: upgrade now with 30 seats; downgrade refused above 10 students")
    void tutorPlanChange() {
        webhooks.accept(PaymentProviderCode.STRIPE, paid(checkout(tutorCaller(), PlanTierCode.TUTOR), "pi_u"));

        subscriptionService.changePlan(tutorCaller(), PlanTierCode.TUTOR_PLUS);
        assertThat(entitlements.tutorSeats(tutor).seatLimit()).isEqualTo(30);
        assertThat(stripe.calls).contains("change:TUTOR_PLUS:true");

        UUID subscription = subscriptions.findLive(tutor).orElseThrow().id();
        var pool = seats.load(subscription);
        for (int i = 0; i < 11; i++) {
            pool = pool.assign(UUID.randomUUID(), clock.instant());
        }
        seats.save(pool);
        assertThatThrownBy(() -> subscriptionService.changePlan(tutorCaller(), PlanTierCode.TUTOR))
                .isInstanceOfSatisfying(BillingException.class, e -> {
                    assertThat(e.code()).isEqualTo("seats-in-use");
                    assertThat(e.getMessage()).isEqualTo("Remove students to 10 or fewer to switch to Tutor.");
                });
    }
}
