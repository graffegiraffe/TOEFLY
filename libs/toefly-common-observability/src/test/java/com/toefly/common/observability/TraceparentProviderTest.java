package com.toefly.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;

class TraceparentProviderTest {

    @Test
    void formatsW3cTraceparent() {
        assertThat(TraceparentProvider.format("4bf92f3577b34da6a3ce929d0e0e4736", "f067aa0ba902b7", true))
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    }

    @Test
    void isEmptyWithoutCurrentSpan() {
        assertThat(new TraceparentProvider(new SimpleTracer()).current()).isEmpty();
    }

    @Test
    void readsCurrentSpan() {
        SimpleTracer tracer = new SimpleTracer();
        var span = tracer.nextSpan().start();
        try (var scope = tracer.withSpan(span)) {
            assertThat(new TraceparentProvider(tracer).current())
                    .hasValueSatisfying(value -> assertThat(value)
                            .startsWith("00-")
                            .contains(span.context().spanId()));
        } finally {
            span.end();
        }
    }
}
