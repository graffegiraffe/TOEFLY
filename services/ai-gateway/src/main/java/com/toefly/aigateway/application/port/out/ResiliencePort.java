package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.routing.ModelRef;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Circuit breaker, bulkhead and time limit per model (§2.5.3). {@link #execute} records the outcome: only
 * {@link ProviderFailure.Kind#UNAVAILABLE} counts as a failure.
 */
public interface ResiliencePort {

    boolean permits(ModelRef model);

    <T> T execute(ModelRef model, Duration timeout, Supplier<T> call);

    /** State of every breaker: CLOSED, OPEN, HALF_OPEN. */
    Map<String, String> states();
}
