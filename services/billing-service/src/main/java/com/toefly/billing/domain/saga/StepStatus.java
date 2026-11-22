package com.toefly.billing.domain.saga;

public enum StepStatus {
    PENDING,
    DONE,
    FAILED,
    SKIPPED,
    COMPENSATED
}
