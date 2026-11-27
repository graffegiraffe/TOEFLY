package com.toefly.tutoragent.application.port.in;

import com.toefly.tutoragent.application.port.out.ClosedWeek;
import com.toefly.tutoragent.domain.review.WeeklyReview;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Weekly reviews (docs/06 §5.3, US-18.01, US-18.03). */
public interface WeeklyReviewUseCase {

    /** Generates the review of a closed week; defers it when AI is unavailable. */
    void weekCompleted(ClosedWeek week);

    /** Retries deferred reviews that are due; returns how many were handled. */
    int retryDeferred(Instant now);

    /** A review of the learner; {@link EntitlementRequiredException} without WEEKLY_REVIEW. */
    Optional<WeeklyReview> get(UUID userId, UUID reviewId);

    List<WeeklyReview> list(UUID userId, @Nullable UUID weekId, @Nullable Integer beforeWeekNo, int limit);
}
