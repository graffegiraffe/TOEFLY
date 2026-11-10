package com.toefly.practice.application.port.in;

import com.toefly.practice.domain.attempt.Answer;
import com.toefly.practice.domain.attempt.AttemptMode;
import com.toefly.practice.domain.attempt.AttemptStatus;
import com.toefly.practice.domain.attempt.Section;
import com.toefly.practice.domain.attempt.TaskType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Row of the attempt_view read projection (docs/04-architecture.md §6.6). */
public record AttemptView(
        UUID attemptId,
        UUID userId,
        UUID itemSetId,
        int itemSetVersion,
        TaskType taskType,
        @Nullable Section section,
        AttemptMode mode,
        AttemptStatus status,
        @Nullable UUID weekTaskId,
        @Nullable UUID examSessionId,
        Instant startedAt,
        @Nullable Instant submittedAt,
        @Nullable Instant scoredAt,
        @Nullable Double rawScore,
        double maxScore,
        int itemCount,
        int correctCount,
        int unansweredCount,
        int activeSeconds,
        int estimatedSeconds,
        Map<UUID, Answer> answers,
        Instant lastActivityAt,
        int version) {

    public AttemptView {
        answers = Map.copyOf(answers);
    }
}
