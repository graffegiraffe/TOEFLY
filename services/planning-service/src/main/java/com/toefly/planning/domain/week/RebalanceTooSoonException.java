package com.toefly.planning.domain.week;

import java.time.Instant;

/** A second rebalance within 48 hours (AC-04.05.4). */
public class RebalanceTooSoonException extends WeekRuleException {

    private final Instant nextAllowedAt;

    public RebalanceTooSoonException(Instant nextAllowedAt) {
        super("conflict", "The week can be rebalanced again at " + nextAllowedAt);
        this.nextAllowedAt = nextAllowedAt;
    }

    public Instant nextAllowedAt() {
        return nextAllowedAt;
    }
}
