package com.toefly.vocabulary.application.port.in;

import java.time.LocalDate;
import java.util.List;

/** The learner's day (AC-15.03.1): the queue and its counts. */
public record DueView(
        LocalDate localDate, String timezone, int dueCount, int newCount, int dueTomorrow, List<ReviewCardView> cards) {

    public DueView {
        cards = List.copyOf(cards);
    }
}
