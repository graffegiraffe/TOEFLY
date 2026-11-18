package com.toefly.assessment.adapter.in.web;

import com.toefly.assessment.application.port.in.Caller;
import com.toefly.assessment.application.port.in.SubmissionQueryUseCase;
import com.toefly.assessment.application.port.in.SubmissionView;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.submission.SubmissionStatus;
import com.toefly.assessment.domain.submission.SubmissionVersion;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code GET /api/v1/submissions/{id}/events} (docs/05-api-contracts.md §4): the status of the current version is read
 * every second on a virtual thread, so any replica can serve the stream; it ends with {@code evaluation.completed} or
 * after 120 seconds, and the client reconnects.
 */
@RestController
public class SubmissionEventsController {

    static final Duration LIFETIME = Duration.ofSeconds(120);
    static final Duration POLL = Duration.ofSeconds(1);

    private final SubmissionQueryUseCase queries;
    private final CurrentCaller caller;
    private final Clock clock;

    public SubmissionEventsController(SubmissionQueryUseCase queries, CurrentCaller caller, Clock clock) {
        this.queries = queries;
        this.caller = caller;
        this.clock = clock;
    }

    @GetMapping(value = "/api/v1/submissions/{submissionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID submissionId) {
        Caller who = caller.get();
        queries.get(who, submissionId);
        SseEmitter emitter = new SseEmitter(LIFETIME.toMillis() + 5_000);
        Thread.ofVirtual().name("sse-" + submissionId).start(() -> run(emitter, who, submissionId));
        return emitter;
    }

    private void run(SseEmitter emitter, Caller who, UUID submissionId) {
        AtomicInteger seq = new AtomicInteger();
        long deadline = System.nanoTime() + LIFETIME.toNanos();
        String last = null;
        boolean scoring = false;
        try {
            emitter.send(SseEmitter.event().reconnectTime(3000).comment("open"));
            while (System.nanoTime() < deadline) {
                SubmissionView view = queries.get(who, submissionId);
                SubmissionVersion current = view.submission().current();
                SubmissionStatus status = current.status();
                String key = current.versionNo() + ":" + status;
                if (!key.equals(last)) {
                    last = key;
                    send(
                            emitter,
                            submissionId,
                            seq,
                            "evaluation.status",
                            Map.of(
                                    "submissionId",
                                    submissionId,
                                    "versionNo",
                                    current.versionNo(),
                                    "status",
                                    status.name()));
                    if (status == SubmissionStatus.EVALUATION_DEFERRED && view.deferred() != null) {
                        long seconds = Math.max(
                                0,
                                Duration.between(
                                                clock.instant(), view.deferred().nextAttemptAt())
                                        .toSeconds());
                        send(
                                emitter,
                                submissionId,
                                seq,
                                "evaluation.deferred",
                                Map.of("submissionId", submissionId, "retryAfterSeconds", seconds));
                    }
                }
                if (status == SubmissionStatus.EVALUATING && !scoring) {
                    scoring = true;
                    send(
                            emitter,
                            submissionId,
                            seq,
                            "evaluation.progress",
                            Map.of("submissionId", submissionId, "stage", "SCORING"));
                }
                if (status.evaluated()) {
                    Evaluation evaluation = effective(view, current.versionNo());
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("submissionId", submissionId);
                    data.put("versionNo", current.versionNo());
                    data.put("evaluationId", evaluation == null ? null : evaluation.id());
                    data.put("score", evaluation == null ? null : evaluation.finalScore());
                    send(emitter, submissionId, seq, "evaluation.completed", data);
                    break;
                }
                if (status == SubmissionStatus.DRAFT) {
                    break;
                }
                Thread.sleep(POLL);
            }
            emitter.complete();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (IOException | IllegalStateException closed) {
            emitter.completeWithError(closed);
        } catch (RuntimeException failure) {
            emitter.completeWithError(failure);
        }
    }

    private static @Nullable Evaluation effective(SubmissionView view, int versionNo) {
        return Evaluation.effective(view.evaluations().stream()
                .filter(e -> e.versionNo() == versionNo)
                .toList());
    }

    private static void send(
            SseEmitter emitter, UUID submissionId, AtomicInteger seq, String name, Map<String, Object> data)
            throws IOException {
        emitter.send(SseEmitter.event()
                .id(submissionId + ":" + seq.incrementAndGet())
                .name(name)
                .data(data, MediaType.APPLICATION_JSON));
    }
}
