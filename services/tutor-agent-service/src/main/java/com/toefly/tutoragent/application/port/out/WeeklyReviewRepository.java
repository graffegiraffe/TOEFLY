package com.toefly.tutoragent.application.port.out;

import com.toefly.tutoragent.domain.review.WeeklyReview;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Weekly reviews; one per week (INV-AGT-03). */
public interface WeeklyReviewRepository {

    void save(WeeklyReview review);

    Optional<WeeklyReview> find(UUID reviewId);

    Optional<WeeklyReview> findByWeek(UUID weekId);

    /** Reviews of a learner, latest week first, after the cursor week number. */
    List<WeeklyReview> list(UUID userId, @Nullable Integer beforeWeekNo, int limit);

    Optional<WeeklyReview> latest(UUID userId, int beforeWeekNo);
}
