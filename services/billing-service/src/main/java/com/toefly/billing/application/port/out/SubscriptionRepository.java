package com.toefly.billing.application.port.out;

import com.toefly.billing.domain.plan.PlanTierCode;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import com.toefly.billing.domain.subscription.Subscription;
import com.toefly.billing.domain.subscription.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface SubscriptionRepository {

    /** The live (not EXPIRED) subscription of a user, INV-BIL-01. */
    Optional<Subscription> findLive(UUID userId);

    /** The same under {@code SELECT … FOR UPDATE}. */
    Optional<Subscription> lockLive(UUID userId);

    Optional<Subscription> findById(UUID id);

    Optional<Subscription> lockById(UUID id);

    Optional<Subscription> findByProviderRef(PaymentProviderCode provider, String providerSubscriptionId);

    List<Subscription> findByUser(UUID userId);

    /** Inserts or updates; optimistic lock by {@code version}. */
    Subscription save(Subscription subscription);

    /** Live subscriptions whose timer may be due: trial, cancel, grace or grant end before {@code horizon}. */
    List<UUID> findTimerCandidates(Instant horizon, int limit);

    Page page(
            @Nullable SubscriptionStatus status,
            @Nullable PlanTierCode tier,
            @Nullable PaymentProviderCode provider,
            @Nullable Instant beforeCreatedAt,
            @Nullable UUID beforeId,
            int limit);

    void deleteByUser(UUID userId);

    record Page(List<Subscription> items, boolean more) {}
}
