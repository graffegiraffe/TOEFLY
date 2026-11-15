package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.in.AiOutputInvalidException;
import com.toefly.aigateway.application.port.in.AiProviderUnavailableException;
import com.toefly.aigateway.application.port.in.AiQuotaExhaustedException;
import com.toefly.aigateway.application.port.out.ProviderFailure;
import com.toefly.aigateway.application.port.out.QuotaStorePort;
import com.toefly.aigateway.application.port.out.ResiliencePort;
import com.toefly.aigateway.domain.call.AiCallOutcome;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.quota.QuotaDemand;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.routing.ModelRef;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Walks the candidates of a route (§2.5.3): skips open breakers, waits for quota within the priority's limit,
 * repeats a schema failure twice on the same model, repeats a 5xx once with jitter, empties the buckets on 429, and
 * moves on. When nothing answers, the reason picks the problem: schema → 422, quota → 429, else 503.
 */
@Component
class FallbackExecutor {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FallbackExecutor.class);

    static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(60);

    /** One call of one candidate; {@code violations} of the previous answer when the schema failed. */
    interface CandidateCall<T> {
        T call(ModelRef model, @Nullable InvalidOutput previous);
    }

    /** What the call needs and how long it may take on a candidate. */
    record Plan(
            AiUseCase useCase,
            List<ModelRef> candidates,
            Priority priority,
            @Nullable UUID userId,
            Instant deadline,
            Function<ModelRef, QuotaDemand> demand,
            Function<ModelRef, Duration> timeout) {}

    record Answer<T>(T value, ModelRef model, int depth) {}

    /** All candidates failed; carries the outcome for the journal and the last model tried. */
    static final class Exhausted extends RuntimeException {
        private final AiCallOutcome outcome;
        private final ModelRef lastModel;

        Exhausted(RuntimeException problem, AiCallOutcome outcome, ModelRef lastModel) {
            super(problem);
            this.outcome = outcome;
            this.lastModel = lastModel;
        }

        RuntimeException problem() {
            return (RuntimeException) getCause();
        }

        AiCallOutcome outcome() {
            return outcome;
        }

        ModelRef lastModel() {
            return lastModel;
        }
    }

    private final AdmissionController admission;
    private final ResiliencePort resilience;
    private final QuotaStorePort quotaStore;
    private final GatewaySettings settings;
    private final MeterRegistry meters;
    private final Clock clock;

    FallbackExecutor(
            AdmissionController admission,
            ResiliencePort resilience,
            QuotaStorePort quotaStore,
            GatewaySettings settings,
            MeterRegistry meters,
            Clock clock) {
        this.admission = admission;
        this.resilience = resilience;
        this.quotaStore = quotaStore;
        this.settings = settings;
        this.meters = meters;
        this.clock = clock;
    }

    <T> Answer<T> run(Plan plan, CandidateCall<T> call, Function<T, TokenUsage> usageOf) {
        List<String> violations = List.of();
        long quotaRetryMs = Long.MAX_VALUE;
        boolean quota = false;
        boolean schema = false;
        ModelRef last = plan.candidates().getFirst();
        for (int depth = 0; depth < plan.candidates().size(); depth++) {
            ModelRef model = plan.candidates().get(depth);
            last = model;
            if (!resilience.permits(model)) {
                meters.counter("toefly.ai.candidate.skipped", "model", model.toString(), "reason", "circuit-open")
                        .increment();
                continue;
            }
            InvalidOutput previous = null;
            for (int attempt = 0; attempt <= settings.schemaRepeats(); attempt++) {
                Instant now = clock.instant();
                Instant until =
                        earliest(plan.deadline(), now.plus(plan.priority().waitPerCandidate()));
                QuotaDemand demand = plan.demand().apply(model);
                AdmissionController.Result admitted =
                        admission.admit(model, demand, plan.priority(), plan.userId(), until);
                if (!admitted.admitted()) {
                    quota = true;
                    if (admitted.retryAfterMillis() > 0) {
                        quotaRetryMs = Math.min(quotaRetryMs, admitted.retryAfterMillis());
                    }
                    break;
                }
                try {
                    T value = callWithOneRepeat(model, plan.timeout().apply(model), call, previous);
                    admission.settle(admitted, actual(demand, usageOf.apply(value)));
                    if (depth > 0) {
                        meters.counter(
                                        "toefly.ai.fallback",
                                        "use_case",
                                        plan.useCase().name(),
                                        "depth",
                                        Integer.toString(depth),
                                        "model",
                                        model.toString())
                                .increment();
                    }
                    return new Answer<>(value, model, depth);
                } catch (InvalidOutput invalid) {
                    admission.settle(admitted, actual(demand, invalid.usage()));
                    schema = true;
                    violations = invalid.violations();
                    previous = invalid;
                } catch (ProviderFailure failure) {
                    admission.settle(admitted, new QuotaDemand(1, 0, 0, 0));
                    if (failure.kind() == ProviderFailure.Kind.RATE_LIMITED) {
                        Duration pause = failure.retryAfter() != null ? failure.retryAfter() : DEFAULT_RETRY_AFTER;
                        quotaStore.exhaust(model, pause);
                        quota = true;
                        quotaRetryMs = Math.min(quotaRetryMs, pause.toMillis());
                    }
                    LOG.warn(
                            "{} candidate {} failed ({}): {}",
                            plan.useCase(),
                            model,
                            failure.kind(),
                            failure.getMessage());
                    meters.counter(
                                    "toefly.ai.candidate.failed",
                                    "model",
                                    model.toString(),
                                    "kind",
                                    failure.kind().name())
                            .increment();
                    break;
                }
            }
        }
        if (schema) {
            throw new Exhausted(new AiOutputInvalidException(violations), AiCallOutcome.OUTPUT_INVALID, last);
        }
        if (quota) {
            long seconds = quotaRetryMs == Long.MAX_VALUE
                    ? DEFAULT_RETRY_AFTER.toSeconds()
                    : Math.max(1, (quotaRetryMs + 999) / 1000);
            throw new Exhausted(new AiQuotaExhaustedException(seconds), AiCallOutcome.QUOTA_EXHAUSTED, last);
        }
        throw new Exhausted(
                new AiProviderUnavailableException(
                        "Every model of the " + plan.useCase() + " route is unavailable.",
                        DEFAULT_RETRY_AFTER.toSeconds()),
                AiCallOutcome.PROVIDER_ERROR,
                last);
    }

    private <T> T callWithOneRepeat(
            ModelRef model, Duration timeout, CandidateCall<T> call, @Nullable InvalidOutput previous) {
        try {
            return resilience.execute(model, timeout, () -> call.call(model, previous));
        } catch (ProviderFailure failure) {
            if (failure.kind() != ProviderFailure.Kind.UNAVAILABLE) {
                throw failure;
            }
            sleepJitter();
            return resilience.execute(model, timeout, () -> call.call(model, previous));
        }
    }

    private void sleepJitter() {
        long base = settings.retryJitter().toMillis();
        if (base <= 0) {
            return;
        }
        try {
            Thread.sleep(base / 2 + ThreadLocalRandom.current().nextLong(base));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static QuotaDemand actual(QuotaDemand reserved, TokenUsage usage) {
        long tokens = usage.totalTokens() > 0 ? usage.totalTokens() : reserved.tokens();
        double audio = usage.audioSeconds() > 0 ? usage.audioSeconds() : reserved.audioSeconds();
        long characters = usage.characters() > 0 ? usage.characters() : reserved.characters();
        return new QuotaDemand(1, tokens, audio, characters);
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    static List<String> describe(List<ModelRef> models) {
        List<String> names = new ArrayList<>();
        models.forEach(m -> names.add(m.toString()));
        return names;
    }
}
