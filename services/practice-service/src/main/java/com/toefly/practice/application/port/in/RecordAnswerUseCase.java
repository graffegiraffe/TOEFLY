package com.toefly.practice.application.port.in;

import com.toefly.practice.domain.attempt.Answer;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface RecordAnswerUseCase {

    /** Records or replaces an answer (AC-06.01.4, AC-06.03.1). */
    AnswerResult record(RecordAnswer command);

    AnswerResult clear(ClearAnswer command);

    /**
     * @param userId owner of the attempt; {@code null} for the internal API of mockexam-service
     * @param expectedVersion the If-Match version; {@code null} when the caller does not send it
     */
    record RecordAnswer(
            @Nullable UUID userId,
            UUID attemptId,
            UUID itemId,
            Answer answer,
            @Nullable Integer expectedVersion,
            @Nullable Instant clientTime,
            @Nullable Long answeredInMs) {}

    record ClearAnswer(UUID userId, UUID attemptId, UUID itemId, int expectedVersion) {}

    record AnswerResult(
            UUID itemId,
            @Nullable Answer answer,
            int attemptVersion,
            Instant recordedAt,
            @Nullable Instant deadlineAt) {}
}
