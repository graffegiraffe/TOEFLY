package com.toefly.billing.application.service;

import com.toefly.billing.application.port.in.BillingException;
import com.toefly.billing.application.port.in.CheckoutEmulatorUseCase;
import com.toefly.billing.application.port.out.CheckoutEmulatorPort;
import com.toefly.billing.application.port.out.CheckoutSessionRepository;
import com.toefly.billing.domain.checkout.CheckoutSession;
import com.toefly.billing.domain.checkout.CheckoutStatus;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class CheckoutEmulatorService implements CheckoutEmulatorUseCase {

    private final CheckoutSessionRepository sessions;
    private final ObjectProvider<CheckoutEmulatorPort> emulator;
    private final WebhookService webhooks;

    public CheckoutEmulatorService(
            CheckoutSessionRepository sessions,
            ObjectProvider<CheckoutEmulatorPort> emulator,
            WebhookService webhooks) {
        this.sessions = sessions;
        this.emulator = emulator;
        this.webhooks = webhooks;
    }

    @Override
    public Optional<CheckoutSession> find(UUID sessionId) {
        return sessions.findById(sessionId).filter(session -> session.provider() == PaymentProviderCode.STRIPE);
    }

    @Override
    public String complete(UUID sessionId, boolean approve) {
        CheckoutEmulatorPort port = emulator.getIfAvailable();
        CheckoutSession session = find(sessionId)
                .filter(found -> found.status() == CheckoutStatus.OPEN)
                .orElseThrow(() -> BillingException.notFound("No open emulated checkout " + sessionId));
        if (port == null) {
            throw BillingException.notFound("The checkout emulator is off.");
        }
        CheckoutEmulatorPort.Delivery delivery = port.notification(session, approve);
        webhooks.receive(PaymentProviderCode.STRIPE, delivery.headers(), delivery.body());
        String back = session.returnUrl();
        return back + (back.contains("?") ? "&" : "?") + "session=" + session.id();
    }
}
