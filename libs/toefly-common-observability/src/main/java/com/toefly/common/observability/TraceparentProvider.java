package com.toefly.common.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Optional;

/** W3C {@code traceparent} of the current span; the outbox stores it so Debezium can continue the trace in Kafka. */
public class TraceparentProvider {

    private final Tracer tracer;

    public TraceparentProvider(Tracer tracer) {
        this.tracer = tracer;
    }

    public Optional<String> current() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return Optional.empty();
        }
        TraceContext context = span.context();
        return Optional.of(format(context.traceId(), context.spanId(), Boolean.TRUE.equals(context.sampled())));
    }

    static String format(String traceId, String spanId, boolean sampled) {
        return "00-" + leftPad(traceId, 32) + "-" + leftPad(spanId, 16) + (sampled ? "-01" : "-00");
    }

    private static String leftPad(String hex, int length) {
        return "0".repeat(Math.max(0, length - hex.length())) + hex;
    }
}
