package com.toefly.billing.application.port.out;

import com.toefly.billing.domain.checkout.CheckoutSession;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutSessionRepository {

    /** Inserts a new session; returns false when the user already used the idempotency key. */
    boolean insert(CheckoutSession session);

    void update(CheckoutSession session);

    Optional<CheckoutSession> findById(UUID id);

    Optional<CheckoutSession> findByKey(UUID userId, String idempotencyKey);

    Optional<CheckoutSession> findByProviderSession(PaymentProviderCode provider, String providerSessionId);

    void deleteByUser(UUID userId);
}
