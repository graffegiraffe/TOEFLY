package com.toefly.billing.application.port.in;

/** The provider is unavailable or refused the call; 502 payment-provider-error. */
public class ProviderException extends RuntimeException {

    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
