package com.toefly.billing.domain.saga;

public enum SagaStatus {
    RUNNING,
    COMPENSATING,
    COMPLETED,
    FAILED;

    public boolean active() {
        return this == RUNNING || this == COMPENSATING;
    }
}
