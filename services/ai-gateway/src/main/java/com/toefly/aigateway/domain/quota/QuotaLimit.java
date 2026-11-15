package com.toefly.aigateway.domain.quota;

/** Capacity of one dimension, refilled continuously over its window. */
public record QuotaLimit(QuotaDimension dimension, long capacity) {

    public QuotaLimit {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + dimension);
        }
    }

    /** Units returned to the bucket per millisecond. */
    public double refillPerMilli() {
        return (double) capacity / dimension.window().toMillis();
    }
}
