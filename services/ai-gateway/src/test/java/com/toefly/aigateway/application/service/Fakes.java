package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.out.AiCallRepository;
import com.toefly.aigateway.application.port.out.ChatProviderPort;
import com.toefly.aigateway.application.port.out.ChatRequest;
import com.toefly.aigateway.application.port.out.ChatResult;
import com.toefly.aigateway.application.port.out.GuardModelPort;
import com.toefly.aigateway.application.port.out.NerPort;
import com.toefly.aigateway.application.port.out.PromptRepository;
import com.toefly.aigateway.application.port.out.ProviderFailure;
import com.toefly.aigateway.application.port.out.ProviderQuotaRepository;
import com.toefly.aigateway.application.port.out.QuotaStorePort;
import com.toefly.aigateway.application.port.out.ResiliencePort;
import com.toefly.aigateway.application.port.out.ResponseCachePort;
import com.toefly.aigateway.application.port.out.UsageEventPublisherPort;
import com.toefly.aigateway.domain.call.AiCall;
import com.toefly.aigateway.domain.call.CallCursor;
import com.toefly.aigateway.domain.call.CallFilter;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.call.UsageRow;
import com.toefly.aigateway.domain.prompt.PromptStatus;
import com.toefly.aigateway.domain.prompt.PromptTemplate;
import com.toefly.aigateway.domain.prompt.PromptVersion;
import com.toefly.aigateway.domain.quota.Admission;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.quota.ProviderQuota;
import com.toefly.aigateway.domain.quota.QuotaDemand;
import com.toefly.aigateway.domain.quota.QuotaDimension;
import com.toefly.aigateway.domain.quota.QuotaLimit;
import com.toefly.aigateway.domain.quota.Reservation;
import com.toefly.aigateway.domain.quota.ReservePolicy;
import com.toefly.aigateway.domain.quota.TokenBucket;
import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.safety.GuardModelVerdict;
import com.toefly.aigateway.domain.safety.PiiSpan;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** In-memory ports for the tests of the application layer. */
final class Fakes {

    private Fakes() {}

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-15T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    static PlatformTransactionManager noTransactions() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {}

            @Override
            public void rollback(TransactionStatus status) {}
        };
    }

    /** The arithmetic of reserve.lua over {@link TokenBucket}, on the fake clock. */
    static final class InMemoryQuotaStore implements QuotaStorePort {
        final Map<String, TokenBucket> buckets = new HashMap<>();
        final Map<ModelRef, Instant> blocked = new HashMap<>();
        final Map<String, Integer> queues = new HashMap<>();
        final Clock clock;

        InMemoryQuotaStore(Clock clock) {
            this.clock = clock;
        }

        @Override
        public Admission reserve(ProviderQuota quota, QuotaDemand demand, Priority priority, ReservePolicy policy) {
            long now = clock.millis();
            Instant until = blocked.get(quota.model());
            if (until != null && until.toEpochMilli() > now) {
                return Admission.waitFor(until.toEpochMilli() - now);
            }
            long wait = 0;
            Map<QuotaLimit, TokenBucket> refilled = new HashMap<>();
            for (QuotaLimit limit : quota.limits()) {
                TokenBucket bucket = buckets.getOrDefault(
                                key(quota.model(), limit.dimension()), TokenBucket.full(limit, now))
                        .refill(limit, now);
                long w = bucket.waitFor(limit, demand.need(limit.dimension()), policy.floor(limit, priority));
                if (w < 0) {
                    return Admission.waitFor(-1);
                }
                wait = Math.max(wait, w);
                refilled.put(limit, bucket);
            }
            if (wait > 0) {
                refilled.forEach((l, b) -> buckets.put(key(quota.model(), l.dimension()), b));
                return Admission.waitFor(wait);
            }
            refilled.forEach(
                    (l, b) -> buckets.put(key(quota.model(), l.dimension()), b.take(demand.need(l.dimension()))));
            return Admission.granted(new Reservation(UUID.randomUUID().toString(), quota.model(), demand));
        }

        @Override
        public void settle(ProviderQuota quota, Reservation reservation, QuotaDemand actual) {
            for (QuotaLimit limit : quota.limits()) {
                String key = key(quota.model(), limit.dimension());
                TokenBucket bucket = buckets.get(key);
                double delta = reservation.demand().need(limit.dimension()) - actual.need(limit.dimension());
                if (bucket != null) {
                    buckets.put(
                            key,
                            new TokenBucket(
                                    Math.min(limit.capacity(), bucket.tokens() + delta), bucket.updatedAtMillis()));
                }
            }
        }

        @Override
        public void exhaust(ModelRef model, Duration pause) {
            blocked.put(model, clock.instant().plus(pause));
        }

        @Override
        public Map<QuotaDimension, Double> remaining(ProviderQuota quota) {
            Map<QuotaDimension, Double> out = new EnumMap<>(QuotaDimension.class);
            quota.limits()
                    .forEach(l -> out.put(
                            l.dimension(),
                            buckets.getOrDefault(key(quota.model(), l.dimension()), TokenBucket.full(l, 0))
                                    .tokens()));
            return out;
        }

        @Override
        public boolean enterQueue(UUID userId, Priority priority, int max) {
            int now = queues.merge(userId + ":" + priority, 1, Integer::sum);
            if (now > max) {
                queues.merge(userId + ":" + priority, -1, Integer::sum);
                return false;
            }
            return true;
        }

        @Override
        public void leaveQueue(UUID userId, Priority priority) {
            queues.merge(userId + ":" + priority, -1, Integer::sum);
        }

        static String key(ModelRef model, QuotaDimension dimension) {
            return model + ":" + dimension;
        }
    }

    static final class Quotas implements ProviderQuotaRepository {
        final Map<ModelRef, ProviderQuota> quotas = new HashMap<>();

        @Override
        public ProviderQuota find(ModelRef model) {
            return quotas.getOrDefault(model, ProviderQuota.unlimited(model));
        }

        @Override
        public List<ProviderQuota> all() {
            return List.copyOf(quotas.values());
        }

        @Override
        public void save(ProviderQuota quota) {
            quotas.put(quota.model(), quota);
        }
    }

    /** Breakers that are open only for the models listed. */
    static final class Resilience implements ResiliencePort {
        final java.util.Set<ModelRef> open = new java.util.HashSet<>();

        @Override
        public boolean permits(ModelRef model) {
            return !open.contains(model);
        }

        @Override
        public <T> T execute(ModelRef model, Duration timeout, Supplier<T> call) {
            return call.get();
        }

        @Override
        public Map<String, String> states() {
            Map<String, String> states = new HashMap<>();
            open.forEach(m -> states.put(m.toString(), "OPEN"));
            return states;
        }
    }

    /** Answers scripted per model; records every request it saw. */
    static final class ScriptedProvider implements ChatProviderPort {
        final AiProvider provider;
        final List<ChatRequest> requests = new ArrayList<>();
        final Map<String, Deque<Function<ChatRequest, ChatResult>>> scripts = new HashMap<>();
        Function<ChatRequest, ChatResult> fallback = r -> answer("ok");

        ScriptedProvider(AiProvider provider) {
            this.provider = provider;
        }

        ScriptedProvider then(String model, Function<ChatRequest, ChatResult> step) {
            scripts.computeIfAbsent(model, m -> new ArrayDeque<>()).add(step);
            return this;
        }

        @Override
        public AiProvider provider() {
            return provider;
        }

        @Override
        public ChatResult call(ChatRequest request) {
            requests.add(request);
            Deque<Function<ChatRequest, ChatResult>> script =
                    scripts.get(request.model().model());
            Function<ChatRequest, ChatResult> step = script == null || script.isEmpty() ? fallback : script.poll();
            return step.apply(request);
        }

        @Override
        public ChatResult stream(ChatRequest request, Consumer<String> onDelta) {
            ChatResult result = call(request);
            if (result.content() != null) {
                for (String part : result.content().split("(?<=\\s)")) {
                    onDelta.accept(part);
                }
            }
            return result;
        }
    }

    static ChatResult answer(String content) {
        return new ChatResult(content, List.of(), "stop", TokenUsage.tokens(100, 20), Map.of());
    }

    static Function<ChatRequest, ChatResult> failing(ProviderFailure.Kind kind, Duration retryAfter) {
        return r -> {
            throw new ProviderFailure(kind, "scripted " + kind, retryAfter, null);
        };
    }

    static final class Prompts implements PromptRepository {
        final Map<String, PromptTemplate> templates = new HashMap<>();
        final Map<String, PromptVersion> versions = new HashMap<>();

        @Override
        public Optional<PromptTemplate> template(String key) {
            return Optional.ofNullable(templates.get(key));
        }

        @Override
        public List<PromptTemplate> templates() {
            return List.copyOf(templates.values());
        }

        @Override
        public Optional<PromptVersion> version(String key, int version) {
            return Optional.ofNullable(versions.get(key + "@" + version));
        }

        @Override
        public List<PromptVersion> versions(String key) {
            return versions.values().stream().filter(v -> v.key().equals(key)).toList();
        }

        @Override
        public void save(PromptTemplate template) {
            templates.put(template.key(), template);
        }

        @Override
        public void add(PromptVersion version) {
            versions.put(version.id(), version);
        }

        @Override
        public void updateStatus(String key, int version, PromptStatus status) {
            versions.computeIfPresent(key + "@" + version, (k, v) -> v.withStatus(status));
        }
    }

    static final class Calls implements AiCallRepository, UsageEventPublisherPort {
        final List<AiCall> saved = new ArrayList<>();
        final List<AiCall> published = new ArrayList<>();

        @Override
        public void save(AiCall call) {
            saved.add(call);
        }

        @Override
        public void usageRecorded(AiCall call) {
            published.add(call);
        }

        @Override
        public List<AiCall> page(CallFilter filter, CallCursor after, int limit) {
            return saved;
        }

        @Override
        public List<UsageRow> usage(UUID userId, LocalDate from, LocalDate to) {
            return List.of();
        }

        @Override
        public int deleteUser(UUID userId) {
            return 0;
        }

        @Override
        public int clearPayloads(Instant before) {
            return 0;
        }

        @Override
        public int deleteOlderThan(Instant before) {
            return 0;
        }
    }

    static final class Cache implements ResponseCachePort {
        final Map<String, CachedAnswer> answers = new HashMap<>();

        @Override
        public Optional<CachedAnswer> get(String key) {
            return Optional.ofNullable(answers.get(key));
        }

        @Override
        public void put(String key, CachedAnswer answer) {
            answers.put(key, answer);
        }
    }

    static final class Ner implements NerPort {
        boolean down;
        List<PiiSpan> spans = List.of();

        @Override
        public List<PiiSpan> find(String text) {
            if (down) {
                throw ProviderFailure.unavailable("ner down", null);
            }
            return spans;
        }
    }

    static final class Guard implements GuardModelPort {
        boolean down;
        String answer = "safe";

        @Override
        public GuardModelVerdict classify(String text, boolean output) {
            if (down) {
                throw ProviderFailure.unavailable("guard down", null);
            }
            return GuardModelVerdict.parseLlamaGuard(answer, "llama-guard3:1b");
        }
    }

    static ProviderQuota tpm(ModelRef model, long tpm) {
        return new ProviderQuota(model, List.of(new QuotaLimit(QuotaDimension.TPM, tpm)));
    }
}
