package com.toefly.vocabulary.application.service;

import com.toefly.vocabulary.application.port.in.DueView;
import com.toefly.vocabulary.application.port.in.ReviewCardView;
import com.toefly.vocabulary.application.port.in.ReviewUseCase;
import com.toefly.vocabulary.application.port.in.SessionView;
import com.toefly.vocabulary.application.port.in.VocabularyException;
import com.toefly.vocabulary.application.port.out.CardRepository;
import com.toefly.vocabulary.application.port.out.EntryRepository;
import com.toefly.vocabulary.application.port.out.LearnerSettingsRepository;
import com.toefly.vocabulary.application.port.out.ReviewLogRepository;
import com.toefly.vocabulary.application.port.out.ReviewSessionRepository;
import com.toefly.vocabulary.application.port.out.VocabularyEventPublisherPort;
import com.toefly.vocabulary.domain.card.CardSuspendedException;
import com.toefly.vocabulary.domain.card.ReviewCard;
import com.toefly.vocabulary.domain.entry.EntryContext;
import com.toefly.vocabulary.domain.entry.VocabularyEntry;
import com.toefly.vocabulary.domain.fsrs.CardState;
import com.toefly.vocabulary.domain.fsrs.FsrsScheduler;
import com.toefly.vocabulary.domain.fsrs.Rating;
import com.toefly.vocabulary.domain.fsrs.ScheduledReview;
import com.toefly.vocabulary.domain.review.CardFormat;
import com.toefly.vocabulary.domain.review.DailyQueue;
import com.toefly.vocabulary.domain.review.ReviewSession;
import com.toefly.vocabulary.domain.review.SessionStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Daily FSRS reviews (US-15.03, docs/03 §11.4–11.5). */
@Service
@Transactional
public class ReviewService implements ReviewUseCase {

    private final CardRepository cards;
    private final EntryRepository entries;
    private final ReviewLogRepository logs;
    private final ReviewSessionRepository sessions;
    private final LearnerSettingsRepository settings;
    private final VocabularyEventPublisherPort events;
    private final FsrsScheduler scheduler;
    private final Clock clock;

    public ReviewService(
            CardRepository cards,
            EntryRepository entries,
            ReviewLogRepository logs,
            ReviewSessionRepository sessions,
            LearnerSettingsRepository settings,
            VocabularyEventPublisherPort events,
            FsrsScheduler scheduler,
            Clock clock) {
        this.cards = cards;
        this.entries = entries;
        this.logs = logs;
        this.sessions = sessions;
        this.settings = settings;
        this.events = events;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public DueView due(UUID userId) {
        Instant now = clock.instant();
        LearnerDay day = LearnerDay.of(settings, userId, now);
        DailyQueue queue = queue(userId, day, now);
        return new DueView(
                queue.localDate(),
                day.zone().getId(),
                queue.dueCount(),
                queue.newCount(),
                queue.dueTomorrow(),
                views(userId, queue.cards(), day, now));
    }

    @Override
    public SessionView start(UUID userId, @Nullable UUID weekTaskId) {
        Instant now = clock.instant();
        LearnerDay day = LearnerDay.of(settings, userId, now);
        DailyQueue queue = queue(userId, day, now);
        ReviewSession session = ReviewSession.start(
                UUID.randomUUID(), userId, weekTaskId, queue, day.zone().getId(), now);
        sessions.insert(session);
        return new SessionView(
                session, views(userId, queue.cards(), day, now), queue.cards().size(), queue.dueTomorrow(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public SessionView session(UUID userId, UUID sessionId) {
        ReviewSession session = sessions.find(userId, sessionId).orElseThrow(ReviewService::sessionNotFound);
        Instant now = clock.instant();
        LearnerDay day = LearnerDay.of(settings, userId, now);
        List<ReviewCard> planned = ordered(cards.findAll(userId, session.plannedCardIds()), session.plannedCardIds());
        return new SessionView(session, views(userId, planned, day, now), 0, 0, null);
    }

    @Override
    public ReviewCardView review(
            UUID userId, UUID cardId, Rating rating, UUID sessionId, int sequenceNo, int elapsedMs) {
        Instant now = clock.instant();
        LearnerDay day = LearnerDay.of(settings, userId, now);
        ReviewSession session = sessions.findForUpdate(userId, sessionId).orElseThrow(ReviewService::sessionNotFound);
        ReviewCard card = cards.findForUpdate(userId, cardId)
                .orElseThrow(() -> VocabularyException.notFound("The card is not in your dictionary."));
        if (session.status() == SessionStatus.COMPLETED) {
            throw VocabularyException.conflict("session-completed", "The review session is already completed.");
        }
        if (!session.planned(cardId) && !dueToday(card, day, now)) {
            throw VocabularyException.conflict("card-not-due", "The card is not due for review today.");
        }
        ReviewCard.Reviewed reviewed;
        try {
            reviewed = card.review(
                    scheduler, rating, now, day.zone(), UUID.randomUUID(), sessionId, sequenceNo, elapsedMs);
        } catch (CardSuspendedException suspended) {
            throw VocabularyException.conflict("card-suspended", suspended.getMessage());
        }
        if (!logs.append(reviewed.log())) {
            // INV-VOC-04: the same grade sent again — answer with the card as it is
            return view(card, entry(userId, card), 0, day, now);
        }
        ReviewCard updated = cards.update(reviewed.card());
        sessions.update(session.graded(cardId, rating, card.state(), elapsedMs));
        return view(updated, entry(userId, updated), 0, day, now);
    }

    @Override
    public SessionView complete(UUID userId, UUID sessionId) {
        Instant now = clock.instant();
        LearnerDay day = LearnerDay.of(settings, userId, now);
        ReviewSession session = sessions.findForUpdate(userId, sessionId).orElseThrow(ReviewService::sessionNotFound);
        if (session.status() == SessionStatus.COMPLETED) {
            throw VocabularyException.conflict("session-completed", "The review session is already completed.");
        }
        ReviewSession completed = sessions.update(session.complete(now));
        DailyQueue rest = queue(userId, day, now);
        int dueRemaining = rest.dueCount();
        events.reviewCompleted(completed, dueRemaining, rest.dueTomorrow(), now);
        Long next = nextDue(userId, now);
        return new SessionView(completed, List.of(), dueRemaining, rest.dueTomorrow(), next);
    }

    // ---- helpers ----

    private DailyQueue queue(UUID userId, LearnerDay day, Instant now) {
        Instant endOfTomorrow = day.end().plus(Duration.ofDays(1));
        List<ReviewCard> candidates = cards.queueCandidates(userId, endOfTomorrow);
        ReviewLogRepository.DayCounts today = logs.countBetween(userId, day.start(), day.end());
        return DailyQueue.build(
                candidates, scheduler, now, day.zone(), day.limits(), today.newIntroduced(), today.cardsReviewed());
    }

    private boolean dueToday(ReviewCard card, LearnerDay day, Instant now) {
        return switch (card.state()) {
            case NEW -> false;
            case LEARNING, RELEARNING -> card.memory().due().isBefore(day.end());
            case REVIEW -> card.memory().due().isBefore(day.end());
        };
    }

    private @Nullable Long nextDue(UUID userId, Instant now) {
        return cards.queueCandidates(userId, now.plus(Duration.ofDays(400))).stream()
                .filter(card -> card.state() != CardState.NEW)
                .map(card -> card.memory().due())
                .filter(due -> due.isAfter(now))
                .min(Instant::compareTo)
                .map(due -> Duration.between(now, due).toSeconds())
                .orElse(null);
    }

    private List<ReviewCardView> views(UUID userId, List<ReviewCard> queue, LearnerDay day, Instant now) {
        Map<UUID, VocabularyEntry> byId =
                entries.findAll(userId, queue.stream().map(ReviewCard::entryId).toList()).stream()
                        .collect(Collectors.toMap(VocabularyEntry::id, Function.identity()));
        List<ReviewCardView> views = new ArrayList<>();
        for (int position = 0; position < queue.size(); position++) {
            ReviewCard card = queue.get(position);
            VocabularyEntry entry = byId.get(card.entryId());
            if (entry != null) {
                views.add(view(card, entry, position, day, now));
            }
        }
        return views;
    }

    private ReviewCardView view(ReviewCard card, VocabularyEntry entry, int position, LearnerDay day, Instant now) {
        Map<Rating, Duration> intervals = new EnumMap<>(Rating.class);
        for (Map.Entry<Rating, ScheduledReview> option :
                card.preview(scheduler, now, day.zone()).entrySet()) {
            intervals.put(option.getKey(), option.getValue().interval());
        }
        boolean hasDefinition = entry.details().definitionEn() != null;
        CardFormat format = CardFormat.choose(position, card.memory().reps(), hasDefinition, cloze(entry));
        return new ReviewCardView(card, entry, format, intervals);
    }

    /** The cloze format needs a sentence that contains the word. */
    static boolean cloze(VocabularyEntry entry) {
        EntryContext context = entry.latestContext();
        String sentence = context != null ? context.sentence() : entry.details().exampleEn();
        return sentence != null
                && sentence.toLowerCase(Locale.ROOT).contains(entry.lemma().toLowerCase(Locale.ROOT));
    }

    private VocabularyEntry entry(UUID userId, ReviewCard card) {
        return entries.find(userId, card.entryId()).orElseThrow();
    }

    private static List<ReviewCard> ordered(List<ReviewCard> found, List<UUID> order) {
        Map<UUID, ReviewCard> byId = found.stream().collect(Collectors.toMap(ReviewCard::id, Function.identity()));
        return order.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private static VocabularyException sessionNotFound() {
        return VocabularyException.notFound("The review session does not exist.");
    }
}
