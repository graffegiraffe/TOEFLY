package com.toefly.billing.application.port.in;

import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.util.Map;

public interface WebhookUseCase {

    /**
     * Verifies, stores once and processes a provider notification (INV-BIL-03).
     *
     * @throws InvalidSignatureException before anything is stored
     */
    void receive(PaymentProviderCode provider, Map<String, String> headers, byte[] body);

    /** Processes stored notifications that failed before; returns how many were handled. */
    int processPending();
}
