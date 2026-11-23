package com.toefly.vocabulary.domain.card;

/** A suspended card is not reviewed (AC-15.06.2). */
public class CardSuspendedException extends RuntimeException {

    public CardSuspendedException() {
        super("The card is suspended.");
    }
}
