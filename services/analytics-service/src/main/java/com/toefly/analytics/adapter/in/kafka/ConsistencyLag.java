package com.toefly.analytics.adapter.in.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Delay from the domain fact to its read model (NFR-PERF-08), metric {@code toefly.analytics.consistency.lag}. */
@Component
class ConsistencyLag {

    private final Timer lag;
    private final Clock clock;

    ConsistencyLag(MeterRegistry meters, Clock clock) {
        this.clock = clock;
        this.lag = Timer.builder("toefly.analytics.consistency.lag")
                .description("Time from the domain fact to its read model")
                .publishPercentiles(0.95, 0.99)
                .register(meters);
    }

    void applied(boolean applied, Instant occurredAt) {
        if (applied) {
            Duration delay = Duration.between(occurredAt, clock.instant());
            lag.record(delay.isNegative() ? Duration.ZERO : delay);
        }
    }
}
