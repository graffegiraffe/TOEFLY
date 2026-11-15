package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.in.AiGuardBlockedException;
import com.toefly.aigateway.application.port.in.CompleteUseCase;
import com.toefly.aigateway.application.port.in.CompletionCommand;
import com.toefly.aigateway.application.port.in.CompletionResult;
import com.toefly.aigateway.application.port.in.PromptNotFoundException;
import com.toefly.aigateway.application.port.in.PromptTooLargeException;
import com.toefly.aigateway.application.port.out.ChatProviderPort;
import com.toefly.aigateway.application.port.out.ChatRequest;
import com.toefly.aigateway.application.port.out.ChatResult;
import com.toefly.aigateway.application.port.out.OutputSchemaPort;
import com.toefly.aigateway.application.port.out.PromptRendererPort;
import com.toefly.aigateway.application.port.out.PromptRepository;
import com.toefly.aigateway.application.port.out.ProviderFailure;
import com.toefly.aigateway.application.port.out.ResponseCachePort;
import com.toefly.aigateway.application.port.out.ResponseCachePort.CachedAnswer;
import com.toefly.aigateway.application.port.out.RoutingTablePort;
import com.toefly.aigateway.application.port.out.TokenCounterPort;
import com.toefly.aigateway.domain.call.AiCall;
import com.toefly.aigateway.domain.call.AiCallOutcome;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.chat.ChatMessage;
import com.toefly.aigateway.domain.prompt.PromptRuleException;
import com.toefly.aigateway.domain.prompt.PromptTemplate;
import com.toefly.aigateway.domain.prompt.PromptVersion;
import com.toefly.aigateway.domain.quota.QuotaDemand;
import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.routing.ModelRoute;
import com.toefly.aigateway.domain.routing.RouteParams;
import com.toefly.aigateway.domain.safety.GuardAssessment;
import com.toefly.aigateway.domain.safety.MaskingSession;
import com.toefly.aigateway.domain.safety.UntrustedText;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Templated generation of the workers (§2.2.1, pipeline §3.1): prompt version → masking and input guard of the
 * untrusted variables → nonce tags → render → size check → cache → fallback chain with schema checks → output
 * guard → unmasking → journal.
 */
@Service
class GenerationService implements CompleteUseCase {

    static final String SCHEMA_INSTRUCTION =
            "\n\nAnswer with one JSON object only, no prose and no code fences. It must match this JSON Schema:\n";

    private final PromptRepository prompts;
    private final RoutingTablePort routing;
    private final ProviderRegistry providers;
    private final SafetyService safety;
    private final FallbackExecutor fallback;
    private final PromptRendererPort renderer;
    private final TokenCounterPort tokens;
    private final OutputSchemaPort schemas;
    private final ResponseCachePort cache;
    private final CallJournal journal;
    private final ProviderLimits limits;
    private final Clock clock;

    GenerationService(
            PromptRepository prompts,
            RoutingTablePort routing,
            ProviderRegistry providers,
            SafetyService safety,
            FallbackExecutor fallback,
            PromptRendererPort renderer,
            TokenCounterPort tokens,
            OutputSchemaPort schemas,
            ResponseCachePort cache,
            CallJournal journal,
            ProviderLimits limits,
            Clock clock) {
        this.prompts = prompts;
        this.routing = routing;
        this.providers = providers;
        this.safety = safety;
        this.fallback = fallback;
        this.renderer = renderer;
        this.tokens = tokens;
        this.schemas = schemas;
        this.cache = cache;
        this.journal = journal;
        this.limits = limits;
        this.clock = clock;
    }

    @Override
    public CompletionResult complete(CompletionCommand command) {
        return run(command, null);
    }

    @Override
    public CompletionResult stream(CompletionCommand command, Consumer<String> deltas) {
        return run(command, deltas);
    }

    private CompletionResult run(CompletionCommand command, @Nullable Consumer<String> deltas) {
        Instant started = clock.instant();
        String callId = journal.nextId();
        PromptVersion version = version(command);
        if (deltas != null && version.structured()) {
            throw new PromptRuleException(
                    "validation-failed", version.id() + " returns JSON and cannot be streamed; use chat/completions.");
        }
        ModelRoute route = routing.current()
                .route(command.useCase(), command.passTag())
                .orElseThrow(() ->
                        new PromptRuleException("validation-failed", "no route for use case " + command.useCase()));
        RouteParams params = route.params()
                .with(
                        version.params().temperature(),
                        version.params().maxOutputTokens(),
                        version.params().reasoningEffort());
        List<ModelRef> candidates = route.order(override(command.modelOverride()), excludedModel(command));
        MaskingSession session = new MaskingSession();
        PreparedPrompt prepared = prepare(version, command, session);
        GuardAssessment inputGuard = prepared.inputGuard();
        List<String> flags = inputGuard == null ? List.of() : inputGuard.integrityFlags();
        String requestMasked = schemas.write(
                Map.of("variables", prepared.maskedVariables(), "messages", describe(prepared.messages())));
        Entry base = new Entry(command, version, callId, requestMasked, started);
        if (inputGuard != null && !inputGuard.allowed() && version.guardPolicy().blocks()) {
            journal.record(
                    callId,
                    base.failed(
                            candidates.getFirst(),
                            AiCallOutcome.GUARD_BLOCKED,
                            "ai-guard-blocked",
                            verdict(inputGuard),
                            clock));
            throw new AiGuardBlockedException(inputGuard, SafetyService.refusalCode(inputGuard));
        }
        int estimated = tokens.estimate(text(prepared.messages()), candidates.getFirst());
        if (estimated > version.maxInputTokens()) {
            throw new PromptTooLargeException(estimated, version.maxInputTokens());
        }
        boolean cacheable = route.params().cacheable() && command.cachePolicy().reads();
        String cacheKey = cacheable ? cacheKey(command, version, candidates.getFirst(), prepared) : null;
        if (cacheKey != null) {
            Optional<CachedAnswer> hit = cache.get(cacheKey);
            if (hit.isPresent()) {
                return fromCache(command, version, base, hit.get(), session, flags);
            }
        }
        long maxWait = command.maxWaitMs() != null
                ? command.maxWaitMs()
                : command.priority().waitPerCandidate().toMillis() * candidates.size();
        FallbackExecutor.Plan plan = new FallbackExecutor.Plan(
                command.useCase(),
                candidates,
                command.priority(),
                command.userId(),
                started.plusMillis(maxWait),
                model -> QuotaDemand.tokens(
                        tokens.estimate(text(prepared.messages()), model) + (long) params.maxOutputTokens()),
                model -> timeout(model, params));
        AtomicBoolean emitted = new AtomicBoolean(false);
        FallbackExecutor.Answer<Generated> answer;
        try {
            answer = fallback.run(
                    plan,
                    (model, previous) -> generate(model, version, params, prepared, previous, deltas, session, emitted),
                    Generated::usage);
        } catch (FallbackExecutor.Exhausted exhausted) {
            journal.record(
                    callId,
                    base.failed(
                            exhausted.lastModel(),
                            exhausted.outcome(),
                            slug(exhausted.outcome()),
                            verdict(inputGuard),
                            clock));
            throw exhausted.problem();
        } catch (StreamBroken broken) {
            journal.record(
                    callId,
                    base.failed(
                            candidates.getFirst(),
                            AiCallOutcome.PROVIDER_ERROR,
                            "ai-provider-unavailable",
                            verdict(inputGuard),
                            clock));
            throw new com.toefly.aigateway.application.port.in.AiProviderUnavailableException(
                    "The stream broke after it started.", FallbackExecutor.DEFAULT_RETRY_AFTER.toSeconds());
        }
        Generated generated = answer.value();
        GuardAssessment outputGuard = null;
        if (version.guardPolicy().checksOutput() && generated.text() != null) {
            outputGuard = safety.check(generated.text(), version.guardPolicy(), true);
            if (!outputGuard.allowed() && version.guardPolicy().blocks()) {
                journal.record(
                        callId,
                        base.failed(
                                answer.model(),
                                AiCallOutcome.GUARD_BLOCKED,
                                "ai-guard-blocked",
                                verdict(outputGuard),
                                clock));
                throw new AiGuardBlockedException(outputGuard, SafetyService.refusalCode(outputGuard));
            }
            flags = merge(flags, outputGuard.integrityFlags());
        }
        boolean degraded = answer.depth() > 0
                && answer.model().provider() == AiProvider.OLLAMA
                && command.useCase().grades();
        AiCall call = journal.record(
                callId,
                base.succeeded(
                        answer.model(), answer.depth(), degraded, generated, verdict(inputGuard, outputGuard), clock));
        if (cacheKey != null && command.cachePolicy().writes()) {
            cache.put(
                    cacheKey,
                    new CachedAnswer(
                            generated.masked(),
                            answer.model().provider().name(),
                            answer.model().model(),
                            version.key(),
                            version.version()));
        }
        return new CompletionResult(
                generated.output(),
                generated.output() == null ? generated.text() : null,
                call.stamp(),
                generated.usage(),
                call.latencyMs(),
                flags);
    }

    /** One call of one candidate; throws {@link InvalidOutput} when the JSON does not match. */
    private Generated generate(
            ModelRef model,
            PromptVersion version,
            RouteParams params,
            PreparedPrompt prepared,
            @Nullable InvalidOutput previous,
            @Nullable Consumer<String> deltas,
            MaskingSession session,
            AtomicBoolean emitted) {
        List<ChatMessage> messages = new ArrayList<>(prepared.messages());
        if (previous != null) {
            messages.add(ChatMessage.assistant(previous.answer()));
            messages.add(ChatMessage.user("Your previous answer did not match the JSON Schema: "
                    + String.join("; ", previous.violations())
                    + ". Answer again with only the corrected JSON object."));
        }
        ChatRequest request = new ChatRequest(model, messages, params, version.outputSchema(), List.of(), null);
        ChatProviderPort provider = providers.chat(model.provider());
        ChatResult result;
        if (deltas == null) {
            result = provider.call(request);
        } else {
            var unmasker = new com.toefly.aigateway.domain.safety.StreamingUnmasker(session);
            try {
                result = provider.stream(request, delta -> {
                    emitted.set(true);
                    String ready = unmasker.accept(delta);
                    if (!ready.isEmpty()) {
                        deltas.accept(ready);
                    }
                });
            } catch (ProviderFailure failure) {
                if (emitted.get()) {
                    throw new StreamBroken(failure);
                }
                throw failure;
            }
            String rest = unmasker.finish();
            if (!rest.isEmpty()) {
                deltas.accept(rest);
            }
        }
        limits.apply(model, result.reportedLimits());
        String content = result.content() == null ? "" : result.content();
        if (version.outputSchema() == null) {
            return new Generated(null, session.unmask(content), content, result.usage());
        }
        Object parsed = schemas.parse(content);
        if (parsed == null) {
            throw new InvalidOutput(List.of("the answer is not a JSON object"), content, result.usage());
        }
        List<String> violations = schemas.validate(version.outputSchema(), parsed);
        if (!violations.isEmpty()) {
            throw new InvalidOutput(violations, content, result.usage());
        }
        return new Generated(schemas.mapStrings(parsed, session::unmask), null, content, result.usage());
    }

    private PreparedPrompt prepare(PromptVersion version, CompletionCommand command, MaskingSession session) {
        String nonce = UntrustedText.nonce();
        Set<String> allow = Set.copyOf(command.allowList());
        Map<String, Object> rendered = new LinkedHashMap<>();
        Map<String, Object> masked = new TreeMap<>();
        StringBuilder untrusted = new StringBuilder();
        for (Map.Entry<String, Object> variable : command.variables().entrySet()) {
            String name = variable.getKey();
            Object value = variable.getValue();
            if (version.untrusted().contains(name)) {
                String text = String.valueOf(value);
                untrusted.append(text).append('\n');
                String maskedText = safety.mask(text, session, command.piiHints(), allow);
                masked.put(name, maskedText);
                rendered.put(name, UntrustedText.wrap(tag(name), nonce, maskedText));
            } else {
                String plain = plain(value);
                masked.put(name, plain);
                rendered.put(name, plain);
            }
        }
        rendered.put("nonce", nonce);
        GuardAssessment guard = null;
        if (version.guardPolicy().checksInput() && !untrusted.isEmpty()) {
            guard = safety.check(untrusted.toString(), version.guardPolicy(), false);
        }
        String system = renderer.render(version.system(), rendered);
        if (version.outputSchema() != null) {
            system = system + SCHEMA_INSTRUCTION + version.outputSchema();
        }
        List<ChatMessage> messages =
                List.of(ChatMessage.system(system), ChatMessage.user(renderer.render(version.user(), rendered)));
        return new PreparedPrompt(messages, masked, guard);
    }

    private PromptVersion version(CompletionCommand command) {
        PromptTemplate template = prompts.template(command.promptKey())
                .orElseThrow(() -> new PromptNotFoundException(
                        command.promptKey(), "No prompt " + command.promptKey() + " in the registry."));
        Integer number =
                command.promptVersion() != null ? command.promptVersion() : template.versionFor(command.userId());
        if (number == null) {
            throw new PromptNotFoundException(command.promptKey(), command.promptKey() + " has no active version.");
        }
        PromptVersion version = prompts.version(command.promptKey(), number)
                .orElseThrow(() -> new PromptNotFoundException(
                        command.promptKey(), "No version " + number + " of " + command.promptKey() + "."));
        if (version.useCase() != command.useCase()) {
            throw new PromptRuleException(
                    "validation-failed",
                    version.id() + " belongs to use case " + version.useCase() + ", not " + command.useCase());
        }
        return version;
    }

    private CompletionResult fromCache(
            CompletionCommand command,
            PromptVersion version,
            Entry base,
            CachedAnswer hit,
            MaskingSession session,
            List<String> flags) {
        ModelRef model = new ModelRef(AiProvider.valueOf(hit.provider()), hit.model());
        Object output = null;
        String text = null;
        if (version.outputSchema() != null) {
            Object parsed = schemas.parse(hit.content());
            output = parsed == null ? null : schemas.mapStrings(parsed, session::unmask);
        } else {
            text = session.unmask(hit.content());
        }
        Generated cached = new Generated(output, text, hit.content(), TokenUsage.NONE);
        AiCall call = journal.record(base.callId, base.cached(model, cached, clock));
        return new CompletionResult(output, text, call.stamp(), TokenUsage.NONE, call.latencyMs(), flags);
    }

    private String cacheKey(CompletionCommand command, PromptVersion version, ModelRef first, PreparedPrompt prepared) {
        Object lang = command.variables().getOrDefault("explanationLanguage", "");
        String material = String.join(
                "|",
                command.useCase().name(),
                version.key(),
                Integer.toString(version.version()),
                first.family(),
                schemas.write(prepared.maskedVariables()),
                String.valueOf(lang));
        return sha256(material);
    }

    static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Duration timeout(ModelRef model, RouteParams params) {
        return model.provider() == AiProvider.OLLAMA ? Duration.ofSeconds(180) : params.timeout();
    }

    private @Nullable ModelRef override(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (AiProvider provider : AiProvider.values()) {
            if (value.startsWith(provider.name() + ":")) {
                return ModelRef.parse(value);
            }
        }
        return new ModelRef(AiProvider.GROQ, value);
    }

    private static @Nullable String excludedModel(CompletionCommand command) {
        Object generator = command.variables().get("generatorModel");
        return generator == null ? null : generator.toString();
    }

    private String plain(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(item -> "- " + plainScalar(item)).collect(Collectors.joining("\n"));
        }
        return plainScalar(value);
    }

    private String plainScalar(Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return schemas.write(value);
        }
        return String.valueOf(value);
    }

    static String tag(String variable) {
        String snake = variable.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
        return snake.equals("student_text") ? "student_response" : snake;
    }

    private static String text(List<ChatMessage> messages) {
        return messages.stream()
                .map(m -> m.content() == null ? "" : m.content())
                .collect(Collectors.joining("\n"));
    }

    private static List<Map<String, String>> describe(List<ChatMessage> messages) {
        List<Map<String, String>> out = new ArrayList<>();
        for (ChatMessage message : messages) {
            out.add(Map.of(
                    "role",
                    message.role().name().toLowerCase(java.util.Locale.ROOT),
                    "content",
                    message.content() == null ? "" : message.content()));
        }
        return out;
    }

    private static @Nullable String verdict(@Nullable GuardAssessment input) {
        return input == null ? null : "input:" + input.verdict() + input.categories();
    }

    private static @Nullable String verdict(@Nullable GuardAssessment input, @Nullable GuardAssessment output) {
        if (output == null) {
            return verdict(input);
        }
        String in = verdict(input);
        return (in == null ? "" : in + ";") + "output:" + output.verdict() + output.categories();
    }

    private static List<String> merge(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        b.stream().filter(flag -> !all.contains(flag)).forEach(all::add);
        return all;
    }

    static String slug(AiCallOutcome outcome) {
        return switch (outcome) {
            case QUOTA_EXHAUSTED -> "ai-quota-exhausted";
            case OUTPUT_INVALID -> "ai-output-invalid";
            case GUARD_BLOCKED -> "ai-guard-blocked";
            default -> "ai-provider-unavailable";
        };
    }

    /** A successful answer: parsed JSON or unmasked text, and the masked answer for the journal and the cache. */
    record Generated(@Nullable Object output, @Nullable String text, String masked, TokenUsage usage) {}

    /** The parts of a journal entry known before the answer. */
    private record Entry(
            CompletionCommand command, PromptVersion version, String callId, String requestMasked, Instant started) {

        CallJournal.Entry succeeded(
                ModelRef model, int depth, boolean degraded, Generated answer, @Nullable String verdict, Clock clock) {
            return entry(
                    model,
                    depth,
                    degraded,
                    false,
                    answer.masked(),
                    answer.usage(),
                    verdict,
                    AiCallOutcome.SUCCESS,
                    null,
                    clock);
        }

        CallJournal.Entry cached(ModelRef model, Generated answer, Clock clock) {
            return entry(
                    model,
                    0,
                    false,
                    true,
                    answer.masked(),
                    TokenUsage.NONE,
                    null,
                    AiCallOutcome.CACHE_HIT,
                    null,
                    clock);
        }

        CallJournal.Entry failed(
                ModelRef model, AiCallOutcome outcome, String error, @Nullable String verdict, Clock clock) {
            return entry(model, 0, false, false, null, TokenUsage.NONE, verdict, outcome, error, clock);
        }

        private CallJournal.Entry entry(
                ModelRef model,
                int depth,
                boolean degraded,
                boolean cacheHit,
                @Nullable String response,
                TokenUsage usage,
                @Nullable String verdict,
                AiCallOutcome outcome,
                @Nullable String error,
                Clock clock) {
            return new CallJournal.Entry(
                    command.callerService(),
                    command.useCase(),
                    command.feature(),
                    command.priority(),
                    command.userId(),
                    version.key(),
                    version.version(),
                    model,
                    depth,
                    degraded,
                    cacheHit,
                    requestMasked,
                    response,
                    usage,
                    Duration.between(started, clock.instant()).toMillis(),
                    verdict,
                    outcome,
                    error,
                    command.correlationId());
        }
    }
}
