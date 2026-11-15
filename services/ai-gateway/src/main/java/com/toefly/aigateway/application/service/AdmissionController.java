package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.out.ProviderQuotaRepository;
import com.toefly.aigateway.application.port.out.QuotaStorePort;
import com.toefly.aigateway.domain.quota.Admission;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.quota.ProviderQuota;
import com.toefly.aigateway.domain.quota.QuotaDemand;
import com.toefly.aigateway.domain.quota.Reservation;
import com.toefly.aigateway.domain.routing.ModelRef;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Admits a call to a model within its quota (§2.5.2): a call that does not fit waits in the queue, re-checking the
 * buckets with a growing pause, until the deadline. A learner waits with at most two GRADING calls at once.
 */
@Component
class AdmissionController {

    static final long MIN_PAUSE_MS = 100;
    static final long MAX_PAUSE_MS = 1000;
    static final String UNLIMITED = "unlimited";

    /** Pause between checks; tests replace it with a fake clock. */
    interface Pause {
        void sleep(long millis) throws InterruptedException;
    }

    /** A reservation, or how long the caller should come back after. */
    record Result(@Nullable Reservation reservation, ProviderQuota quota, long retryAfterMillis) {
        boolean admitted() {
            return reservation != null;
        }
    }

    private final QuotaStorePort store;
    private final ProviderQuotaRepository quotas;
    private final GatewaySettings settings;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Pause pause;

    @Autowired
    AdmissionController(
            QuotaStorePort store,
            ProviderQuotaRepository quotas,
            GatewaySettings settings,
            Clock clock,
            MeterRegistry meters) {
        this(store, quotas, settings, clock, meters, Thread::sleep);
    }

    AdmissionController(
            QuotaStorePort store,
            ProviderQuotaRepository quotas,
            GatewaySettings settings,
            Clock clock,
            MeterRegistry meters,
            Pause pause) {
        this.store = store;
        this.quotas = quotas;
        this.settings = settings;
        this.clock = clock;
        this.meters = meters;
        this.pause = pause;
    }

    Result admit(ModelRef model, QuotaDemand demand, Priority priority, @Nullable UUID userId, Instant deadline) {
        ProviderQuota quota = quotas.find(model);
        if (quota.limits().isEmpty()) {
            return new Result(new Reservation(UNLIMITED, model, demand), quota, 0);
        }
        Admission first = store.reserve(quota, demand, priority, settings.reservePolicy());
        if (first.granted()) {
            return new Result(first.reservation(), quota, 0);
        }
        if (first.impossible()) {
            return new Result(null, quota, -1);
        }
        boolean queued = userId != null && priority == Priority.GRADING;
        if (queued && !store.enterQueue(userId, priority, settings.maxQueuedGradingPerUser())) {
            meters.counter("toefly.ai.queue.rejected", "model", model.toString())
                    .increment();
            return new Result(null, quota, first.waitMillis());
        }
        meters.counter("toefly.ai.queue.waits", "model", model.toString(), "priority", priority.name())
                .increment();
        try {
            long pauseMs = MIN_PAUSE_MS;
            long lastWait = first.waitMillis();
            while (true) {
                long left = deadline.toEpochMilli() - clock.millis();
                if (left <= 0) {
                    return new Result(null, quota, lastWait);
                }
                pause.sleep(Math.max(1, Math.min(Math.min(pauseMs, Math.max(lastWait, MIN_PAUSE_MS)), left)));
                Admission next = store.reserve(quota, demand, priority, settings.reservePolicy());
                if (next.granted()) {
                    return new Result(next.reservation(), quota, 0);
                }
                if (next.impossible()) {
                    return new Result(null, quota, -1);
                }
                lastWait = next.waitMillis();
                pauseMs = Math.min(MAX_PAUSE_MS, pauseMs * 2);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Result(null, quota, first.waitMillis());
        } finally {
            if (queued) {
                store.leaveQueue(userId, priority);
            }
        }
    }

    void settle(Result admitted, QuotaDemand actual) {
        Reservation reservation = admitted.reservation();
        if (reservation != null && !UNLIMITED.equals(reservation.id())) {
            store.settle(admitted.quota(), reservation, actual);
        }
    }
}
