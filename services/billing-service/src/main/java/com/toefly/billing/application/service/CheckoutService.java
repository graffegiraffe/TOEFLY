package com.toefly.billing.application.service;

import com.toefly.billing.application.port.in.BillingException;
import com.toefly.billing.application.port.in.Caller;
import com.toefly.billing.application.port.in.CheckoutUseCase;
import com.toefly.billing.application.port.in.ProviderException;
import com.toefly.billing.application.port.out.CheckoutSessionRepository;
import com.toefly.billing.application.port.out.PaymentProvider;
import com.toefly.billing.application.port.out.SubscriptionRepository;
import com.toefly.billing.domain.checkout.CheckoutSession;
import com.toefly.billing.domain.checkout.CheckoutStatus;
import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.Subscription;
import com.toefly.billing.domain.subscription.SubscriptionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hosted checkout (docs/10 §17.2): amount and price come from the tier, never from the client; the return URL is from
 * the allow list; the provider session is created outside the transaction and stored after.
 */
@Service
public class CheckoutService implements CheckoutUseCase {

    private final PaymentProviders providers;
    private final CheckoutSessionRepository sessions;
    private final SubscriptionRepository subscriptions;
    private final EntitlementService entitlements;
    private final BillingPolicy policy;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public CheckoutService(
            PaymentProviders providers,
            CheckoutSessionRepository sessions,
            SubscriptionRepository subscriptions,
            EntitlementService entitlements,
            BillingPolicy policy,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.providers = providers;
        this.sessions = sessions;
        this.subscriptions = subscriptions;
        this.entitlements = entitlements;
        this.policy = policy;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public Result create(
            Caller caller, PlanTierCode tierCode, PaymentProviderCode providerCode, String returnUrl, String key) {
        Optional<CheckoutSession> earlier = sessions.findByKey(caller.userId(), key);
        if (earlier.isPresent()) {
            CheckoutSession session = earlier.get();
            if (session.tier() != tierCode || session.provider() != providerCode || session.redirectUrl() == null) {
                throw BillingException.unprocessable(
                        "idempotency-key-reuse", "The Idempotency-Key was used for another checkout.");
            }
            return new Result(session, true);
        }
        PlanTier tier = validate(caller, tierCode, providerCode, returnUrl);
        PaymentProvider provider = providers.require(providerCode);
        Instant now = clock.instant();
        CheckoutSession open = CheckoutSession.open(
                UUID.randomUUID(),
                caller.userId(),
                key,
                tierCode,
                providerCode,
                returnUrl,
                provider.testMode(),
                now,
                now.plus(policy.checkoutLifetime()));
        Boolean inserted = transactions.execute(status -> sessions.insert(open));
        if (!Boolean.TRUE.equals(inserted)) {
            return create(caller, tierCode, providerCode, returnUrl, key);
        }
        String back = returnUrl + (returnUrl.contains("?") ? "&" : "?") + "session=" + open.id();
        PaymentProvider.Checkout checkout;
        try {
            checkout = provider.createCheckout(new PaymentProvider.CheckoutRequest(
                    open.id(), caller.userId(), tier, back, back + "&canceled=true", caller.email()));
        } catch (ProviderException failure) {
            transactions.executeWithoutResult(
                    status -> sessions.update(open.failed("PROVIDER_ERROR", failure.getMessage())));
            throw failure;
        }
        CheckoutSession redirected =
                open.redirect(checkout.providerSessionId(), checkout.redirectUrl(), checkout.expiresAt());
        transactions.executeWithoutResult(status -> sessions.update(redirected));
        return new Result(redirected, false);
    }

    private PlanTier validate(Caller caller, PlanTierCode tierCode, PaymentProviderCode provider, String returnUrl) {
        if (tierCode == PlanTierCode.STUDENT_FREE) {
            throw BillingException.invalid("STUDENT_FREE needs no payment.");
        }
        if (provider != PaymentProviderCode.STRIPE && provider != PaymentProviderCode.BEPAID) {
            throw BillingException.invalid("Web checkout supports STRIPE and BEPAID; iOS uses the App Store.");
        }
        if (!policy.webProviders().contains(provider)) {
            throw BillingException.unprocessable("provider-disabled", "Payment provider " + provider + " is off.");
        }
        Caller.Role audience = tierCode.tutor() ? Caller.Role.TUTOR : Caller.Role.STUDENT;
        if (!caller.is(audience)) {
            throw BillingException.forbidden("Plan " + tierCode + " is for the role " + audience + ".");
        }
        if (!policy.allowedReturnUrl(returnUrl)) {
            throw BillingException.unprocessable("return-url-not-allowed", "The return address is not allowed.");
        }
        Optional<Subscription> live = subscriptions.findLive(caller.userId());
        if (live.isPresent() && live.get().status() != SubscriptionStatus.TRIALING) {
            throw BillingException.conflict("subscription-exists", "You already have a subscription.");
        }
        return entitlements.calculator().tier(tierCode);
    }

    @Override
    public CheckoutSession session(Caller caller, UUID sessionId) {
        CheckoutSession session = sessions.findById(sessionId)
                .filter(found -> found.userId().equals(caller.userId()))
                .orElseThrow(() -> BillingException.notFound("No checkout session " + sessionId));
        if (session.status() == CheckoutStatus.OPEN && !clock.instant().isBefore(session.expiresAt())) {
            return new CheckoutSession(
                    session.id(),
                    session.userId(),
                    session.idempotencyKey(),
                    session.tier(),
                    session.provider(),
                    session.providerSessionId(),
                    session.redirectUrl(),
                    session.returnUrl(),
                    CheckoutStatus.EXPIRED,
                    session.testMode(),
                    session.failureReason(),
                    session.failureMessage(),
                    session.sagaId(),
                    session.createdAt(),
                    session.expiresAt());
        }
        return session;
    }

    @Override
    public String portal(Caller caller, String returnUrl) {
        if (!policy.allowedReturnUrl(returnUrl)) {
            throw BillingException.unprocessable("return-url-not-allowed", "The return address is not allowed.");
        }
        Subscription subscription = subscriptions
                .findLive(caller.userId())
                .filter(found -> found.provider() != null && found.providerCustomerId() != null)
                .orElseThrow(() -> BillingException.conflict("no-subscription", "There is no paid subscription."));
        String url = providers.require(subscription.provider()).portalUrl(subscription.providerCustomerId(), returnUrl);
        if (url == null) {
            throw BillingException.conflict("portal-unavailable", "This provider has no customer portal.");
        }
        return url;
    }
}
