package com.toefly.tutoragent.application.port.out;

import com.toefly.tutoragent.domain.review.WeeklyReview;

/** weekly-review-generated through the outbox. */
public interface ReviewEventPublisherPort {

    void weeklyReviewGenerated(WeeklyReview review, Cause cause);
}
