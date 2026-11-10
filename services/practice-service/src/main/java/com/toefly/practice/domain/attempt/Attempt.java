package com.toefly.practice.domain.attempt;

import com.toefly.practice.domain.attempt.AttemptEvent.AnswerCleared;
import com.toefly.practice.domain.attempt.AttemptEvent.AnswerRecorded;
import com.toefly.practice.domain.attempt.AttemptEvent.AttemptAbandoned;
import com.toefly.practice.domain.attempt.AttemptEvent.AttemptPaused;
import com.toefly.practice.domain.attempt.AttemptEvent.AttemptResumed;
import com.toefly.practice.domain.attempt.AttemptEvent.AttemptScored;
import com.toefly.practice.domain.attempt.AttemptEvent.AttemptStarted;
import com.toefly.practice.domain.attempt.AttemptEvent.AttemptSubmitted;
import com.toefly.practice.domain.attempt.AttemptEvent.ItemScored;
import com.toefly.practice.domain.attempt.AttemptSnapshot.ItemScore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Event-sourced attempt (ADR-0010, docs/03-domain-model.md §6.5). Commands check the invariants and raise events;
 * {@link #apply} is the only place that changes state, so replaying the stream gives the same state as the commands.
 */
public final class Attempt {

    /** EXAM answers are accepted up to two seconds after the deadline (INV-ATT-02). */
    static final Duration EXAM_GRACE = Duration.ofSeconds(2);

    private @Nullable AttemptStarted start;
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;
    private final Map<UUID, Answer> answers = new HashMap<>();
    private final Map<UUID, Instant> answeredAt = new HashMap<>();
    private int answerChanges;
    private boolean paused;
    private @Nullable Instant pausedSince;
    private long pausedSeconds;
    private @Nullable Instant submittedAt;
    private @Nullable SubmitReason submitReason;
    private int activeSecondsAtSubmit;
    private final Map<UUID, ItemScore> scores = new HashMap<>();
    private @Nullable Instant scoredAt;
    private @Nullable Double rawScore;
    private int correctCount;
    private int unansweredCount;
    private @Nullable Instant abandonedAt;
    private int version;
    private int persistedVersion;
    private final List<AttemptEvent> pending = new ArrayList<>();

    private Attempt() {}

    /** A new attempt whose first event is {@code started}. */
    public static Attempt start(AttemptStarted started) {
        Attempt attempt = new Attempt();
        attempt.raise(started);
        return attempt;
    }

    /** Rebuilds the attempt from its whole stream. */
    public static Attempt replay(List<? extends AttemptEvent> history) {
        if (history.isEmpty() || !(history.getFirst() instanceof AttemptStarted)) {
            throw new IllegalArgumentException("An attempt stream starts with AttemptStarted");
        }
        Attempt attempt = new Attempt();
        history.forEach(attempt::applyAndCount);
        attempt.persistedVersion = attempt.version;
        return attempt;
    }

    /** Rebuilds the attempt from a snapshot and the events written after it. */
    public static Attempt restore(AttemptSnapshot snapshot, List<? extends AttemptEvent> tail) {
        Attempt attempt = new Attempt();
        attempt.start = snapshot.start();
        attempt.status = snapshot.status();
        attempt.answers.putAll(snapshot.answers());
        attempt.answeredAt.putAll(snapshot.answeredAt());
        attempt.answerChanges = snapshot.answerChanges();
        attempt.paused = snapshot.paused();
        attempt.pausedSince = snapshot.pausedSince();
        attempt.pausedSeconds = snapshot.pausedSeconds();
        attempt.submittedAt = snapshot.submittedAt();
        attempt.submitReason = snapshot.submitReason();
        attempt.activeSecondsAtSubmit = snapshot.activeSecondsAtSubmit();
        attempt.scores.putAll(snapshot.scores());
        attempt.scoredAt = snapshot.scoredAt();
        attempt.rawScore = snapshot.rawScore();
        attempt.correctCount = snapshot.correctCount();
        attempt.unansweredCount = snapshot.unansweredCount();
        attempt.abandonedAt = snapshot.abandonedAt();
        attempt.version = snapshot.version();
        tail.forEach(attempt::applyAndCount);
        attempt.persistedVersion = attempt.version;
        return attempt;
    }

    // ---- commands ----

    public void recordAnswer(
            UUID itemId, Answer answer, Instant now, @Nullable Instant clientTime, @Nullable Long answeredInMs) {
        requireInProgress();
        requireItem(itemId);
        requireBeforeDeadline(now);
        if (answer.blank()) {
            clearAnswer(itemId, now);
            return;
        }
        if (answer.equals(answers.get(itemId))) {
            return; // last answer wins; the same answer again changes nothing
        }
        raise(new AnswerRecorded(itemId, answer, clientTime, answeredInMs, now));
    }

    public void clearAnswer(UUID itemId, Instant now) {
        requireInProgress();
        requireItem(itemId);
        requireBeforeDeadline(now);
        if (answers.containsKey(itemId)) {
            raise(new AnswerCleared(itemId, now));
        }
    }

    /** Practice pause (AC-06.01.3); EXAM has none (INV-ATT-07). */
    public void pause(Instant now) {
        requireInProgress();
        if (started().mode() == AttemptMode.EXAM) {
            throw new AttemptStateConflictException("An exam attempt cannot be paused.");
        }
        if (!paused) {
            raise(new AttemptPaused(now));
        }
    }

    public void resume(Instant now) {
        requireInProgress();
        if (started().mode() == AttemptMode.EXAM) {
            throw new AttemptStateConflictException("An exam attempt cannot be paused.");
        }
        if (paused) {
            raise(new AttemptResumed(now));
        }
    }

    /** Freezes the answers; unanswered items will count as wrong (AC-06.01.5). */
    public void submit(Instant now, SubmitReason reason, int activeSeconds) {
        if (status != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadySubmittedException(status);
        }
        raise(new AttemptSubmitted(reason, Math.max(0, activeSeconds), now));
    }

    /** Records the check of every pinned item; a second call does nothing (INV-ATT-04). */
    public void score(List<ItemOutcome> outcomes, Instant now) {
        if (status == AttemptStatus.SCORED) {
            return;
        }
        if (status != AttemptStatus.SUBMITTED) {
            throw new AttemptStateConflictException("Only a submitted attempt can be scored.");
        }
        Map<UUID, ItemOutcome> byItem = new HashMap<>();
        outcomes.forEach(outcome -> byItem.put(outcome.itemId(), outcome));
        double total = 0;
        double max = 0;
        int correct = 0;
        int unanswered = 0;
        for (PinnedItem item : started().items()) {
            ItemOutcome outcome = byItem.get(item.itemId());
            if (outcome == null) {
                outcome = new ItemOutcome(
                        item.itemId(), item.ordinal(), 0, item.maxScore(), false, false, false, item.b());
            }
            raise(new ItemScored(
                    item.itemId(),
                    item.ordinal(),
                    outcome.score(),
                    outcome.maxScore(),
                    outcome.answered(),
                    outcome.correct(),
                    outcome.pending(),
                    item.b(),
                    now));
            total += outcome.score();
            max += outcome.maxScore();
            correct += outcome.correct() ? 1 : 0;
            unanswered += outcome.answered() ? 0 : 1;
        }
        raise(new AttemptScored(total, max, correct, unanswered, now));
    }

    /** Leaves the attempt (Save and exit is not abandoning); abandoning twice does nothing. */
    public void abandon(Instant now, AbandonReason reason) {
        if (status == AttemptStatus.ABANDONED) {
            return;
        }
        if (status != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadySubmittedException(status);
        }
        raise(new AttemptAbandoned(reason, now));
    }

    // ---- state ----

    private void raise(AttemptEvent event) {
        applyAndCount(event);
        pending.add(event);
    }

    private void applyAndCount(AttemptEvent event) {
        apply(event);
        version++;
    }

    private void apply(AttemptEvent event) {
        switch (event) {
            case AttemptStarted started -> {
                this.start = started;
                this.status = AttemptStatus.IN_PROGRESS;
            }
            case AnswerRecorded recorded -> {
                answers.put(recorded.itemId(), recorded.answer());
                answeredAt.put(recorded.itemId(), recorded.occurredAt());
                answerChanges++;
            }
            case AnswerCleared cleared -> {
                answers.remove(cleared.itemId());
                answeredAt.remove(cleared.itemId());
                answerChanges++;
            }
            case AttemptPaused pausedEvent -> {
                paused = true;
                pausedSince = pausedEvent.occurredAt();
            }
            case AttemptResumed resumed -> closePause(resumed.occurredAt());
            case AttemptSubmitted submitted -> {
                closePause(submitted.occurredAt());
                status = AttemptStatus.SUBMITTED;
                submittedAt = submitted.occurredAt();
                submitReason = submitted.reason();
                activeSecondsAtSubmit = submitted.activeSeconds();
            }
            case ItemScored scored ->
                scores.put(
                        scored.itemId(),
                        new ItemScore(
                                scored.ordinal(),
                                scored.score(),
                                scored.maxScore(),
                                scored.answered(),
                                scored.correct(),
                                scored.pending(),
                                scored.b()));
            case AttemptScored scored -> {
                status = AttemptStatus.SCORED;
                scoredAt = scored.occurredAt();
                rawScore = scored.rawScore();
                correctCount = scored.correctCount();
                unansweredCount = scored.unansweredCount();
            }
            case AttemptAbandoned abandoned -> {
                closePause(abandoned.occurredAt());
                status = AttemptStatus.ABANDONED;
                abandonedAt = abandoned.occurredAt();
            }
        }
    }

    private void closePause(Instant at) {
        if (paused && pausedSince != null) {
            pausedSeconds += Math.max(0, Duration.between(pausedSince, at).toSeconds());
        }
        paused = false;
        pausedSince = null;
    }

    private void requireInProgress() {
        if (status != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadySubmittedException(status);
        }
    }

    private void requireItem(UUID itemId) {
        if (started().items().stream().noneMatch(item -> item.itemId().equals(itemId))) {
            throw new UnknownItemException(itemId);
        }
    }

    private void requireBeforeDeadline(Instant now) {
        Instant deadline = started().deadlineAt();
        if (started().mode() == AttemptMode.EXAM && deadline != null && now.isAfter(deadline.plus(EXAM_GRACE))) {
            throw new AttemptTimeExpiredException(deadline);
        }
    }

    private AttemptStarted started() {
        return Objects.requireNonNull(start, "attempt has not started");
    }

    // ---- queries ----

    public UUID id() {
        return started().attemptId();
    }

    public UUID userId() {
        return started().userId();
    }

    public AttemptStarted startedEvent() {
        return started();
    }

    public AttemptStatus status() {
        return status;
    }

    public Map<UUID, Answer> answers() {
        return Map.copyOf(answers);
    }

    public boolean paused() {
        return paused;
    }

    public @Nullable Instant submittedAt() {
        return submittedAt;
    }

    /** Version of the stream including events not yet written. */
    public int version() {
        return version;
    }

    /** Version the store had when the attempt was loaded; the expected version of the next append. */
    public int persistedVersion() {
        return persistedVersion;
    }

    public List<AttemptEvent> pendingEvents() {
        return List.copyOf(pending);
    }

    /** Called by the store after a successful append. */
    public void markPersisted() {
        pending.clear();
        persistedVersion = version;
    }

    public AttemptSnapshot snapshot() {
        return new AttemptSnapshot(
                started(),
                status,
                answers,
                answeredAt,
                answerChanges,
                paused,
                pausedSince,
                pausedSeconds,
                submittedAt,
                submitReason,
                activeSecondsAtSubmit,
                scores,
                scoredAt,
                rawScore,
                correctCount,
                unansweredCount,
                abandonedAt,
                version);
    }
}
