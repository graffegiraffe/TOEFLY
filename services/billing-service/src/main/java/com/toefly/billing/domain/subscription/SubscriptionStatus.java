package com.toefly.billing.domain.subscription;

/** docs/00-README.md §5.2. */
public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    EXPIRED
}
