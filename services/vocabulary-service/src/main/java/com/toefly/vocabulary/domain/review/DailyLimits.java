package com.toefly.vocabulary.domain.review;

/** INV-VOC-05: new cards per day 5–30 (10 by default) and at most 100 cards a day (AC-15.03.1). */
public record DailyLimits(int newCardsPerDay, int cardsPerDay) {

    public static final int MIN_NEW = 5;
    public static final int MAX_NEW = 30;
    public static final int DEFAULT_NEW = 10;
    public static final int CAP = 100;

    public DailyLimits {
        if (newCardsPerDay < MIN_NEW || newCardsPerDay > MAX_NEW) {
            throw new IllegalArgumentException("newCardsPerDay must be 5..30");
        }
        if (cardsPerDay < 1 || cardsPerDay > CAP) {
            throw new IllegalArgumentException("cardsPerDay must be 1..100");
        }
    }

    public static DailyLimits of(int newCardsPerDay) {
        return new DailyLimits(newCardsPerDay, CAP);
    }

    public static DailyLimits defaults() {
        return of(DEFAULT_NEW);
    }
}
