package com.toefly.billing.domain.subscription;

/** Why access from a subscription stopped ({@code subscription-canceled.reason}). */
public enum EndReason {
    USER_CANCELED,
    TRIAL_EXPIRED,
    PAYMENT_FAILED,
    PROVIDER_CANCELED,
    REFUNDED,
    SAGA_COMPENSATED,
    ACCOUNT_DELETED,
    GRANT_ENDED,
    /** A paid period without automatic renewal (a one-off bePaid payment) is over. */
    PERIOD_ENDED
}
