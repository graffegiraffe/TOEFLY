package com.toefly.aigateway.application.port.in;

/** Whether a call may read or write the response cache (§2.2.1). */
public enum CachePolicy {
    NONE,
    READ_WRITE,
    READ_ONLY;

    public boolean reads() {
        return this != NONE;
    }

    public boolean writes() {
        return this == READ_WRITE;
    }
}
