package com.toefly.planning.application.port.in;

import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.domain.composition.WeekFocus.FocusPoint;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** The next week follows the weekly review of tutor-agent (docs/06-ai-agent.md §5.2, docs/12 phase 5c decision 1). */
public interface DraftNextWeekUseCase {

    /**
     * {@code weekly-review-generated}: drafts the week after the reviewed one with WeekGenerator when it has not
     * started yet. The model is called outside the transaction; {@code commitOnce} runs the write once per event.
     */
    void weeklyReviewGenerated(WeeklyReview review, Cause cause, Predicate<Runnable> commitOnce);

    /**
     * What planning needs of a weekly review.
     *
     * @param weekNo the reviewed (closed) week; the draft is for {@code weekNo + 1}
     * @param topMistakes up to 5 recurring mistakes of the reviewed week
     * @param nextWeekFocus 1 to 3 focus points
     */
    record WeeklyReview(
            UUID reviewId,
            UUID userId,
            UUID weekId,
            int weekNo,
            boolean lowActivity,
            List<ReviewMistake> topMistakes,
            List<FocusPoint> nextWeekFocus) {

        public WeeklyReview {
            topMistakes = List.copyOf(topMistakes);
            nextWeekFocus = List.copyOf(nextWeekFocus);
        }
    }

    record ReviewMistake(String subcategory, int count) {}
}
