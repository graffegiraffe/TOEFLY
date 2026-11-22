package com.toefly.billing.application.service;

import com.toefly.billing.application.port.in.AppStoreUseCase;
import com.toefly.billing.application.port.in.BillingException;
import com.toefly.billing.application.port.in.Caller;
import com.toefly.billing.application.port.in.SubscriptionView;
import com.toefly.billing.application.port.out.AppStoreVerifierPort;
import com.toefly.billing.application.port.out.ProviderEvent;
import com.toefly.billing.application.port.out.SubscriptionRepository;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import org.springframework.stereotype.Service;

/**
 * AC-27.04.2: the app sends the signed StoreKit 2 transaction; after the JWS check the same path as a webhook runs.
 * The first transaction of an original id starts the saga, later ones renew.
 */
@Service
public class AppStoreService implements AppStoreUseCase {

    private final AppStoreVerifierPort verifier;
    private final SubscriptionRepository subscriptions;
    private final WebhookService webhooks;
    private final SubscriptionService views;
    private final BillingPolicy policy;

    public AppStoreService(
            AppStoreVerifierPort verifier,
            SubscriptionRepository subscriptions,
            WebhookService webhooks,
            SubscriptionService views,
            BillingPolicy policy) {
        this.verifier = verifier;
        this.subscriptions = subscriptions;
        this.webhooks = webhooks;
        this.views = views;
        this.policy = policy;
    }

    @Override
    public SubscriptionView submit(Caller caller, String signedTransactionInfo) {
        AppStoreVerifierPort.Transaction transaction = verifier.verify(signedTransactionInfo);
        if (!caller.userId().equals(transaction.appAccountToken())) {
            throw BillingException.conflict(
                    "conflict", "The transaction belongs to another account (appAccountToken).");
        }
        PlanTierCode tier = policy.appStoreProducts().get(transaction.productId());
        if (tier == null) {
            throw BillingException.invalid("Unknown App Store product " + transaction.productId());
        }
        boolean renewal = subscriptions
                .findByProviderRef(PaymentProviderCode.APP_STORE, transaction.originalTransactionId())
                .isPresent();
        String eventId = "transaction:" + transaction.transactionId();
        ProviderEvent event = renewal
                ? new ProviderEvent.RenewalPaid(
                        eventId,
                        "app-store.transaction",
                        transaction.originalTransactionId(),
                        transaction.transactionId(),
                        transaction.priceMinor(),
                        transaction.currency(),
                        transaction.purchaseDate(),
                        transaction.purchaseDate(),
                        transaction.expiresDate(),
                        null,
                        transaction.sandbox())
                : new ProviderEvent.CheckoutCompleted(
                        eventId,
                        "app-store.transaction",
                        null,
                        null,
                        caller.userId(),
                        tier,
                        transaction.transactionId(),
                        transaction.originalTransactionId(),
                        null,
                        transaction.priceMinor(),
                        transaction.currency(),
                        transaction.purchaseDate(),
                        transaction.purchaseDate(),
                        transaction.expiresDate(),
                        null,
                        transaction.sandbox());
        webhooks.accept(PaymentProviderCode.APP_STORE, event);
        return views.current(caller);
    }
}
