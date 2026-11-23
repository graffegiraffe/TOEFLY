package com.toefly.vocabulary.application.port.in;

import com.toefly.vocabulary.domain.review.ReviewSession;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A session with its cards; {@code dueRemaining} and {@code nextDueInSeconds} are filled once completed. */
public record SessionView(
        ReviewSession session,
        List<ReviewCardView> cards,
        int dueRemaining,
        int dueTomorrow,
        @Nullable Long nextDueInSeconds) {

    public SessionView {
        cards = List.copyOf(cards);
    }
}
