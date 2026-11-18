package com.toefly.assessment.domain.submission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeferredEvaluationTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    @Test
    @DisplayName("AC-11.06.2 retries wait 1, 2, 4, 8, 16 minutes, then an hour")
    void backsOff() {
        DeferredEvaluation deferred =
                DeferredEvaluation.first(UUID.randomUUID(), 1, UUID.randomUUID(), NOW, null, "503");
        assertThat(deferred.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(deferred.due(NOW)).isFalse();
        assertThat(deferred.due(NOW.plusSeconds(60))).isTrue();
        for (int i = 0; i < 5; i++) {
            deferred = deferred.retried(NOW);
        }
        assertThat(deferred.attempts()).isEqualTo(6);
        assertThat(deferred.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        DeferredEvaluation again = deferred.failedAgain(NOW, null, "429");
        assertThat(again.attempts()).isEqualTo(6);
        assertThat(again.lastError()).isEqualTo("429");
        assertThat(DeferredEvaluation.delay(3, null)).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    @DisplayName("AC-11.06.2 Retry-After of ai-gateway is never shortened")
    void honoursRetryAfter() {
        DeferredEvaluation deferred =
                DeferredEvaluation.first(UUID.randomUUID(), 1, UUID.randomUUID(), NOW, Duration.ofMinutes(10), "429");
        assertThat(deferred.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("AC-11.06.4 after 24 hours the command goes to the DLQ once")
    void deadLettersOnce() {
        DeferredEvaluation deferred =
                DeferredEvaluation.first(UUID.randomUUID(), 1, UUID.randomUUID(), NOW, null, "503");
        Instant later = NOW.plus(Duration.ofHours(24));
        assertThat(deferred.shouldDeadLetter(later.minusSeconds(1))).isFalse();
        assertThat(deferred.shouldDeadLetter(later)).isTrue();
        assertThat(deferred.markDeadLettered().shouldDeadLetter(later)).isFalse();
        assertThat(deferred.markDeadLettered().failedAgain(later, null, "x").deadLettered())
                .isTrue();
    }
}
