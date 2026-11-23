package com.toefly.tutoragent.application.port.in;

import com.toefly.tutoragent.application.port.out.Cause;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.learner.LearnerModel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Updates of the learner model by events (docs/06 §6.2) and its reading. */
public interface LearnerModelUseCase {

    LearnerModel get(UUID userId);

    /** Results of a scored closed attempt, enriched with the review of practice-service. */
    void attemptScored(ScoredAttempt attempt, Cause cause);

    /** An evaluated writing version; its comments are read from assessment-service. */
    void submissionEvaluated(UUID userId, UUID submissionId, int versionNo, Instant evaluatedAt, Cause cause);

    void bandsUpdated(UUID userId, Map<String, Double> bands, Cause cause);

    void goalChanged(
            UUID userId,
            @Nullable Double targetBand,
            @Nullable LocalDate examDate,
            @Nullable String timezone,
            ExplanationLanguage language,
            Cause cause);

    void languageChanged(UUID userId, ExplanationLanguage language, Cause cause);

    /** Snapshot at the end of a week. */
    void weekCompleted(UUID userId, int weekNo);

    /** Publishes learner-model-updated for windows that ended; returns how many were published. */
    int publishDue(Instant now);

    record ScoredAttempt(UUID userId, UUID attemptId, String taskType, Instant scoredAt, List<ScoredItem> items) {
        public ScoredAttempt {
            items = List.copyOf(items);
        }
    }

    record ScoredItem(UUID itemId, boolean answered, boolean correct) {}
}
