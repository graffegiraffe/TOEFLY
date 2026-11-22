package com.toefly.billing.domain.subscription;

/** The requested transition is not allowed from the current status (docs/03 §6.13, state diagram). */
public class SubscriptionStateException extends RuntimeException {

    private final String code;

    public SubscriptionStateException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Problem slug: {@code no-subscription}, {@code manage-in-app-store}, {@code conflict}, {@code seats-in-use}. */
    public String code() {
        return code;
    }
}
