package com.toefly.aigateway.application.service;

import com.toefly.aigateway.application.port.in.AiGuardBlockedException;
import com.toefly.aigateway.application.port.in.AiProviderUnavailableException;
import com.toefly.aigateway.application.port.in.ChatFacadeUseCase;
import com.toefly.aigateway.application.port.in.FacadeCommand;
import com.toefly.aigateway.application.port.in.FacadeResult;
import com.toefly.aigateway.application.port.out.ChatRequest;
import com.toefly.aigateway.application.port.out.ChatResult;
import com.toefly.aigateway.application.port.out.ConversationMaskingPort;
import com.toefly.aigateway.application.port.out.OutputSchemaPort;
import com.toefly.aigateway.application.port.out.ProviderFailure;
import com.toefly.aigateway.application.port.out.RoutingTablePort;
import com.toefly.aigateway.application.port.out.TokenCounterPort;
import com.toefly.aigateway.domain.call.AiCall;
import com.toefly.aigateway.domain.call.AiCallOutcome;
import com.toefly.aigateway.domain.call.TokenUsage;
import com.toefly.aigateway.domain.chat.ChatMessage;
import com.toefly.aigateway.domain.chat.ToolCall;
import com.toefly.aigateway.domain.prompt.PromptRuleException;
import com.toefly.aigateway.domain.quota.QuotaDemand;
import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.AiUseCase;
import com.toefly.aigateway.domain.routing.ModelRef;
import com.toefly.aigateway.domain.routing.ModelRoute;
import com.toefly.aigateway.domain.routing.RouteParams;
import com.toefly.aigateway.domain.safety.GuardAssessment;
import com.toefly.aigateway.domain.safety.GuardPolicy;
import com.toefly.aigateway.domain.safety.MaskingSession;
import com.toefly.aigateway.domain.safety.StreamingUnmasker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * OpenAI-compatible facade for the agent loop of tutor-agent-service (§2.2.2): the loop and its tools stay with the
 * caller; masking (stable per conversation), guard, routing, quotas and accounting happen here.
 */
@Service
class ChatFacadeService implements ChatFacadeUseCase {

    private static final Set<AiUseCase> CHAT =
            EnumSet.of(AiUseCase.TUTOR_CHAT, AiUseCase.TUTOR_ROUTER, AiUseCase.TUTOR_ESCALATION, AiUseCase.HINT);

    private final RoutingTablePort routing;
    private final ProviderRegistry providers;
    private final SafetyService safety;
    private final FallbackExecutor fallback;
    private final TokenCounterPort tokens;
    private final ConversationMaskingPort conversations;
    private final OutputSchemaPort json;
    private final CallJournal journal;
    private final Clock clock;

    ChatFacadeService(
            RoutingTablePort routing,
            ProviderRegistry providers,
            SafetyService safety,
            FallbackExecutor fallback,
            TokenCounterPort tokens,
            ConversationMaskingPort conversations,
            OutputSchemaPort json,
            CallJournal journal,
            Clock clock) {
        this.routing = routing;
        this.providers = providers;
        this.safety = safety;
        this.fallback = fallback;
        this.tokens = tokens;
        this.conversations = conversations;
        this.json = json;
        this.journal = journal;
        this.clock = clock;
    }

    @Override
    public FacadeResult complete(FacadeCommand command) {
        return run(command, null);
    }

    @Override
    public FacadeResult stream(FacadeCommand command, Consumer<String> deltas) {
        return run(command, deltas);
    }

    private FacadeResult run(FacadeCommand command, @Nullable Consumer<String> deltas) {
        Instant started = clock.instant();
        String callId = journal.nextId();
        ModelRoute route = routing.current()
                .route(command.useCase(), null)
                .orElseThrow(() ->
                        new PromptRuleException("validation-failed", "no route for use case " + command.useCase()));
        RouteParams params = route.params().with(command.temperature(), command.maxTokens(), null);
        GuardPolicy policy = CHAT.contains(command.useCase()) ? GuardPolicy.CHAT : GuardPolicy.NONE;
        MaskingSession session =
                command.conversationId() == null ? new MaskingSession() : conversations.load(command.conversationId());
        List<ChatMessage> masked = new ArrayList<>();
        for (ChatMessage message : command.messages()) {
            masked.add(mask(message, session, command.piiHints()));
        }
        String requestMasked = json.write(Map.of("messages", describe(masked)));
        GuardAssessment input = null;
        String lastUser = lastUserText(command.messages());
        if (policy.checksInput() && lastUser != null) {
            input = safety.check(lastUser, policy, false);
            if (!input.allowed()) {
                record(
                        callId,
                        command,
                        route.candidates().getFirst(),
                        0,
                        false,
                        requestMasked,
                        null,
                        TokenUsage.NONE,
                        started,
                        "input:" + input.verdict() + input.categories(),
                        AiCallOutcome.GUARD_BLOCKED,
                        "ai-guard-blocked");
                throw new AiGuardBlockedException(input, SafetyService.refusalCode(input));
            }
        }
        List<ModelRef> candidates = route.order(null, null);
        String promptText =
                masked.stream().map(m -> m.content() == null ? "" : m.content()).collect(Collectors.joining("\n"));
        FallbackExecutor.Plan plan = new FallbackExecutor.Plan(
                command.useCase(),
                candidates,
                command.priority(),
                command.userId(),
                started.plus(command.priority().waitPerCandidate().multipliedBy(candidates.size())),
                model -> QuotaDemand.tokens(tokens.estimate(promptText, model) + (long) params.maxOutputTokens()),
                model -> model.provider() == AiProvider.OLLAMA ? Duration.ofSeconds(20) : params.timeout());
        AtomicBoolean emitted = new AtomicBoolean(false);
        StreamingUnmasker unmasker = new StreamingUnmasker(session);
        FallbackExecutor.Answer<ChatResult> answer;
        try {
            answer = fallback.run(
                    plan,
                    (model, previous) -> call(model, masked, params, command, deltas, unmasker, emitted),
                    ChatResult::usage);
        } catch (FallbackExecutor.Exhausted exhausted) {
            record(
                    callId,
                    command,
                    exhausted.lastModel(),
                    0,
                    false,
                    requestMasked,
                    null,
                    TokenUsage.NONE,
                    started,
                    null,
                    exhausted.outcome(),
                    GenerationService.slug(exhausted.outcome()));
            throw exhausted.problem();
        } catch (StreamBroken broken) {
            record(
                    callId,
                    command,
                    candidates.getFirst(),
                    0,
                    false,
                    requestMasked,
                    null,
                    TokenUsage.NONE,
                    started,
                    null,
                    AiCallOutcome.PROVIDER_ERROR,
                    "ai-provider-unavailable");
            throw new AiProviderUnavailableException(
                    "The stream broke after it started.", FallbackExecutor.DEFAULT_RETRY_AFTER.toSeconds());
        }
        if (deltas != null) {
            String rest = unmasker.finish();
            if (!rest.isEmpty()) {
                deltas.accept(rest);
            }
        }
        ChatResult result = answer.value();
        String verdict = input == null ? null : "input:" + input.verdict() + input.categories();
        if (policy.checksOutput()
                && result.content() != null
                && !result.content().isBlank()) {
            GuardAssessment output = safety.check(result.content(), policy, true);
            verdict = (verdict == null ? "" : verdict + ";") + "output:" + output.verdict() + output.categories();
            if (!output.allowed()) {
                record(
                        callId,
                        command,
                        answer.model(),
                        answer.depth(),
                        false,
                        requestMasked,
                        result.content(),
                        result.usage(),
                        started,
                        verdict,
                        AiCallOutcome.GUARD_BLOCKED,
                        "ai-guard-blocked");
                throw new AiGuardBlockedException(output, SafetyService.refusalCode(output));
            }
        }
        List<ToolCall> toolCalls = result.toolCalls().stream()
                .map(call -> new ToolCall(call.id(), call.name(), session.unmask(call.arguments())))
                .toList();
        ChatMessage message = new ChatMessage(
                ChatMessage.Role.ASSISTANT,
                result.content() == null ? null : session.unmask(result.content()),
                toolCalls,
                null);
        if (command.conversationId() != null) {
            conversations.save(command.conversationId(), session);
        }
        AiCall call = record(
                callId,
                command,
                answer.model(),
                answer.depth(),
                false,
                requestMasked,
                result.content(),
                result.usage(),
                started,
                verdict,
                AiCallOutcome.SUCCESS,
                null);
        return new FacadeResult(message, result.finishReason(), result.usage(), call.stamp());
    }

    private ChatResult call(
            ModelRef model,
            List<ChatMessage> messages,
            RouteParams params,
            FacadeCommand command,
            @Nullable Consumer<String> deltas,
            StreamingUnmasker unmasker,
            AtomicBoolean emitted) {
        ChatRequest request = new ChatRequest(model, messages, params, null, command.tools(), command.toolChoice());
        if (deltas == null) {
            return providers.chat(model.provider()).call(request);
        }
        try {
            return providers.chat(model.provider()).stream(request, delta -> {
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
    }

    /** User, tool and earlier assistant turns carry the learner's data; the system prompt is the caller's. */
    private ChatMessage mask(ChatMessage message, MaskingSession session, List<String> hints) {
        if (message.role() == ChatMessage.Role.SYSTEM) {
            return message;
        }
        ChatMessage out = message.content() == null
                ? message
                : message.withContent(safety.mask(message.content(), session, hints, Set.of()));
        if (!message.toolCalls().isEmpty()) {
            out = out.withToolCalls(message.toolCalls().stream()
                    .map(call -> new ToolCall(
                            call.id(), call.name(), safety.mask(call.arguments(), session, hints, Set.of())))
                    .toList());
        }
        return out;
    }

    private static @Nullable String lastUserText(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.role() == ChatMessage.Role.USER && message.content() != null) {
                return message.content();
            }
        }
        return null;
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

    private AiCall record(
            String callId,
            FacadeCommand command,
            ModelRef model,
            int depth,
            boolean degraded,
            String request,
            @Nullable String response,
            TokenUsage usage,
            Instant started,
            @Nullable String verdict,
            AiCallOutcome outcome,
            @Nullable String error) {
        return journal.record(
                callId,
                new CallJournal.Entry(
                        command.callerService(),
                        command.useCase(),
                        command.feature(),
                        command.priority(),
                        command.userId(),
                        command.promptKey(),
                        command.promptVersion(),
                        model,
                        depth,
                        degraded,
                        false,
                        request,
                        response,
                        usage,
                        Duration.between(started, clock.instant()).toMillis(),
                        verdict,
                        outcome,
                        error,
                        command.correlationId()));
    }
}
