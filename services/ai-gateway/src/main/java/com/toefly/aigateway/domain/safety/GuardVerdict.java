package com.toefly.aigateway.domain.safety;

/** Verdict of the guard (§3.4). */
public enum GuardVerdict {
    SAFE,
    UNSAFE,
    INJECTION,
    DISHONESTY
}
