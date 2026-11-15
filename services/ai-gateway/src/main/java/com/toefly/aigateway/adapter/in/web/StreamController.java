package com.toefly.aigateway.adapter.in.web;

import com.toefly.aigateway.adapter.in.web.dto.CompletionRequest;
import com.toefly.aigateway.application.port.in.CompleteUseCase;
import com.toefly.aigateway.application.port.in.CompletionResult;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code POST /internal/v1/ai/chat/streams}: events {@code delta}, {@code completed}, {@code error}. The work runs on
 * a virtual thread; a problem after the first event goes out as an {@code error} event.
 */
@RestController
public class StreamController {

    static final long TIMEOUT_MS = 180_000;

    private final CompleteUseCase completions;
    private final ProblemMapper problems;
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    public StreamController(CompleteUseCase completions, ProblemMapper problems) {
        this.completions = completions;
        this.problems = problems;
    }

    @PostMapping(
            value = "/internal/v1/ai/chat/streams",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody CompletionRequest request) {
        var command = CompletionController.command(request, null);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        SecurityContext security = SecurityContextHolder.getContext();
        AtomicBoolean started = new AtomicBoolean(false);
        threads.submit(() -> {
            SecurityContextHolder.setContext(security);
            try {
                CompletionResult result = completions.stream(command, delta -> {
                    started.set(true);
                    send(emitter, "delta", Map.of("text", delta));
                });
                send(emitter, "completed", CompletionController.response(result));
                emitter.complete();
            } catch (RuntimeException failure) {
                ProblemDetail problem = problems.toProblem(failure);
                send(emitter, "error", problem);
                emitter.complete();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        return emitter;
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException gone) {
            throw new IllegalStateException("the caller closed the stream", gone);
        }
    }
}
