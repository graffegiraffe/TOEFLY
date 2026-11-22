package com.toefly.billing.domain.subscription;

/** docs/00-README.md §5.2; {@code MANUAL} is a grant of an administrator without a provider. */
public enum PaymentProviderCode {
    STRIPE,
    BEPAID,
    APP_STORE,
    MANUAL
}
