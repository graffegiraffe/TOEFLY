package com.toefly.aigateway.application.port.in;

/** A refusal that tells the caller when to come back; the web adapter sends it as {@code Retry-After}. */
public interface RetryAfter {

    long retryAfterSeconds();
}
