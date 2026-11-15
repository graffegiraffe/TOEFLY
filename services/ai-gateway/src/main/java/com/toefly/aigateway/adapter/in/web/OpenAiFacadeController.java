package com.toefly.aigateway.adapter.in.web;

import com.toefly.aigateway.application.port.in.ChatFacadeUseCase;
import com.toefly.aigateway.application.port.in.FacadeCommand;
import com.toefly.aigateway.application.port.in.FacadeResult;
import com.toefly.aigateway.domain.chat.ChatMessage;
import com.toefly.aigateway.domain.chat.ToolCall;
import com.toefly.aigateway.domain.chat.ToolSpec;
import com.toefly.aigateway.domain.prompt.PromptRuleException;
import com.toefly.aigateway.domain.quota.Priority;
import com.toefly.aigateway.domain.routing.AiUseCase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI-compatible {@code /internal/openai/v1/chat/completions} for Spring AI {@code OpenAiChatModel} in
 * tutor-agent-service (§2.2.2). Metadata comes in {@code X-Toefly-*} headers; streamed answers end with a usage
 * chunk and {@code [DONE]}.
 */
@RestController
public class OpenAiFacadeController {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ChatFacadeUseCase facade;
    private final ProblemMapper problems;
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    public OpenAiFacadeController(ChatFacadeUseCase facade, ProblemMapper problems) {
        this.facade = facade;
        this.problems = problems;
    }

    @PostMapping(value = "/internal/openai/v1/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object complete(
            @RequestBody JsonNode body,
            @RequestHeader("X-Toefly-Use-Case") String useCase,
            @RequestHeader(value = "X-Toefly-Prompt-Key", required = false) @Nullable String promptKey,
            @RequestHeader(value = "X-Toefly-Prompt-Version", required = false) @Nullable Integer promptVersion,
            @RequestHeader(value = "X-Toefly-User-Id", required = false) @Nullable UUID userId,
            @RequestHeader(value = "X-Toefly-Conversation-Id", required = false) @Nullable String conversationId,
            @RequestHeader(value = "X-Toefly-Pii-Hints", required = false) @Nullable String piiHints,
            @RequestHeader(value = "X-Toefly-Priority", required = false) @Nullable String priority,
            @RequestHeader(value = "X-Toefly-Feature", required = false) @Nullable String feature) {
        FacadeCommand command = new FacadeCommand(
                Callers.service(),
                parse(AiUseCase.class, useCase),
                promptKey,
                promptVersion,
                userId,
                conversationId,
                hints(piiHints),
                priority == null ? Priority.INTERACTIVE : parse(Priority.class, priority),
                feature,
                messages(body.path("messages")),
                tools(body.path("tools")),
                toolChoice(body.path("tool_choice")),
                body.has("temperature") ? body.path("temperature").asDouble() : null,
                body.has("max_completion_tokens")
                        ? Integer.valueOf(body.path("max_completion_tokens").asInt())
                        : body.has("max_tokens")
                                ? Integer.valueOf(body.path("max_tokens").asInt())
                                : null,
                null);
        if (body.path("stream").asBoolean(false)) {
            return stream(command);
        }
        FacadeResult result = facade.complete(command);
        return ResponseEntity.ok()
                .header("X-Toefly-Ai-Call-Id", result.aiStamp().aiCallId())
                .header("X-Toefly-Model", result.aiStamp().model())
                .header(
                        "X-Toefly-Fallback-Depth",
                        Integer.toString(result.aiStamp().fallbackDepth()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(completion(result));
    }

    private SseEmitter stream(FacadeCommand command) {
        SseEmitter emitter = new SseEmitter(StreamController.TIMEOUT_MS);
        SecurityContext security = SecurityContextHolder.getContext();
        String id = "chatcmpl-" + UUID.randomUUID();
        long created = System.currentTimeMillis() / 1000;
        threads.submit(() -> {
            SecurityContextHolder.setContext(security);
            try {
                FacadeResult result = facade.stream(
                        command, delta -> send(emitter, chunk(id, created, "", Map.of("content", delta), null)));
                Map<String, Object> last = new LinkedHashMap<>();
                if (!result.message().toolCalls().isEmpty()) {
                    last.put("tool_calls", toolCalls(result.message().toolCalls(), true));
                }
                send(emitter, chunk(id, created, result.aiStamp().model(), last, finish(result)));
                Map<String, Object> usage = chunk(id, created, result.aiStamp().model(), null, null);
                usage.put("usage", usage(result));
                usage.put(
                        "x_toefly",
                        Map.of(
                                "aiCallId", result.aiStamp().aiCallId(),
                                "model", result.aiStamp().model(),
                                "fallbackDepth", result.aiStamp().fallbackDepth()));
                send(emitter, usage);
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (IOException gone) {
                emitter.completeWithError(gone);
            } catch (RuntimeException failure) {
                ProblemDetail problem = problems.toProblem(failure);
                send(emitter, Map.of("error", problem));
                emitter.complete();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        return emitter;
    }

    static Map<String, Object> completion(FacadeResult result) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", result.message().content());
        if (!result.message().toolCalls().isEmpty()) {
            message.put("tool_calls", toolCalls(result.message().toolCalls(), false));
        }
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", finish(result));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", "chatcmpl-" + result.aiStamp().aiCallId());
        body.put("object", "chat.completion");
        body.put("created", result.aiStamp().createdAt().getEpochSecond());
        body.put("model", result.aiStamp().model());
        body.put("choices", List.of(choice));
        body.put("usage", usage(result));
        return body;
    }

    private static Map<String, Object> chunk(
            String id, long created, String model, @Nullable Map<String, Object> delta, @Nullable String finish) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("object", "chat.completion.chunk");
        body.put("created", created);
        body.put("model", model);
        if (delta == null) {
            body.put("choices", List.of());
        } else {
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("index", 0);
            choice.put("delta", delta);
            choice.put("finish_reason", finish);
            body.put("choices", List.of(choice));
        }
        return body;
    }

    private static String finish(FacadeResult result) {
        if (!result.message().toolCalls().isEmpty()) {
            return "tool_calls";
        }
        String reason = result.finishReason();
        return reason == null || reason.isBlank() ? "stop" : reason.toLowerCase(Locale.ROOT);
    }

    private static Map<String, Object> usage(FacadeResult result) {
        return Map.of(
                "prompt_tokens", result.usage().inputTokens(),
                "completion_tokens", result.usage().outputTokens(),
                "total_tokens", result.usage().totalTokens());
    }

    private static List<Map<String, Object>> toolCalls(List<ToolCall> calls, boolean indexed) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            ToolCall call = calls.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            if (indexed) {
                item.put("index", i);
            }
            item.put("id", call.id());
            item.put("type", "function");
            item.put("function", Map.of("name", call.name(), "arguments", call.arguments()));
            out.add(item);
        }
        return out;
    }

    static List<ChatMessage> messages(JsonNode messages) {
        if (!messages.isArray() || messages.isEmpty()) {
            throw new PromptRuleException("validation-failed", "messages must be a non-empty array");
        }
        List<ChatMessage> out = new ArrayList<>();
        for (JsonNode message : messages) {
            String role = message.path("role").asString("");
            ChatMessage.Role parsed = switch (role) {
                case "system", "developer" -> ChatMessage.Role.SYSTEM;
                case "user" -> ChatMessage.Role.USER;
                case "assistant" -> ChatMessage.Role.ASSISTANT;
                case "tool" -> ChatMessage.Role.TOOL;
                default -> throw new PromptRuleException("validation-failed", "unknown role " + role);
            };
            List<ToolCall> calls = new ArrayList<>();
            for (JsonNode call : message.path("tool_calls")) {
                JsonNode function = call.path("function");
                calls.add(new ToolCall(
                        call.path("id").asString(""),
                        function.path("name").asString(""),
                        function.path("arguments").isString()
                                ? function.path("arguments").asString()
                                : JSON.writeValueAsString(function.path("arguments"))));
            }
            out.add(new ChatMessage(
                    parsed,
                    content(message.path("content")),
                    calls,
                    message.has("tool_call_id") ? message.path("tool_call_id").asString() : null));
        }
        return out;
    }

    private static @Nullable String content(JsonNode content) {
        if (content.isMissingNode() || content.isNull()) {
            return null;
        }
        if (content.isString()) {
            return content.asString();
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : content) {
            if ("text".equals(part.path("type").asString(""))) {
                text.append(part.path("text").asString(""));
            }
        }
        return text.toString();
    }

    static List<ToolSpec> tools(JsonNode tools) {
        List<ToolSpec> out = new ArrayList<>();
        for (JsonNode tool : tools) {
            JsonNode function = tool.path("function");
            out.add(new ToolSpec(
                    function.path("name").asString(""),
                    function.path("description").asString(""),
                    function.has("parameters")
                            ? JSON.writeValueAsString(function.path("parameters"))
                            : "{\"type\":\"object\",\"properties\":{}}"));
        }
        return out;
    }

    private static @Nullable String toolChoice(JsonNode choice) {
        if (choice.isMissingNode() || choice.isNull()) {
            return null;
        }
        return choice.isString() ? choice.asString() : JSON.writeValueAsString(choice);
    }

    static List<String> hints(@Nullable String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        try {
            String json = new String(Base64.getDecoder().decode(header), StandardCharsets.UTF_8);
            List<String> names = new ArrayList<>();
            JSON.readTree(json).forEach(node -> names.add(node.asString("")));
            return names;
        } catch (RuntimeException broken) {
            throw new PromptRuleException("validation-failed", "X-Toefly-Pii-Hints must be base64 of a JSON array");
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new PromptRuleException("validation-failed", "unknown " + type.getSimpleName() + " " + value);
        }
    }

    private static void send(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event().data(JSON.writeValueAsString(data)));
        } catch (IOException gone) {
            throw new IllegalStateException("the caller closed the stream", gone);
        }
    }
}
