package com.toefly.profile.domain.privacy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Request to collect the user's data from every service (US-31.03). Collection itself arrives with the services; a
 * new request is allowed 24 hours after the previous one (AC-31.03.3).
 */
public record DataExport(
        UUID id,
        UUID userId,
        DataExportStatus status,
        Instant requestedAt,
        @Nullable Instant readyAt) {

    public static final Duration MIN_INTERVAL = Duration.ofHours(24);

    public static DataExport request(UUID userId, @Nullable DataExport previous, Instant now) {
        if (previous != null) {
            Instant allowedAt = previous.requestedAt().plus(MIN_INTERVAL);
            if (now.isBefore(allowedAt)) {
                throw new DataExportTooSoonException(Duration.between(now, allowedAt));
            }
        }
        return new DataExport(UUID.randomUUID(), userId, DataExportStatus.REQUESTED, now, null);
    }
}
