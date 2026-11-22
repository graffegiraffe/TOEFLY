package com.toefly.billing.application.port.in;

import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.Subscription;
import com.toefly.billing.domain.subscription.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface AdminBillingUseCase {

    SubscriptionPage subscriptions(
            @Nullable SubscriptionStatus status,
            @Nullable PlanTierCode tier,
            @Nullable PaymentProviderCode provider,
            @Nullable Instant beforeCreatedAt,
            @Nullable UUID beforeId,
            int limit);

    /** Manual grant of a tier until a date (provider MANUAL). */
    EntitlementView grant(UUID userId, PlanTierCode tier, Instant until, String reason);

    record SubscriptionPage(List<Subscription> items, boolean more) {}
}
