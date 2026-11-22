package com.toefly.billing.domain.entitlement;

/** The lifetime limit of AI checks on Free (docs/02 §1.3, rule 1). */
public record FreeAiChecks(int limit, int used) {

    public FreeAiChecks {
        if (limit < 0 || used < 0) {
            throw new IllegalArgumentException("limit and used must not be negative");
        }
    }

    public int remaining() {
        return Math.max(0, limit - used);
    }

    public boolean exhausted() {
        return used >= limit;
    }
}
