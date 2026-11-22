package com.toefly.billing.domain.entitlement;

import com.toefly.billing.domain.plan.PlanTier;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.Subscription;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Rights of a user = union of the Free base set, the own subscription (paid, trial or manual) and a tutor's seat
 * (docs/02 §1.3, docs/03 §6.13). One grant per right: an unlimited grant wins over the limited Free one.
 */
public final class EntitlementCalculator {

    private final Map<PlanTierCode, PlanTier> tiers;

    public EntitlementCalculator(List<PlanTier> tiers) {
        this.tiers = new EnumMap<>(PlanTierCode.class);
        tiers.forEach(tier -> this.tiers.put(tier.code(), tier));
        if (!this.tiers.keySet().containsAll(List.of(PlanTierCode.values()))) {
            throw new IllegalArgumentException("every plan tier needs reference data");
        }
    }

    public PlanTier tier(PlanTierCode code) {
        return Objects.requireNonNull(tiers.get(code));
    }

    /**
     * @param own the user's live subscription, if any
     * @param seat the seat the user holds at a tutor, if any
     * @param freeUsed AI checks used on Free so far
     */
    public EntitlementSet calculate(
            UUID userId, @Nullable Subscription own, @Nullable SeatHolding seat, int freeUsed, Instant now) {
        PlanTier free = tier(PlanTierCode.STUDENT_FREE);
        FreeAiChecks freeChecks =
                new FreeAiChecks(free.freeAiCheckLimit() == null ? 0 : free.freeAiCheckLimit(), freeUsed);
        Map<Entitlement, Grant> grants = new EnumMap<>(Entitlement.class);
        PlanTierCode effective = PlanTierCode.STUDENT_FREE;
        UUID tutorId = null;
        Instant trialEndsAt = null;

        for (Entitlement entitlement : free.entitlements()) {
            boolean limited = entitlement.aiCheck();
            put(
                    grants,
                    new Grant(
                            entitlement,
                            EntitlementSource.SUBSCRIPTION,
                            null,
                            limited ? freeChecks.limit() : null,
                            limited ? freeChecks.used() : null,
                            null));
        }
        if (seat != null && seat.activeAt(now)) {
            for (Entitlement entitlement : tier(PlanTierCode.STUDENT_PRO).entitlements()) {
                put(grants, new Grant(entitlement, EntitlementSource.SEAT, seat.subscriptionId(), null, null, null));
            }
            effective = PlanTierCode.STUDENT_PRO;
            tutorId = seat.tutorId();
        }
        if (own != null && own.grantsAccess(now)) {
            EntitlementSource source = own.trial()
                    ? EntitlementSource.TRIAL
                    : own.provider() == PaymentProviderCode.MANUAL
                            ? EntitlementSource.MANUAL
                            : EntitlementSource.SUBSCRIPTION;
            for (Entitlement entitlement : tier(own.tier()).entitlements()) {
                put(grants, new Grant(entitlement, source, own.id(), null, null, own.accessUntil()));
            }
            if (rank(own.tier()) >= rank(effective)) {
                effective = own.tier();
            }
            if (own.trial()) {
                trialEndsAt = own.trialEndsAt();
            }
            if (own.tier().tutor()) {
                // Student rights are not given to tutors (docs/03 §6.13): the Free base set goes away.
                grants.values()
                        .removeIf(
                                grant -> grant.source() == EntitlementSource.SUBSCRIPTION && grant.sourceId() == null);
            }
        }
        List<Grant> ordered = new ArrayList<>(grants.values());
        ordered.sort(Comparator.comparing(Grant::entitlement));
        return new EntitlementSet(userId, effective, grants.keySet(), ordered, freeChecks, tutorId, trialEndsAt);
    }

    /** Free < Pro < Tutor < Tutor Plus: the effective tier shown to the user. */
    private static int rank(PlanTierCode code) {
        return code.ordinal();
    }

    private static void put(Map<Entitlement, Grant> grants, Grant candidate) {
        Grant current = grants.get(candidate.entitlement());
        if (current == null || better(candidate, current)) {
            grants.put(candidate.entitlement(), candidate);
        }
    }

    private static boolean better(Grant candidate, Grant current) {
        if (current.limited() != candidate.limited()) {
            return !candidate.limited();
        }
        if ((current.sourceId() == null) != (candidate.sourceId() == null)) {
            return current.sourceId() == null; // a real source wins over the Free base set
        }
        if (candidate.validUntil() == null) {
            return current.validUntil() != null;
        }
        return current.validUntil() != null && candidate.validUntil().isAfter(current.validUntil());
    }

    /** A seat at a tutor whose subscription gives Pro to the student. */
    public record SeatHolding(UUID tutorId, UUID subscriptionId, boolean tutorSubscriptionActive) {

        public boolean activeAt(Instant now) {
            return tutorSubscriptionActive;
        }
    }
}
