package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.quota.Admission;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.quota.ProviderQuota;
import com.toefly.aigateway.domain.quota.QuotaDemand;
import com.toefly.aigateway.domain.quota.QuotaDimension;
import com.toefly.aigateway.domain.quota.Reservation;
import com.toefly.aigateway.domain.quota.ReservePolicy;
import com.toefly.aigateway.domain.routing.ModelRef;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Token buckets shared by all replicas (Valkey, INV-AIG-05). */
public interface QuotaStorePort {

    /** Checks and takes every dimension of the model at once. */
    Admission reserve(ProviderQuota quota, QuotaDemand demand, Priority priority, ReservePolicy policy);

    /** Returns or takes the difference between the reservation and the actual usage. */
    void settle(ProviderQuota quota, Reservation reservation, QuotaDemand actual);

    /** The provider answered 429: no reservations for the model until the pause ends. */
    void exhaust(ModelRef model, Duration pause);

    Map<QuotaDimension, Double> remaining(ProviderQuota quota);

    /** Takes a place in the queue of a learner; false if the learner already waits {@code max} times. */
    boolean enterQueue(UUID userId, Priority priority, int max);

    void leaveQueue(UUID userId, Priority priority);
}
