package com.toefly.billing.application.port.in;

import com.toefly.billing.domain.payment.Payment;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.Subscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface EntitlementQueryUseCase {

    /** Current rights; a user billing never saw gets the Free set with version 0. */
    EntitlementView entitlements(UUID userId);

    /** Internal: the stored snapshot, empty for an unknown user (the caller applies Free). */
    Optional<EntitlementView> snapshot(UUID userId);

    TutorSeats tutorSeats(UUID tutorId);

    Export export(UUID userId);

    record TutorSeats(UUID tutorId, @Nullable PlanTierCode tier, int seatLimit, int seatsUsed) {}

    record Export(
            UUID userId, List<Subscription> subscriptions, List<Payment> payments, EntitlementView entitlements) {}
}
