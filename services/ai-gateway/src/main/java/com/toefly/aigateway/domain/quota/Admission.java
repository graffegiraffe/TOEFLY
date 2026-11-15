package com.toefly.aigateway.domain.quota;

import org.jspecify.annotations.Nullable;

/** Answer of the buckets: a reservation, or how long to wait for one ({@code -1}: the demand never fits). */
public record Admission(@Nullable Reservation reservation, long waitMillis) {

    public static Admission granted(Reservation reservation) {
        return new Admission(reservation, 0);
    }

    public static Admission waitFor(long millis) {
        return new Admission(null, millis);
    }

    public boolean granted() {
        return reservation != null;
    }

    public boolean impossible() {
        return reservation == null && waitMillis < 0;
    }
}
