package com.toefly.billing.domain.checkout;

/** OPEN until the webhook; PROCESSING while the saga runs; then COMPLETED or FAILED. */
public enum CheckoutStatus {
    OPEN,
    PROCESSING,
    COMPLETED,
    FAILED,
    EXPIRED
}
