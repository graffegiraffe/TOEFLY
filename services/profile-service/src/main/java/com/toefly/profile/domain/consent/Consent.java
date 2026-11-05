package com.toefly.profile.domain.consent;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One entry of the consent journal; a change appends a new entry and earlier ones stay as evidence (INV-PRF-06).
 * The address is kept truncated (AC-31.01.2).
 */
public record Consent(
        UUID id,
        UUID userId,
        ConsentType type,
        boolean granted,
        String policyVersion,
        ConsentSource source,
        @Nullable String ipPrefix,
        Instant recordedAt) {

    public static Consent record(
            UUID userId,
            ConsentType type,
            boolean granted,
            String policyVersion,
            ConsentSource source,
            @Nullable String ip,
            Instant now) {
        if (!granted && type.requiredAtSignUp()) {
            throw new RequiredConsentException(type);
        }
        return new Consent(UUID.randomUUID(), userId, type, granted, policyVersion, source, TruncatedIp.of(ip), now);
    }
}
