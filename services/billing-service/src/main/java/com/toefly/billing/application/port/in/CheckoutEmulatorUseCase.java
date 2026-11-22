package com.toefly.billing.application.port.in;

import com.toefly.billing.domain.checkout.CheckoutSession;
import java.util.Optional;
import java.util.UUID;

/** The test checkout page of the local profile (Stripe emulator). */
public interface CheckoutEmulatorUseCase {

    Optional<CheckoutSession> find(UUID sessionId);

    /** Delivers the signed webhook of a paid or declined card; returns the address to go back to. */
    String complete(UUID sessionId, boolean approve);
}
