package com.toefly.vocabulary.application.port.in;

import com.toefly.vocabulary.domain.card.ReviewCard;
import com.toefly.vocabulary.domain.entry.VocabularyEntry;
import com.toefly.vocabulary.domain.fsrs.Rating;
import com.toefly.vocabulary.domain.review.CardFormat;
import java.time.Duration;
import java.util.Map;

/** A card to show: the entry, the format of the front side and the interval under each grade button. */
public record ReviewCardView(
        ReviewCard card, VocabularyEntry entry, CardFormat format, Map<Rating, Duration> intervals) {

    public ReviewCardView {
        intervals = Map.copyOf(intervals);
    }
}
