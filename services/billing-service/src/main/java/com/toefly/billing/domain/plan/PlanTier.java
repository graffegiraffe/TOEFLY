package com.toefly.billing.domain.plan;

import com.toefly.billing.domain.entitlement.Entitlement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Reference data of a plan tier: price per month, seats, storage, trial and rights (docs/03 §6.13). */
public record PlanTier(
        PlanTierCode code,
        int priceMinor,
        String currency,
        @Nullable Integer seatLimit,
        @Nullable Long storageBytes,
        int trialDays,
        @Nullable Integer freeAiCheckLimit,
        Set<Entitlement> entitlements) {

    public PlanTier {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(currency, "currency");
        entitlements = Set.copyOf(entitlements);
        if (priceMinor < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
    }

    public boolean free() {
        return priceMinor == 0;
    }

    public int seats() {
        return seatLimit == null ? 0 : seatLimit;
    }

    public Duration trial() {
        return Duration.ofDays(trialDays);
    }

    /** End of a monthly period started at {@code start}. */
    public static Instant periodEnd(Instant start) {
        return start.atOffset(ZoneOffset.UTC).plusMonths(1).toInstant();
    }
}
