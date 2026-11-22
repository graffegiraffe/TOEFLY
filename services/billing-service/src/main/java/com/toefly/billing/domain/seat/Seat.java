package com.toefly.billing.domain.seat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A seat of a tutor subscription; free while {@code studentId} is null. */
public record Seat(
        UUID id,
        UUID subscriptionId,
        int index,
        @Nullable UUID studentId,
        @Nullable Instant assignedAt) {

    public Seat {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        if (index < 1 || index > 30) {
            throw new IllegalArgumentException("seat index must be 1 to 30");
        }
    }

    public boolean free() {
        return studentId == null;
    }

    public Seat assign(UUID student, Instant now) {
        return new Seat(id, subscriptionId, index, student, now);
    }

    public Seat release() {
        return new Seat(id, subscriptionId, index, null, null);
    }
}
