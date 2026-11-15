package com.toefly.aigateway.adapter.out.resilience;

import com.toefly.aigateway.application.port.out.ProviderFailure;
import com.toefly.aigateway.application.port.out.ResiliencePort;
import com.toefly.aigateway.domain.routing.ModelRef;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Resilience4j per {@code (provider, model)} (§2.5.3): circuit breaker over 20 calls, 50% failures, open for 60 s;
 * bulkheads from the settings (Ollama 2, Azure STT 1); the time limit runs the call on a virtual thread. Only
 * {@link ProviderFailure.Kind#UNAVAILABLE} counts as a failure: a 429 or a 400 says nothing about the health.
 */
public class Resilience4jAdapter implements ResiliencePort {

    private final CircuitBreakerRegistry breakers;
    private final BulkheadRegistry bulkheads;
    private final Map<String, Integer> concurrency;
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    public Resilience4jAdapter(MeterRegistry meters, Map<String, Integer> concurrency, Duration openFor) {
        this.concurrency = Map.copyOf(concurrency);
        this.breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(openFor)
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordException(error ->
                        error instanceof ProviderFailure failure && failure.kind() == ProviderFailure.Kind.UNAVAILABLE)
                .build());
        this.bulkheads = BulkheadRegistry.of(BulkheadConfig.custom()
                .maxConcurrentCalls(64)
                .maxWaitDuration(Duration.ofSeconds(30))
                .build());
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers).bindTo(meters);
        TaggedBulkheadMetrics.ofBulkheadRegistry(bulkheads).bindTo(meters);
    }

    @Override
    public boolean permits(ModelRef model) {
        CircuitBreaker breaker = breaker(model);
        return breaker.getState() != CircuitBreaker.State.OPEN && breaker.getState() != CircuitBreaker.State.FORCED_OPEN
                || breaker.tryAcquirePermission() && release(breaker);
    }

    @Override
    public <T> T execute(ModelRef model, Duration timeout, Supplier<T> call) {
        CircuitBreaker breaker = breaker(model);
        Bulkhead bulkhead = bulkhead(model);
        Supplier<T> limited = () -> withTimeout(model, timeout, call);
        try {
            return CircuitBreaker.decorateSupplier(breaker, Bulkhead.decorateSupplier(bulkhead, limited))
                    .get();
        } catch (CallNotPermittedException open) {
            throw ProviderFailure.unavailable(model + ": circuit open", open);
        } catch (BulkheadFullException full) {
            throw ProviderFailure.unavailable(model + ": too many calls at once", full);
        }
    }

    @Override
    public Map<String, String> states() {
        Map<String, String> states = new LinkedHashMap<>();
        breakers.getAllCircuitBreakers()
                .forEach(b -> states.put(b.getName(), b.getState().name()));
        return states;
    }

    private <T> T withTimeout(ModelRef model, Duration timeout, Supplier<T> call) {
        Future<T> future = threads.submit(call::get);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException late) {
            future.cancel(true);
            throw ProviderFailure.unavailable(model + ": no answer within " + timeout, late);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw ProviderFailure.unavailable(model + ": " + failed.getCause(), failed.getCause());
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw ProviderFailure.unavailable(model + ": interrupted", interrupted);
        }
    }

    private CircuitBreaker breaker(ModelRef model) {
        return breakers.circuitBreaker(model.toString());
    }

    private Bulkhead bulkhead(ModelRef model) {
        Integer limit = concurrency.getOrDefault(
                model.toString(), concurrency.get(model.provider().name()));
        if (limit == null) {
            return bulkheads.bulkhead(model.toString());
        }
        return bulkheads.bulkhead(
                model.toString(),
                BulkheadConfig.custom()
                        .maxConcurrentCalls(limit)
                        .maxWaitDuration(Duration.ofSeconds(30))
                        .build());
    }

    private static boolean release(CircuitBreaker breaker) {
        breaker.releasePermission();
        return true;
    }
}
