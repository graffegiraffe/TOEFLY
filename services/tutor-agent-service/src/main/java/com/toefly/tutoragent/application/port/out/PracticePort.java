package com.toefly.tutoragent.application.port.out;

import com.toefly.tutoragent.application.port.NotSubmittedException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** practice-service internal API (docs/05 §9.3.2): reviews of submitted attempts. */
public interface PracticePort {

    /** Review of a submitted attempt; {@link NotSubmittedException} for one still in progress. */
    AttemptReview review(UUID attemptId, @Nullable String bearer);

    record AttemptReview(UUID attemptId, String taskType, List<ItemReview> items) {
        public AttemptReview {
            items = List.copyOf(items);
        }
    }

    /**
     * One question: the texts of the given and correct answers (option texts, typed words or assembled sentence)
     * and the explanation of the correct answer.
     */
    record ItemReview(
            UUID itemId,
            int ordinal,
            boolean correct,
            boolean answered,
            @Nullable String answer,
            @Nullable String key,
            @Nullable String explanation) {}
}
