package com.toefly.tutoragent.application.service;

import com.toefly.tutoragent.application.port.AiUnavailableException;
import com.toefly.tutoragent.application.port.in.ChatRejectedException;
import com.toefly.tutoragent.application.port.in.ChatRejectedException.Reason;
import com.toefly.tutoragent.application.port.in.ChatStreamSink;
import com.toefly.tutoragent.application.port.in.ChatUseCase;
import com.toefly.tutoragent.application.port.in.EntitlementRequiredException;
import com.toefly.tutoragent.application.port.in.LearnerModelUseCase;
import com.toefly.tutoragent.application.port.out.AiPort;
import com.toefly.tutoragent.application.port.out.ChatModelPort;
import com.toefly.tutoragent.application.port.out.ChatRepository;
import com.toefly.tutoragent.application.port.out.EntitlementPort;
import com.toefly.tutoragent.application.port.out.OwnerToolsPort;
import com.toefly.tutoragent.application.port.out.PendingActionRepository;
import com.toefly.tutoragent.application.port.out.ProjectionRepository;
import com.toefly.tutoragent.application.port.out.PromptPort;
import com.toefly.tutoragent.application.port.out.ToolLog;
import com.toefly.tutoragent.domain.chat.AnswerLeakDetector;
import com.toefly.tutoragent.domain.chat.ChatContext;
import com.toefly.tutoragent.domain.chat.ChatContextType;
import com.toefly.tutoragent.domain.chat.ChatMessage;
import com.toefly.tutoragent.domain.chat.ChatThread;
import com.toefly.tutoragent.domain.chat.InjectionHeuristics;
import com.toefly.tutoragent.domain.chat.PendingAction;
import com.toefly.tutoragent.domain.chat.Replies;
import com.toefly.tutoragent.domain.chat.RouterDecision;
import com.toefly.tutoragent.domain.chat.ToolCatalog;
import com.toefly.tutoragent.domain.chat.TutorIntent;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.learner.LearnerModel;
import com.toefly.tutoragent.domain.learner.MistakeStat;
import com.toefly.tutoragent.domain.learner.Taxonomy;
import com.toefly.tutoragent.domain.review.Emoji;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The orchestrator of Toefly Tutor (docs/06 §4.3, §4.6): mode gate and input guard before any AI call, the router,
 * the screen, learner model and mistake memory as context, a tool loop of at most 4 rounds with the tools of the
 * intent and the screen, the output guard, and the SSE events of §4.9 stored for resuming.
 */
public class ChatService implements ChatUseCase {

    static final String ENTITLEMENT = "AI_TUTOR_CHAT";
    static final String SYSTEM_PROMPT = "tutor.chat.system";
    static final String ROUTER_PROMPT = "tutor.router";
    static final String SUMMARY_PROMPT = "tutor.chat.summarize";
    static final int WINDOW = 16;
    static final int MAX_TOOL_ROUNDS = 4;
    static final int MAX_TOOL_RESULT_CHARS = 6000;
    static final int PER_TEN_MINUTES = 30;
    static final int PER_DAY = 200;

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ChatRepository chats;
    private final PendingActionRepository actions;
    private final ToolLog toolLog;
    private final EntitlementPort entitlements;
    private final ProjectionRepository projections;
    private final LearnerModelUseCase learner;
    private final AiPort ai;
    private final ChatModelPort model;
    private final PromptPort prompts;
    private final OwnerToolsPort owners;
    private final ToolExecutor tools;
    private final ScreenContextAssembler screens;
    private final Map<String, ChatModelPort.ToolSpec> specs;
    private final Taxonomy taxonomy;
    private final Transactions transactions;
    private final Clock clock;
    private final Set<UUID> stopping = ConcurrentHashMap.newKeySet();
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public ChatService(
            ChatRepository chats,
            PendingActionRepository actions,
            ToolLog toolLog,
            EntitlementPort entitlements,
            ProjectionRepository projections,
            LearnerModelUseCase learner,
            AiPort ai,
            ChatModelPort model,
            PromptPort prompts,
            OwnerToolsPort owners,
            ToolExecutor tools,
            ScreenContextAssembler screens,
            List<ChatModelPort.ToolSpec> specs,
            Taxonomy taxonomy,
            Transactions transactions,
            Clock clock) {
        this.chats = chats;
        this.actions = actions;
        this.toolLog = toolLog;
        this.entitlements = entitlements;
        this.projections = projections;
        this.learner = learner;
        this.ai = ai;
        this.model = model;
        this.prompts = prompts;
        this.owners = owners;
        this.tools = tools;
        this.screens = screens;
        this.specs = specs.stream()
                .collect(Collectors.toMap(ChatModelPort.ToolSpec::name, s -> s, (a, b) -> a, LinkedHashMap::new));
        this.taxonomy = taxonomy;
        this.transactions = transactions;
        this.clock = clock;
    }

    // ---- threads ----

    @Override
    public ChatThread createThread(UUID userId, @Nullable String title, @Nullable ChatContext context) {
        gate(userId);
        Instant now = clock.instant();
        ChatThread thread = new ChatThread(
                UUID.randomUUID(),
                userId,
                title == null || title.isBlank() ? "New chat" : ChatThread.titleOf(title),
                context,
                learner.get(userId).language(),
                null,
                null,
                0,
                now,
                null);
        transactions.run(() -> chats.saveThread(thread));
        return thread;
    }

    @Override
    public List<ChatThread> threads(UUID userId, @Nullable UUID after, int limit) {
        return chats.threads(userId, after, limit);
    }

    @Override
    public ChatThread thread(UUID userId, UUID threadId) {
        return chats.thread(threadId)
                .filter(t -> t.userId().equals(userId))
                .orElseThrow(() -> new ChatRejectedException(Reason.NOT_FOUND, "No such thread."));
    }

    @Override
    public ChatThread updateThread(
            UUID userId, UUID threadId, @Nullable String title, @Nullable ExplanationLanguage language) {
        ChatThread t = thread(userId, threadId);
        ChatThread updated = new ChatThread(
                t.id(),
                t.userId(),
                title == null ? t.title() : ChatThread.titleOf(title),
                t.context(),
                language == null ? t.language() : language,
                t.summary(),
                t.summarizedUpto(),
                t.messageCount(),
                t.createdAt(),
                t.lastMessageAt());
        transactions.run(() -> chats.saveThread(updated));
        return updated;
    }

    @Override
    public void deleteThread(UUID userId, UUID threadId) {
        thread(userId, threadId);
        transactions.run(() -> chats.deleteThread(threadId));
    }

    @Override
    public List<ChatMessage> messages(UUID userId, UUID threadId, @Nullable UUID after, int limit) {
        thread(userId, threadId);
        return chats.messages(threadId, after, limit);
    }

    @Override
    public List<PendingAction> actionsOf(UUID messageId) {
        return actions.ofMessage(messageId);
    }

    // ---- a turn ----

    @Override
    public Turn accept(UUID userId, @Nullable String bearer, UUID threadId, String text, ContextChoice choice) {
        ChatThread thread = thread(userId, threadId);
        gate(userId);
        Instant now = clock.instant();
        if (chats.userMessagesSince(userId, now.minus(Duration.ofMinutes(10))) >= PER_TEN_MINUTES
                || chats.userMessagesSince(userId, now.minus(Duration.ofDays(1))) >= PER_DAY) {
            throw new ChatRejectedException(
                    Reason.RATE_LIMITED, "You're sending messages too fast. Try again in a minute");
        }
        if (chats.window(threadId, 2).stream().anyMatch(m -> m.status() == ChatMessage.Status.STREAMING)) {
            throw new ChatRejectedException(Reason.MESSAGE_IN_PROGRESS, "Wait for the current answer or stop it.");
        }
        if (InjectionHeuristics.suspicious(text)) {
            throw new ChatRejectedException(Reason.GUARDRAIL_REJECTED, "This message can't be sent to Toefly Tutor.");
        }
        ChatContext context = choice.keepThread() ? thread.context() : choice.context();
        UUID userMessage = UUID.randomUUID();
        UUID answer = UUID.randomUUID();
        transactions.run(() -> {
            chats.saveMessage(new ChatMessage(
                    userMessage,
                    threadId,
                    userId,
                    ChatMessage.Role.USER,
                    text.strip(),
                    ChatMessage.Status.COMPLETE,
                    null,
                    context,
                    List.of(),
                    null,
                    null,
                    null,
                    now));
            chats.saveMessage(new ChatMessage(
                    answer,
                    threadId,
                    userId,
                    ChatMessage.Role.ASSISTANT,
                    "",
                    ChatMessage.Status.STREAMING,
                    null,
                    context,
                    List.of(),
                    null,
                    null,
                    null,
                    now.plusMillis(1)));
            chats.saveThread(new ChatThread(
                    thread.id(),
                    thread.userId(),
                    thread.messageCount() == 0 && thread.title().equals("New chat")
                            ? ChatThread.titleOf(text)
                            : thread.title(),
                    thread.context(),
                    thread.language(),
                    thread.summary(),
                    thread.summarizedUpto(),
                    thread.messageCount() + 2,
                    thread.createdAt(),
                    now));
        });
        running.add(answer);
        return new Turn(userId, bearer, threadId, userMessage, answer, text.strip(), context);
    }

    @Override
    public void answer(Turn turn, ChatStreamSink sink) {
        AtomicInteger seq = new AtomicInteger();
        Emitter emit = (event, data) -> {
            int n = seq.incrementAndGet();
            String json = JSON.writeValueAsString(data);
            chats.appendEvent(turn.answerId(), n, event, json);
            try {
                sink.send(n, event, data);
            } catch (RuntimeException gone) {
                log.debug("Chat stream {} lost its client: {}", turn.answerId(), gone.getMessage());
            }
        };
        StringBuilder text = new StringBuilder();
        TurnState state = new TurnState();
        try {
            run(turn, emit, text, state);
        } catch (Stopped stopped) {
            finish(turn, text.toString(), ChatMessage.Status.INTERRUPTED, state);
            emit.send("done", done(turn, state, "INTERRUPTED"));
        } catch (AiUnavailableException unavailable) {
            log.warn("Chat answer {} failed: {}", turn.answerId(), unavailable.getMessage());
            finish(turn, text.toString(), ChatMessage.Status.FAILED, state);
            emit.send(
                    "error",
                    Map.of(
                            "type", "https://toefly.app/problems/ai-unavailable",
                            "title", "AI unavailable",
                            "status", 503,
                            "code", "AI_UNAVAILABLE",
                            "detail", "Toefly Tutor is unavailable right now. Try again soon."));
        } catch (RuntimeException failure) {
            log.error("Chat answer {} failed", turn.answerId(), failure);
            finish(turn, text.toString(), ChatMessage.Status.FAILED, state);
            emit.send(
                    "error",
                    Map.of(
                            "type", "https://toefly.app/problems/internal-error",
                            "title", "Internal error",
                            "status", 500,
                            "code", "INTERNAL_ERROR",
                            "detail", "Something went wrong. Try again."));
        } finally {
            running.remove(turn.answerId());
            stopping.remove(turn.answerId());
        }
        summarizeIfNeeded(turn.threadId());
    }

    @FunctionalInterface
    interface Emitter {
        void send(String event, Map<String, Object> data);
    }

    static final class TurnState {
        @Nullable
        TutorIntent intent;

        final List<String> toolsUsed = new ArrayList<>();

        @Nullable
        String aiCallId;

        int inputTokens;
        int outputTokens;
    }

    static final class Stopped extends RuntimeException {
        Stopped() {
            super("stopped", null, false, false);
        }
    }

    private void run(Turn turn, Emitter emit, StringBuilder text, TurnState state) {
        ChatThread thread = thread(turn.userId(), turn.threadId());
        LearnerModel learnerModel = learner.get(turn.userId());
        ScreenContextAssembler.Screen screen = screens.load(turn.context(), turn.userId(), turn.bearer());
        ChatContextType screenType = screen.context().type();
        RouterDecision decision = route(turn, thread.language(), screenType);
        state.intent = decision.intent();
        ExplanationLanguage language = thread.language();
        emit.send(
                "meta",
                Map.of(
                        "messageId", turn.answerId().toString(),
                        "intent", decision.intent().name(),
                        "language", language.name(),
                        "contextType", screenType.name()));

        if (decision.intent() == TutorIntent.OFF_TOPIC) {
            reply(turn, emit, text, state, Replies.offTopic(language));
            return;
        }
        if (decision.intent() == TutorIntent.SOLVE_IN_PROGRESS && screenType.unsubmitted()) {
            reply(turn, emit, text, state, Replies.solveBeforeSubmit(language));
            return;
        }

        PromptPort.Prompt prompt = prompts.active(SYSTEM_PROMPT);
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> vars = variables(learnerModel, thread, screen, decision, turn, nonce);
        List<ChatModelPort.Message> messages = new ArrayList<>();
        messages.add(ChatModelPort.Message.system(render(prompt.system(), vars)));
        for (ChatMessage m : chats.window(turn.threadId(), WINDOW + 2)) {
            if (m.id().equals(turn.userMessageId())
                    || m.id().equals(turn.answerId())
                    || m.text().isBlank()) {
                continue;
            }
            messages.add(
                    m.role() == ChatMessage.Role.USER
                            ? ChatModelPort.Message.user(
                                    "<student_message id=\"" + nonce + "\">" + m.text() + "</student_message>")
                            : ChatModelPort.Message.assistant(m.text()));
        }
        messages.add(ChatModelPort.Message.user(render(prompt.user(), vars)));

        List<ChatModelPort.ToolSpec> toolSpecs = ToolCatalog.select(decision.intent(), screenType).stream()
                .map(specs::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        boolean buffer = screenType.unsubmitted();
        String useCase = decision.escalate() ? "TUTOR_ESCALATION" : "TUTOR_CHAT";
        ToolExecutor.ToolContext toolContext = new ToolExecutor.ToolContext(
                turn.userId(), turn.bearer(), turn.threadId(), turn.answerId(), screenType);

        for (int round = 0; ; round++) {
            boolean last = round >= MAX_TOOL_ROUNDS;
            StringBuilder roundText = new StringBuilder();
            ChatModelPort.Turn result = model.stream(
                    new ChatModelPort.TurnRequest(
                            useCase,
                            SYSTEM_PROMPT,
                            prompt.version(),
                            turn.userId(),
                            turn.threadId().toString(),
                            messages,
                            last ? List.of() : toolSpecs),
                    token -> {
                        if (stopping.contains(turn.answerId())) {
                            throw new Stopped();
                        }
                        String clean = Emoji.strip(token);
                        roundText.append(clean);
                        if (!buffer && !clean.isEmpty()) {
                            text.append(clean);
                            emit.send("token", Map.of("t", clean));
                        }
                    });
            state.aiCallId = result.aiCallId();
            state.inputTokens += result.inputTokens();
            state.outputTokens += result.outputTokens();
            if (result.toolCalls().isEmpty() || last) {
                if (buffer) {
                    text.append(roundText);
                }
                break;
            }
            messages.add(new ChatModelPort.Message("assistant", roundText.toString(), result.toolCalls(), null));
            for (ChatModelPort.ToolCall call : result.toolCalls()) {
                messages.add(ChatModelPort.Message.tool(call.id(), callTool(call, toolContext, emit, state)));
            }
        }

        String answer = text.toString();
        if (AnswerLeakDetector.leaks(answer, screenType, screen.taskWords(), screen.draft())) {
            String replacement = Replies.solveBeforeSubmit(language);
            if (!buffer) {
                emit.send("retract", Map.of("reason", "OUTPUT_GUARD", "replacement", replacement));
            }
            text.setLength(0);
            text.append(replacement);
            if (buffer) {
                emitTokens(emit, replacement);
            }
            finish(turn, text.toString(), buffer ? ChatMessage.Status.COMPLETE : ChatMessage.Status.RETRACTED, state);
        } else {
            if (buffer) {
                emitTokens(emit, answer);
            }
            if (answer.isBlank()) {
                String fallback = Replies.refusal(language);
                text.append(fallback);
                emitTokens(emit, fallback);
            }
            finish(turn, text.toString(), ChatMessage.Status.COMPLETE, state);
        }
        emit.send("done", done(turn, state, "COMPLETE"));
    }

    private String callTool(ChatModelPort.ToolCall call, ToolExecutor.ToolContext ctx, Emitter emit, TurnState state) {
        if (stopping.contains(ctx.messageId())) {
            throw new Stopped();
        }
        emit.send("tool", Map.of("name", call.name(), "status", "started", "label", ToolExecutor.label(call.name())));
        long started = System.nanoTime();
        Map<String, Object> args = arguments(call.argumentsJson());
        ToolExecutor.Outcome outcome = tools.run(call.name(), args, ctx);
        long latency = (System.nanoTime() - started) / 1_000_000;
        state.toolsUsed.add(call.name());
        transactions.run(() -> {
            toolLog.record(ctx.threadId(), ctx.messageId(), call.name(), args, outcome.status(), latency);
            if (outcome.action() != null) {
                actions.save(outcome.action());
            }
        });
        if (outcome.action() != null) {
            emit.send(
                    "action",
                    Map.of(
                            "actionId", outcome.action().id().toString(),
                            "type", outcome.action().type(),
                            "title", outcome.action().title(),
                            "confirmUrl",
                                    "/api/v1/pending-actions/"
                                            + outcome.action().id() + ":confirm"));
        }
        emit.send("tool", Map.of("name", call.name(), "status", "finished", "label", ToolExecutor.label(call.name())));
        return trimmed(outcome.result());
    }

    private void reply(Turn turn, Emitter emit, StringBuilder text, TurnState state, String reply) {
        text.append(reply);
        emitTokens(emit, reply);
        finish(turn, reply, ChatMessage.Status.COMPLETE, state);
        emit.send("done", done(turn, state, "COMPLETE"));
    }

    private static void emitTokens(Emitter emit, String text) {
        for (String part : text.split("(?<= )")) {
            emit.send("token", Map.of("t", part));
        }
    }

    private void finish(Turn turn, String text, ChatMessage.Status status, TurnState state) {
        transactions.run(() -> chats.saveMessage(new ChatMessage(
                turn.answerId(),
                turn.threadId(),
                turn.userId(),
                ChatMessage.Role.ASSISTANT,
                text,
                status,
                state.intent,
                turn.context(),
                List.copyOf(state.toolsUsed),
                state.aiCallId,
                state.inputTokens,
                state.outputTokens,
                clock.instant())));
    }

    private Map<String, Object> done(Turn turn, TurnState state, String status) {
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("messageId", turn.answerId().toString());
        done.put("status", status);
        done.put("aiStamp", state.aiCallId == null ? Map.of() : Map.of("aiCallId", state.aiCallId));
        done.put("usage", Map.of("inputTokens", state.inputTokens, "outputTokens", state.outputTokens));
        return done;
    }

    RouterDecision route(Turn turn, ExplanationLanguage language, ChatContextType screen) {
        try {
            AiPort.Completion completion = ai.complete(new AiPort.CompletionCall(
                    "TUTOR_ROUTER",
                    ROUTER_PROMPT,
                    null,
                    Map.of(
                            "studentMessage", turn.text(),
                            "taskState", screen.unsubmitted() ? "NOT SUBMITTED" : "submitted or no task",
                            "contextType", screen.name()),
                    turn.userId(),
                    "tutor-chat",
                    "INTERACTIVE",
                    3_000,
                    null,
                    null));
            Map<String, Object> out = completion.output();
            if (out == null) {
                return RouterDecision.fallback(language);
            }
            Map<?, ?> explicit = out.get("explicitUserRequest") instanceof Map<?, ?> m ? m : Map.of();
            return new RouterDecision(
                    TutorIntent.valueOf(String.valueOf(out.get("intent"))),
                    language,
                    Boolean.TRUE.equals(explicit.get("addWord")),
                    Boolean.TRUE.equals(explicit.get("showAnswer")),
                    Boolean.TRUE.equals(explicit.get("assignTask")),
                    Boolean.TRUE.equals(out.get("escalate")));
        } catch (AiUnavailableException | IllegalArgumentException unavailable) {
            log.info("Router unavailable, default intent: {}", unavailable.getMessage());
            return RouterDecision.fallback(language);
        }
    }

    private Map<String, String> variables(
            LearnerModel m,
            ChatThread thread,
            ScreenContextAssembler.Screen screen,
            RouterDecision decision,
            Turn turn,
            String nonce) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("explanationLanguage", thread.language().name());
        vars.put("taskState", screen.context().type().unsubmitted() ? "NOT SUBMITTED" : "submitted or no task");
        vars.put("nonce", nonce);
        vars.put(
                "targetBand",
                m.targetBand() == null ? "not set" : m.targetBand().toString());
        vars.put(
                "examDate",
                m.examDate() == null ? "an unset date" : m.examDate().toString());
        vars.put(
                "weeksLeft",
                m.examDate() == null
                        ? "?"
                        : Long.toString(Math.max(
                                0,
                                ChronoUnit.WEEKS.between(
                                        LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC), m.examDate()))));
        vars.put("weekNumber", "see getCurrentWeek");
        vars.put(
                "bandSummary",
                m.bands().isEmpty()
                        ? "not enough data yet"
                        : m.bands().entrySet().stream()
                                        .map(e -> e.getKey() + " " + e.getValue())
                                        .collect(Collectors.joining(", "))
                                + " (AI estimate)");
        vars.put(
                "topMistakes",
                m.topMistakes(3).isEmpty()
                        ? "none recorded yet"
                        : m.topMistakes(3).stream()
                                .map(s ->
                                        taxonomy.title(s.subcategory(), ExplanationLanguage.EN) + " (" + s.subcategory()
                                                + ", " + s.trend().name().toLowerCase(Locale.ROOT) + ")")
                                .collect(Collectors.joining("; ")));
        vars.put(
                "preferences",
                "explanations in " + thread.language().name()
                        + (decision.showAnswer() ? "; the student asked for the answer directly" : ""));
        vars.put("screenContext", screen.text());
        vars.put("retrievedMemories", memories(turn, screen, m));
        vars.put("conversationSummary", thread.summary() == null ? "none" : thread.summary());
        vars.put("studentMessage", turn.text());
        return vars;
    }

    /** Top-5 similar past mistakes, re-ranked by similarity, age and the subcategories of the screen (docs/06 §6.5). */
    private String memories(Turn turn, ScreenContextAssembler.Screen screen, LearnerModel m) {
        List<String> resolved = m.mistakeStats().stream()
                .filter(s -> s.status() == com.toefly.tutoragent.domain.learner.MistakeStatus.RESOLVED)
                .map(MistakeStat::subcategory)
                .toList();
        String query = turn.text() + " "
                + screen.text().substring(0, Math.min(400, screen.text().length()));
        List<String> hits = tools.similar(turn.userId(), query).stream()
                .filter(h -> resolved.stream().noneMatch(h::contains))
                .limit(5)
                .toList();
        return hits.isEmpty() ? "none" : hits.stream().map(h -> "- " + h).collect(Collectors.joining("\n"));
    }

    static String render(String template, Map<String, String> vars) {
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("$" + e.getKey() + "$", e.getValue());
        }
        return out;
    }

    private static Map<String, Object> arguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JacksonException invalid) {
            return Map.of();
        }
    }

    static String trimmed(Map<String, Object> result) {
        String json = JSON.writeValueAsString(result);
        if (json.length() <= MAX_TOOL_RESULT_CHARS) {
            return json;
        }
        return JSON.writeValueAsString(Map.of("truncated", true, "partial", json.substring(0, MAX_TOOL_RESULT_CHARS)));
    }

    private void summarizeIfNeeded(UUID threadId) {
        try {
            ChatThread thread = chats.thread(threadId).orElse(null);
            if (thread == null || thread.messageCount() <= WINDOW) {
                return;
            }
            List<ChatMessage> old = chats.beforeWindow(threadId, WINDOW, thread.summarizedUpto());
            if (old.size() < 4) {
                return;
            }
            String transcript = old.stream()
                    .map(m -> (m.role() == ChatMessage.Role.USER ? "Student: " : "Tutor: ") + m.text())
                    .collect(Collectors.joining("\n"));
            AiPort.Completion completion = ai.complete(new AiPort.CompletionCall(
                    "HINT",
                    SUMMARY_PROMPT,
                    null,
                    Map.of(
                            "previousSummary", thread.summary() == null ? "none" : thread.summary(),
                            "messages",
                                    transcript.length() > 8000
                                            ? transcript.substring(transcript.length() - 8000)
                                            : transcript),
                    thread.userId(),
                    "tutor-chat-summary",
                    "BACKGROUND",
                    10_000,
                    null,
                    null));
            if (completion.text() == null || completion.text().isBlank()) {
                return;
            }
            transactions.run(() -> chats.saveThread(new ChatThread(
                    thread.id(),
                    thread.userId(),
                    thread.title(),
                    thread.context(),
                    thread.language(),
                    completion.text().strip(),
                    old.getLast().createdAt(),
                    thread.messageCount(),
                    thread.createdAt(),
                    thread.lastMessageAt())));
        } catch (RuntimeException failure) {
            log.info("Summary of thread {} skipped: {}", threadId, failure.getMessage());
        }
    }

    // ---- stop, resume, confirm ----

    @Override
    public void stop(UUID userId, UUID threadId, UUID messageId) {
        thread(userId, threadId);
        ChatMessage message = chats.message(messageId)
                .filter(m -> m.threadId().equals(threadId))
                .orElseThrow(() -> new ChatRejectedException(Reason.NOT_FOUND, "No such message."));
        if (running.contains(messageId)) {
            stopping.add(messageId);
        } else if (message.status() == ChatMessage.Status.STREAMING) {
            transactions.run(() -> chats.saveMessage(new ChatMessage(
                    message.id(),
                    message.threadId(),
                    message.userId(),
                    message.role(),
                    message.text(),
                    ChatMessage.Status.INTERRUPTED,
                    message.intent(),
                    message.context(),
                    message.tools(),
                    message.aiCallId(),
                    message.tokensIn(),
                    message.tokensOut(),
                    message.createdAt())));
        }
    }

    @Override
    public List<StoredEvent> eventsAfter(UUID userId, UUID threadId, UUID messageId, int afterSeq) {
        thread(userId, threadId);
        chats.message(messageId)
                .filter(m -> m.threadId().equals(threadId))
                .orElseThrow(() -> new ChatRejectedException(Reason.NOT_FOUND, "No such message."));
        return chats.events(messageId, afterSeq).stream()
                .map(e -> new StoredEvent(e.seq(), e.event(), e.data()))
                .toList();
    }

    @Override
    public boolean streaming(UUID messageId) {
        return running.contains(messageId);
    }

    @Override
    public PendingAction confirm(UUID userId, UUID actionId) {
        PendingAction action = actions.find(actionId)
                .filter(a -> a.userId().equals(userId))
                .orElseThrow(() -> new ChatRejectedException(Reason.NOT_FOUND, "No such action."));
        if (action.status() == PendingAction.Status.CONFIRMED) {
            return action;
        }
        if (action.expired(clock.instant())) {
            throw new ChatRejectedException(
                    Reason.ACTION_EXPIRED, "This suggestion has expired. Ask Toefly Tutor again.");
        }
        Map<String, Object> week = owners.currentWeek(userId, null);
        Object weekId = week.get("weekId");
        if (weekId == null) {
            throw new ChatRejectedException(Reason.ACTION_EXPIRED, "There is no current week to add the task to.");
        }
        Map<String, Object> request = new LinkedHashMap<>(action.payload());
        request.put("source", "TUTOR_AGENT");
        Map<String, Object> task = owners.addExtraTask(
                UUID.fromString(weekId.toString()), request, action.id().toString());
        Object taskId = task.getOrDefault("weekTaskId", task.get("id"));
        PendingAction confirmed = action.confirmed(taskId == null ? action.id() : UUID.fromString(taskId.toString()));
        transactions.run(() -> actions.save(confirmed));
        return confirmed;
    }

    private void gate(UUID userId) {
        if (!entitlements.has(userId, ENTITLEMENT)) {
            throw new EntitlementRequiredException(ENTITLEMENT);
        }
        if (projections.examInProgress(userId, clock.instant())) {
            throw new ChatRejectedException(Reason.EXAM_IN_PROGRESS, "Chat is unavailable during an exam");
        }
    }
}
