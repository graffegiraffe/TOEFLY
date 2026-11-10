package com.toefly.practice.application.port.in;

import com.toefly.practice.domain.attempt.Answer;
import com.toefly.practice.domain.attempt.SubmitReason;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface SubmitAttemptUseCase {

    /**
     * Applies the answers that were not synced, freezes the attempt, scores it, updates theta and returns the
     * review; every integration event goes to the outbox in the same transaction (AC-06.01.6).
     */
    AttemptReviewResult submit(Submit command);

    /** @param userId owner; {@code null} for the internal API of mockexam-service */
    record Submit(
            @Nullable UUID userId,
            UUID attemptId,
            List<FinalAnswer> finalAnswers,
            SubmitReason reason,
            UUID correlationId) {

        public Submit {
            finalAnswers = List.copyOf(finalAnswers);
        }
    }

    record FinalAnswer(UUID itemId, Answer answer) {}
}
