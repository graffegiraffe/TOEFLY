package com.toefly.practice.application.port.in;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface StartAttemptUseCase {

    /** Starts a PRACTICE attempt or returns the open one on the same set (INV-ATT-01, AC-06.01.1). */
    AttemptDetails start(StartPractice command);

    /** Starts an EXAM attempt for a module of mockexam-service. */
    AttemptDetails startExam(StartExam command);

    record StartPractice(
            UUID userId,
            UUID itemSetId,
            @Nullable UUID weekTaskId,
            @Nullable String timeZone,
            UUID correlationId) {}

    record StartExam(
            UUID userId,
            UUID itemSetId,
            UUID examSessionId,
            UUID moduleId,
            Instant deadlineAt,
            double timingMultiplier,
            UUID correlationId) {}
}
