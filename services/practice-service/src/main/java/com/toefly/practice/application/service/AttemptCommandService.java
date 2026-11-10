package com.toefly.practice.application.service;

import com.toefly.practice.application.port.in.AbandonAttemptUseCase;
import com.toefly.practice.application.port.in.AttemptDetails;
import com.toefly.practice.application.port.in.AttemptReviewResult;
import com.toefly.practice.application.port.in.AttemptView;
import com.toefly.practice.application.port.in.ItemSetContent;
import com.toefly.practice.application.port.in.RecordAnswerUseCase;
import com.toefly.practice.application.port.in.StartAttemptUseCase;
import com.toefly.practice.application.port.in.SubmitAttemptUseCase;
import com.toefly.practice.application.port.in.TaskTypeNotSupportedException;
import com.toefly.practice.application.port.in.VersionMismatchException;
import com.toefly.practice.application.port.out.AttemptStorePort;
import com.toefly.practice.application.port.out.AttemptStorePort.EventMetadata;
import com.toefly.practice.application.port.out.AttemptViewPort;
import com.toefly.practice.application.port.out.ItemPoolPort;
import com.toefly.practice.application.port.out.ItemPoolPort.PoolEntry;
import com.toefly.practice.application.port.out.ItemPoolPort.PoolItem;
import com.toefly.practice.application.port.out.ItemSetCatalogPort;
import com.toefly.practice.application.port.out.ItemSetUnavailableException;
import com.toefly.practice.application.port.out.PracticeEventPublisherPort;
import com.toefly.practice.application.port.out.PracticeEventPublisherPort.ChosenOptions;
import com.toefly.practice.application.port.out.TimeEntryPort;
import com.toefly.practice.domain.attempt.AbandonReason;
import com.toefly.practice.domain.attempt.Answer;
import com.toefly.practice.domain.attempt.Attempt;
import com.toefly.practice.domain.attempt.AttemptEvent.AttemptStarted;
import com.toefly.practice.domain.attempt.AttemptMode;
import com.toefly.practice.domain.attempt.AttemptNotFoundException;
import com.toefly.practice.domain.attempt.AttemptSnapshot;
import com.toefly.practice.domain.attempt.AttemptStatus;
import com.toefly.practice.domain.attempt.ItemOutcome;
import com.toefly.practice.domain.attempt.PinnedItem;
import com.toefly.practice.domain.scoring.AttemptScorer;
import com.toefly.practice.domain.time.ContextType;
import com.toefly.practice.domain.time.TimeEntry;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Commands of the attempt lifecycle (docs/04-architecture.md §5.3). */
@Service
public class AttemptCommandService
        implements StartAttemptUseCase, RecordAnswerUseCase, SubmitAttemptUseCase, AbandonAttemptUseCase {

    static final Duration PRACTICE_IDLE_LIMIT = Duration.ofDays(7);
    static final Duration EXAM_IDLE_LIMIT = Duration.ofHours(24);
    private static final int STALE_BATCH = 200;

    private final AttemptStorePort store;
    private final AttemptViewPort views;
    private final ItemPoolPort pool;
    private final ItemSetCatalogPort catalog;
    private final TimeEntryPort timeEntries;
    private final PracticeEventPublisherPort events;
    private final AbilityService abilities;
    private final Transactions transactions;
    private final TraceContext trace;
    private final Clock clock;

    public AttemptCommandService(
            AttemptStorePort store,
            AttemptViewPort views,
            ItemPoolPort pool,
            ItemSetCatalogPort catalog,
            TimeEntryPort timeEntries,
            PracticeEventPublisherPort events,
            AbilityService abilities,
            Transactions transactions,
            TraceContext trace,
            Clock clock) {
        this.store = store;
        this.views = views;
        this.pool = pool;
        this.catalog = catalog;
        this.timeEntries = timeEntries;
        this.events = events;
        this.abilities = abilities;
        this.transactions = transactions;
        this.trace = trace;
        this.clock = clock;
    }

    // ---- start ----

    @Override
    public AttemptDetails start(StartPractice command) {
        Optional<AttemptView> open = views.findInProgress(command.userId(), command.itemSetId());
        if (open.isPresent()) {
            return transactions.once(() -> details(loadAttempt(open.get().attemptId())));
        }
        PoolEntry entry = availableEntry(command.itemSetId());
        ItemSetContent content = catalog.current(command.itemSetId());
        return transactions.once(() -> {
            Optional<AttemptView> raced = views.findInProgress(command.userId(), command.itemSetId());
            if (raced.isPresent()) {
                return details(loadAttempt(raced.get().attemptId()));
            }
            Instant now = clock.instant();
            AttemptStarted started = new AttemptStarted(
                    UUID.randomUUID(),
                    command.userId(),
                    command.itemSetId(),
                    content.version(),
                    content.taskType(),
                    content.section(),
                    content.examFormat(),
                    AttemptMode.PRACTICE,
                    pin(entry, content),
                    command.weekTaskId(),
                    null,
                    null,
                    null,
                    content.estimatedSeconds(),
                    1.0,
                    zone(command.timeZone()).getId(),
                    views.hasScored(command.userId(), command.itemSetId()),
                    now);
            return begin(started, command.correlationId());
        });
    }

    @Override
    public AttemptDetails startExam(StartExam command) {
        PoolEntry entry = availableEntry(command.itemSetId());
        ItemSetContent content = catalog.current(command.itemSetId());
        return transactions.once(() -> {
            Instant now = clock.instant();
            AttemptStarted started = new AttemptStarted(
                    UUID.randomUUID(),
                    command.userId(),
                    command.itemSetId(),
                    content.version(),
                    content.taskType(),
                    content.section(),
                    content.examFormat(),
                    AttemptMode.EXAM,
                    pin(entry, content),
                    null,
                    command.examSessionId(),
                    command.moduleId(),
                    command.deadlineAt(),
                    content.estimatedSeconds(),
                    command.timingMultiplier(),
                    ZoneOffset.UTC.getId(),
                    false,
                    now);
            return begin(started, command.correlationId());
        });
    }

    private PoolEntry availableEntry(UUID itemSetId) {
        PoolEntry entry = pool.find(itemSetId).orElseThrow(() -> new ItemSetUnavailableException(itemSetId));
        if (!entry.available()) {
            throw new ItemSetUnavailableException(itemSetId);
        }
        return entry;
    }

    /** Joins the items of the content with the identifiers and difficulties of the published set. */
    private static List<PinnedItem> pin(PoolEntry entry, ItemSetContent content) {
        if (!content.taskType().closed()) {
            throw new TaskTypeNotSupportedException(content.taskType());
        }
        Map<Integer, PoolItem> byOrdinal = new HashMap<>();
        entry.items().forEach(item -> byOrdinal.put(item.ordinal(), item));
        List<PinnedItem> items = new ArrayList<>();
        for (Integer ordinal : content.items().keySet().stream().sorted().toList()) {
            PoolItem item = byOrdinal.get(ordinal);
            if (item == null) {
                throw new ItemSetUnavailableException(content.itemSetId());
            }
            items.add(new PinnedItem(
                    item.itemId(), ordinal, item.b(), content.maxScores().getOrDefault(ordinal, 1.0)));
        }
        if (items.isEmpty()) {
            throw new ItemSetUnavailableException(content.itemSetId());
        }
        return items;
    }

    private AttemptDetails begin(AttemptStarted started, UUID correlationId) {
        Attempt attempt = Attempt.start(started);
        store.append(attempt, metadata(started.userId(), correlationId));
        TimeEntry time = TimeEntry.open(
                started.userId(),
                ContextType.ATTEMPT,
                started.attemptId(),
                started.taskType(),
                started.taskType().weekSection(),
                LocalDate.ofInstant(started.occurredAt(), ZoneId.of(started.timeZone())),
                started.occurredAt(),
                started.estimatedSeconds());
        timeEntries.save(time);
        AttemptSnapshot state = attempt.snapshot();
        views.upsert(AttemptProjector.project(state, 0, started.occurredAt()));
        events.attemptStarted(state, correlationId);
        return details(attempt);
    }

    private static ZoneId zone(@Nullable String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException unknown) {
            return ZoneOffset.UTC;
        }
    }

    // ---- answers ----

    @Override
    public AnswerResult record(RecordAnswer command) {
        return transactions.once(() -> {
            Attempt attempt = loadFor(command.userId(), command.attemptId());
            requireVersion(attempt, command.expectedVersion());
            Instant now = clock.instant();
            attempt.recordAnswer(command.itemId(), command.answer(), now, command.clientTime(), command.answeredInMs());
            persist(attempt, now, null);
            Answer answer = attempt.answers().get(command.itemId());
            return new AnswerResult(
                    command.itemId(),
                    answer,
                    attempt.version(),
                    now,
                    attempt.startedEvent().deadlineAt());
        });
    }

    @Override
    public AnswerResult clear(ClearAnswer command) {
        return transactions.once(() -> {
            Attempt attempt = loadFor(command.userId(), command.attemptId());
            requireVersion(attempt, command.expectedVersion());
            Instant now = clock.instant();
            attempt.clearAnswer(command.itemId(), now);
            persist(attempt, now, null);
            return new AnswerResult(
                    command.itemId(),
                    null,
                    attempt.version(),
                    now,
                    attempt.startedEvent().deadlineAt());
        });
    }

    private static void requireVersion(Attempt attempt, @Nullable Integer expected) {
        if (expected != null && expected != attempt.persistedVersion()) {
            throw new VersionMismatchException(attempt.persistedVersion());
        }
    }

    // ---- submit ----

    @Override
    public AttemptReviewResult submit(Submit command) {
        return transactions.retryOnConflict(() -> doSubmit(command));
    }

    private AttemptReviewResult doSubmit(Submit command) {
        Attempt attempt = loadFor(command.userId(), command.attemptId());
        Instant now = clock.instant();
        for (FinalAnswer answer : command.finalAnswers()) {
            attempt.recordAnswer(answer.itemId(), answer.answer(), now, null, null);
        }
        AttemptStarted start = attempt.startedEvent();
        TimeEntry time = timeEntry(start);
        time.close(now, pausedSecondsUntil(attempt.snapshot(), now));
        attempt.submit(now, command.reason(), time.activeSeconds());
        ItemSetContent content = pinnedContent(start);
        List<ItemOutcome> outcomes =
                AttemptScorer.score(start.items(), content.keys().keys(), attempt.answers());
        attempt.score(outcomes, now);

        AttemptSnapshot state = persist(attempt, now, command.correlationId());
        timeEntries.save(time);
        Optional<AbilityService.Update> ability = abilities.apply(state, outcomes, now);
        int answered = (int) outcomes.stream().filter(ItemOutcome::answered).count();
        events.attemptSubmitted(state, answered, command.correlationId());
        events.attemptScored(
                state,
                time.activeSeconds(),
                ability.map(update -> update.before().theta()).orElse(null),
                time.audioReplays() == 0,
                choices(state),
                command.correlationId());
        ability.ifPresent(update ->
                events.abilityUpdated(start.attemptId(), update.before(), update.after(), command.correlationId()));
        events.timeTracked(time, start.items().size(), section(start), command.correlationId());
        return ReviewAssembler.assemble(
                state,
                content,
                time.activeSeconds(),
                time.audioReplays(),
                ability.map(AbilityService.Update::change).orElse(null));
    }

    private static long pausedSecondsUntil(AttemptSnapshot state, Instant now) {
        long open = state.paused() && state.pausedSince() != null
                ? Math.max(0, Duration.between(state.pausedSince(), now).toSeconds())
                : 0;
        return state.pausedSeconds() + open;
    }

    private static List<ChosenOptions> choices(AttemptSnapshot state) {
        List<ChosenOptions> choices = new ArrayList<>();
        state.answers().forEach((itemId, answer) -> {
            if (answer instanceof Answer.Choice choice) {
                choices.add(new ChosenOptions(itemId, choice.optionIds()));
            }
        });
        return choices;
    }

    // ---- abandon ----

    @Override
    public AttemptDetails abandon(UUID userId, UUID attemptId, UUID correlationId) {
        return transactions.retryOnConflict(() -> {
            Attempt attempt = loadFor(userId, attemptId);
            close(attempt, AbandonReason.USER_EXIT, correlationId);
            return details(attempt);
        });
    }

    @Override
    public int abandonStale() {
        Instant now = clock.instant();
        int abandoned = 0;
        for (AttemptMode mode : AttemptMode.values()) {
            Duration limit = mode == AttemptMode.EXAM ? EXAM_IDLE_LIMIT : PRACTICE_IDLE_LIMIT;
            for (UUID attemptId : views.stale(mode, now.minus(limit), STALE_BATCH)) {
                abandoned += transactions.retryOnConflict(() -> {
                    Attempt attempt = loadAttempt(attemptId);
                    if (attempt.status() != AttemptStatus.IN_PROGRESS) {
                        return 0;
                    }
                    close(attempt, AbandonReason.INACTIVITY, UUID.randomUUID());
                    return 1;
                });
            }
        }
        return abandoned;
    }

    private void close(Attempt attempt, AbandonReason reason, UUID correlationId) {
        Instant now = clock.instant();
        boolean wasOpen = attempt.status() == AttemptStatus.IN_PROGRESS;
        long paused = pausedSecondsUntil(attempt.snapshot(), now);
        attempt.abandon(now, reason);
        if (!wasOpen) {
            return;
        }
        AttemptStarted start = attempt.startedEvent();
        TimeEntry time = timeEntry(start);
        time.close(now, paused);
        timeEntries.save(time);
        persist(attempt, now, correlationId);
        events.timeTracked(time, start.items().size(), section(start), correlationId);
    }

    // ---- shared ----

    private Attempt loadAttempt(UUID attemptId) {
        return store.load(attemptId).orElseThrow(() -> new AttemptNotFoundException(attemptId));
    }

    /** The attempt of {@code userId}; another user's attempt looks missing. Internal callers pass null. */
    private Attempt loadFor(@Nullable UUID userId, UUID attemptId) {
        Attempt attempt = loadAttempt(attemptId);
        if (userId != null && !attempt.userId().equals(userId)) {
            throw new AttemptNotFoundException(attemptId);
        }
        return attempt;
    }

    private AttemptSnapshot persist(Attempt attempt, Instant now, @Nullable UUID correlationId) {
        if (attempt.pendingEvents().isEmpty()) {
            return attempt.snapshot();
        }
        store.append(attempt, metadata(attempt.userId(), correlationId == null ? UUID.randomUUID() : correlationId));
        AttemptSnapshot state = attempt.snapshot();
        int active = timeEntries
                .find(ContextType.ATTEMPT, attempt.id(), attempt.userId())
                .map(TimeEntry::activeSeconds)
                .orElse(0);
        views.upsert(AttemptProjector.project(state, active, now));
        return state;
    }

    private TimeEntry timeEntry(AttemptStarted start) {
        return timeEntries
                .find(ContextType.ATTEMPT, start.attemptId(), start.userId())
                .orElseGet(() -> TimeEntry.open(
                        start.userId(),
                        ContextType.ATTEMPT,
                        start.attemptId(),
                        start.taskType(),
                        start.taskType().weekSection(),
                        LocalDate.ofInstant(start.occurredAt(), ZoneId.of(start.timeZone())),
                        start.occurredAt(),
                        start.estimatedSeconds()));
    }

    private ItemSetContent pinnedContent(AttemptStarted start) {
        return catalog.pinned(start.itemSetId(), start.itemSetVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "Pinned item set " + start.itemSetId() + " v" + start.itemSetVersion() + " is missing"));
    }

    private AttemptDetails details(Attempt attempt) {
        AttemptStarted start = attempt.startedEvent();
        int active = timeEntries
                .find(ContextType.ATTEMPT, start.attemptId(), start.userId())
                .map(TimeEntry::activeSeconds)
                .orElse(0);
        return new AttemptDetails(attempt.snapshot(), pinnedContent(start), active);
    }

    private EventMetadata metadata(UUID userId, UUID correlationId) {
        return new EventMetadata(userId, correlationId, trace.traceparent());
    }

    private static @Nullable String section(AttemptStarted start) {
        return start.section() == null ? null : start.section().name();
    }
}
