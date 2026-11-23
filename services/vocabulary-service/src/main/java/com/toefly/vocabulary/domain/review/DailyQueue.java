package com.toefly.vocabulary.domain.review;

import com.toefly.vocabulary.domain.card.ReviewCard;
import com.toefly.vocabulary.domain.fsrs.CardState;
import com.toefly.vocabulary.domain.fsrs.FsrsScheduler;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The cards of the learner's day (docs/03 §11.5, AC-15.03.1): learning cards due now, review cards due by the end of
 * the local day, then new cards within the daily limits.
 *
 * @param cards the queue in showing order
 * @param dueCount review and learning cards due today (before the cap)
 * @param newCount new cards in the queue
 * @param dueTomorrow cards that will be due by the end of tomorrow (for the time of tomorrow's task)
 */
public record DailyQueue(LocalDate localDate, List<ReviewCard> cards, int dueCount, int newCount, int dueTomorrow) {

    public DailyQueue {
        cards = List.copyOf(cards);
    }

    /**
     * @param newIntroducedToday new cards already graded today
     * @param reviewedToday cards already graded today (each card once)
     */
    public static DailyQueue build(
            List<ReviewCard> all,
            FsrsScheduler scheduler,
            Instant now,
            ZoneId zone,
            DailyLimits limits,
            int newIntroducedToday,
            int reviewedToday) {
        LocalDate today = now.atZone(zone).toLocalDate();
        Instant endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant endOfTomorrow = today.plusDays(2).atStartOfDay(zone).toInstant();

        List<ReviewCard> learning = new ArrayList<>();
        List<ReviewCard> review = new ArrayList<>();
        List<ReviewCard> fresh = new ArrayList<>();
        int dueTomorrow = 0;
        for (ReviewCard card : all) {
            if (card.suspended()) {
                continue;
            }
            CardState state = card.state();
            Instant due = card.memory().due();
            if (state == CardState.NEW) {
                fresh.add(card);
                continue;
            }
            if (due.isBefore(endOfTomorrow)) {
                dueTomorrow++;
            }
            if ((state == CardState.LEARNING || state == CardState.RELEARNING) && !due.isAfter(now)) {
                learning.add(card);
            } else if (state == CardState.REVIEW && due.isBefore(endOfToday)) {
                review.add(card);
            }
        }
        learning.sort(Comparator.comparing((ReviewCard c) -> c.memory().due()));
        review.sort(Comparator.comparingDouble((ReviewCard c) -> scheduler.retrievability(c.memory(), now, zone))
                .thenComparing(c -> c.memory().due()));
        fresh.sort(Comparator.comparing(ReviewCard::createdAt).thenComparing(ReviewCard::id));

        int capLeft = Math.max(0, limits.cardsPerDay() - reviewedToday);
        List<ReviewCard> queue = new ArrayList<>();
        int dueCount = learning.size() + review.size();
        for (ReviewCard card : concat(learning, review)) {
            if (queue.size() >= capLeft) {
                break;
            }
            queue.add(card);
        }
        int newLeft = Math.max(0, limits.newCardsPerDay() - newIntroducedToday);
        int newShown = Math.min(Math.min(newLeft, fresh.size()), capLeft - queue.size());
        queue.addAll(fresh.subList(0, Math.max(0, newShown)));
        return new DailyQueue(today, queue, dueCount, Math.max(0, newShown), dueTomorrow);
    }

    private static List<ReviewCard> concat(List<ReviewCard> a, List<ReviewCard> b) {
        List<ReviewCard> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
