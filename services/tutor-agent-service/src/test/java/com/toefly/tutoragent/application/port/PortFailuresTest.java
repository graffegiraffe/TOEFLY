package com.toefly.tutoragent.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PortFailuresTest {

    @Test
    void failuresCarryTheirDetails() {
        RuntimeException cause = new RuntimeException("timeout");
        DependencyUnavailableException dependency =
                new DependencyUnavailableException("practice-service", "practice-service is unreachable", cause);
        assertThat(dependency.dependency()).isEqualTo("practice-service");
        assertThat(dependency).hasCause(cause);
        assertThat(new NotSubmittedException("in progress")).hasMessage("in progress");
        AiUnavailableException ai = new AiUnavailableException("429", Duration.ofSeconds(30), true, null);
        assertThat(ai.retryAfter()).hasSeconds(30);
        assertThat(ai.rejected()).isTrue();
    }
}
