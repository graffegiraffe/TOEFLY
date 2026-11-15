package com.toefly.aigateway.domain.quota;

import java.util.EnumMap;
import java.util.Map;

/**
 * Shares of the daily budgets a priority must leave untouched (§2.5.2): grading leaves 10% to interactive calls,
 * background work leaves 30%.
 */
public final class ReservePolicy {

    private final Map<Priority, Double> floors = new EnumMap<>(Priority.class);

    public ReservePolicy(double grading, double background) {
        if (grading < 0 || grading >= 1 || background < 0 || background >= 1) {
            throw new IllegalArgumentException("reserve shares must be in [0, 1)");
        }
        floors.put(Priority.INTERACTIVE, 0.0);
        floors.put(Priority.GRADING, grading);
        floors.put(Priority.BACKGROUND, background);
    }

    public static ReservePolicy defaults() {
        return new ReservePolicy(0.10, 0.30);
    }

    /** Units of the bucket the priority may not spend. */
    public double floor(QuotaLimit limit, Priority priority) {
        return limit.dimension().budget() ? limit.capacity() * floors.get(priority) : 0;
    }
}
