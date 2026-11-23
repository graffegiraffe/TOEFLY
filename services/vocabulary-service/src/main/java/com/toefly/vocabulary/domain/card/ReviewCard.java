package com.toefly.vocabulary.domain.card;

import com.toefly.vocabulary.domain.fsrs.CardMemory;
import com.toefly.vocabulary.domain.fsrs.CardState;
import com.toefly.vocabulary.domain.fsrs.FsrsScheduler;
import com.toefly.vocabulary.domain.fsrs.Rating;
import com.toefly.vocabulary.domain.fsrs.ScheduledReview;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The FSRS card of one entry (INV-VOC-02). The memory changes only through {@link #review} (INV-VOC-03); suspension is
 * orthogonal to the state.
 */
public record ReviewCard(
        UUID id,
        UUID entryId,
        UUID userId,
        CardMemory memory,
        boolean suspended,
        String paramsVersion,
        Instant createdAt,
        int version) {

    public static final double MASTERED_STABILITY = 90;

    public ReviewCard {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(paramsVersion, "paramsVersion");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** AC-15.01.2: a new card in the state NEW. */
    public static ReviewCard create(UUID id, UUID entryId, UUID userId, String paramsVersion, Instant now) {
        return new ReviewCard(id, entryId, userId, CardMemory.fresh(now), false, paramsVersion, now, 0);
    }

    public CardState state() {
        return memory.state();
    }

    public VocabularyStatus status() {
        return switch (memory.state()) {
            case NEW -> VocabularyStatus.NEW;
            case LEARNING, RELEARNING -> VocabularyStatus.LEARNING;
            case REVIEW ->
                memory.stability() >= MASTERED_STABILITY ? VocabularyStatus.MASTERED : VocabularyStatus.REVIEW;
        };
    }

    /** The outcome of each button, for the intervals under the grade buttons. */
    public Map<Rating, ScheduledReview> preview(FsrsScheduler scheduler, Instant now, ZoneId zone) {
        return scheduler.preview(memory, now, zone, fuzzSeed());
    }

    /** AC-15.03.3: applies a grade and returns the card with its log. */
    public Reviewed review(
            FsrsScheduler scheduler,
            Rating rating,
            Instant now,
            ZoneId zone,
            UUID logId,
            @Nullable UUID sessionId,
            int sequenceNo,
            int durationMs) {
        if (suspended) {
            throw new CardSuspendedException();
        }
        ScheduledReview result = scheduler.review(memory, rating, now, zone, fuzzSeed());
        ReviewCard next = new ReviewCard(
                id,
                entryId,
                userId,
                result.after(),
                false,
                scheduler.parameters().version(),
                createdAt,
                version);
        ReviewLog log = new ReviewLog(
                logId,
                id,
                userId,
                rating,
                memory.state(),
                result.after().state(),
                memory.stability(),
                memory.difficulty(),
                result.after().stability(),
                result.after().difficulty(),
                result.elapsedDays(),
                result.scheduledDays(),
                sessionId,
                sequenceNo,
                now,
                Math.max(0, durationMs));
        return new Reviewed(next, log, result);
    }

    public ReviewCard suspend() {
        return new ReviewCard(id, entryId, userId, memory, true, paramsVersion, createdAt, version);
    }

    public ReviewCard resume() {
        return new ReviewCard(id, entryId, userId, memory, false, paramsVersion, createdAt, version);
    }

    private long fuzzSeed() {
        return id.getMostSignificantBits() ^ id.getLeastSignificantBits();
    }

    /** A graded card, its log and the scheduler result. */
    public record Reviewed(ReviewCard card, ReviewLog log, ScheduledReview result) {}
}
