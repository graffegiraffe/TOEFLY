package com.toefly.billing.application.port.out;

import com.toefly.billing.domain.payment.Payment;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    /** Inserts once per provider payment id; returns the stored record (INV-BIL-03). */
    Payment insertIfAbsent(Payment payment);

    void update(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByProviderRef(PaymentProviderCode provider, String providerPaymentId);

    List<Payment> findByUser(UUID userId);

    /** INV-BIL-05: payments stay for accounting without the user. */
    void anonymize(UUID userId);
}
