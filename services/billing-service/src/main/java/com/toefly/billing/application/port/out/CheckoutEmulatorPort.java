package com.toefly.billing.application.port.out;

import com.toefly.billing.domain.checkout.CheckoutSession;
import java.util.Map;

/**
 * Local stand without provider keys: builds the webhook a provider would send after its hosted page, signed with the
 * configured webhook secret, so the real verification and saga run.
 */
public interface CheckoutEmulatorPort {

    Delivery notification(CheckoutSession session, boolean approve);

    record Delivery(Map<String, String> headers, byte[] body) {}
}
