package com.toefly.billing.domain.saga;

/**
 * Steps of the payment saga (docs/04 §5.10). Seats and the tutor workspace run only for tutor tiers; a student tier
 * skips them. Each step is idempotent by {@code sagaId:stepName}.
 */
public enum PaymentSagaStep {
    RECORD_PAYMENT(1, false),
    ACTIVATE_SUBSCRIPTION(2, false),
    ALLOCATE_SEATS(3, true),
    ACTIVATE_TUTOR_WORKSPACE(4, true),
    GRANT_ENTITLEMENTS(5, false),
    COMPLETE(6, false);

    private final int no;
    private final boolean tutorOnly;

    PaymentSagaStep(int no, boolean tutorOnly) {
        this.no = no;
        this.tutorOnly = tutorOnly;
    }

    public int no() {
        return no;
    }

    public boolean tutorOnly() {
        return tutorOnly;
    }

    /** COMPLETE publishes subscription-activated; an event is not taken back (compensation publishes canceled). */
    public boolean compensable() {
        return this != COMPLETE;
    }

    public static PaymentSagaStep of(int no) {
        for (PaymentSagaStep step : values()) {
            if (step.no == no) {
                return step;
            }
        }
        throw new IllegalArgumentException("no saga step " + no);
    }
}
