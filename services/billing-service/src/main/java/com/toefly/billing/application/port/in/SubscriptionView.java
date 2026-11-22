package com.toefly.billing.application.port.in;

import com.toefly.billing.domain.entitlement.EntitlementSource;
import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.saga.SagaStatus;
import com.toefly.billing.domain.seat.SeatPool;
import com.toefly.billing.domain.subscription.Subscription;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What the Billing screen shows: the own subscription or the seat at a tutor, seats, the last saga. */
public record SubscriptionView(
        PlanTierCode tier,
        @Nullable EntitlementSource source,
        @Nullable Subscription subscription,
        @Nullable UUID tutorId,
        @Nullable SeatPool seats,
        @Nullable SagaStatus sagaStatus) {}
