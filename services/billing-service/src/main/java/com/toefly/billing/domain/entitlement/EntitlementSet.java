package com.toefly.billing.domain.entitlement;

import com.toefly.billing.domain.plan.PlanTierCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The calculated rights of a user: effective tier, the union of rights and their grants (docs/02 §1.3). */
public record EntitlementSet(
        UUID userId,
        PlanTierCode planTierCode,
        Set<Entitlement> entitlements,
        List<Grant> grants,
        FreeAiChecks freeAiChecks,
        @Nullable UUID tutorId,
        @Nullable Instant trialEndsAt) {

    public EntitlementSet {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(planTierCode, "planTierCode");
        entitlements = Set.copyOf(entitlements);
        grants = List.copyOf(grants);
    }

    public boolean has(Entitlement entitlement) {
        return entitlements.contains(entitlement);
    }

    /** Stable digest of the content; a new snapshot version is published only when it changes. */
    public String fingerprint() {
        StringBuilder text = new StringBuilder()
                .append(planTierCode)
                .append('|')
                .append(tutorId)
                .append('|')
                .append(trialEndsAt);
        grants.stream()
                .map(grant -> grant.entitlement() + ":" + grant.source() + ":" + grant.sourceId() + ":" + grant.limit()
                        + ":" + grant.used() + ":" + grant.validUntil())
                .sorted()
                .forEach(line -> text.append('|').append(line));
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
