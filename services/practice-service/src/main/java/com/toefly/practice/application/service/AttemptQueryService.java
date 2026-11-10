package com.toefly.practice.application.service;

import com.toefly.practice.application.port.in.AttemptCursor;
import com.toefly.practice.application.port.in.AttemptDetails;
import com.toefly.practice.application.port.in.AttemptNotScoredException;
import com.toefly.practice.application.port.in.AttemptQuery;
import com.toefly.practice.application.port.in.AttemptReviewResult;
import com.toefly.practice.application.port.in.AttemptView;
import com.toefly.practice.application.port.in.Caller;
import com.toefly.practice.application.port.in.GetAttemptUseCase;
import com.toefly.practice.application.port.in.ItemSetContent;
import com.toefly.practice.application.port.out.AttemptStorePort;
import com.toefly.practice.application.port.out.AttemptViewPort;
import com.toefly.practice.application.port.out.ItemSetCatalogPort;
import com.toefly.practice.application.port.out.TimeEntryPort;
import com.toefly.practice.domain.attempt.Attempt;
import com.toefly.practice.domain.attempt.AttemptEvent.AttemptStarted;
import com.toefly.practice.domain.attempt.AttemptNotFoundException;
import com.toefly.practice.domain.attempt.AttemptSnapshot;
import com.toefly.practice.domain.attempt.AttemptStatus;
import com.toefly.practice.domain.time.ContextType;
import com.toefly.practice.domain.time.TimeEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads attempts and reviews (AC-11.01.1); the review is rebuilt from the events and the pinned set. */
@Service
@Transactional(readOnly = true)
public class AttemptQueryService implements GetAttemptUseCase {

    private final AttemptStorePort store;
    private final AttemptViewPort views;
    private final ItemSetCatalogPort catalog;
    private final TimeEntryPort timeEntries;
    private final AbilityService abilities;
    private final StudentAccess access;

    public AttemptQueryService(
            AttemptStorePort store,
            AttemptViewPort views,
            ItemSetCatalogPort catalog,
            TimeEntryPort timeEntries,
            AbilityService abilities,
            StudentAccess access) {
        this.store = store;
        this.views = views;
        this.catalog = catalog;
        this.timeEntries = timeEntries;
        this.abilities = abilities;
        this.access = access;
    }

    @Override
    public AttemptDetails get(Caller caller, UUID attemptId) {
        Attempt attempt = load(attemptId);
        access.requireReadable(caller, attempt.userId(), attemptId);
        return new AttemptDetails(
                attempt.snapshot(),
                content(attempt.startedEvent()),
                time(attempt).map(TimeEntry::activeSeconds).orElse(0));
    }

    @Override
    public AttemptReviewResult review(Caller caller, UUID attemptId) {
        Attempt attempt = load(attemptId);
        access.requireReadable(caller, attempt.userId(), attemptId);
        return review(attempt);
    }

    @Override
    public AttemptReviewResult reviewInternal(UUID attemptId) {
        return review(load(attemptId));
    }

    private AttemptReviewResult review(Attempt attempt) {
        AttemptSnapshot state = attempt.snapshot();
        if (state.status() != AttemptStatus.SCORED) {
            throw new AttemptNotScoredException(state.status());
        }
        Optional<TimeEntry> time = time(attempt);
        return ReviewAssembler.assemble(
                state,
                content(attempt.startedEvent()),
                state.activeSecondsAtSubmit(),
                time.map(TimeEntry::audioReplays).orElse(0),
                abilities.change(attempt.id()).orElse(null));
    }

    @Override
    public List<AttemptView> list(
            Caller caller, @Nullable UUID studentId, AttemptQuery query, @Nullable AttemptCursor after, int limit) {
        return views.page(query.forUser(access.target(caller, studentId)), after, limit);
    }

    @Override
    public List<AttemptView> listOf(AttemptQuery query, int limit) {
        return views.page(query, null, limit);
    }

    private Attempt load(UUID attemptId) {
        return store.load(attemptId).orElseThrow(() -> new AttemptNotFoundException(attemptId));
    }

    private Optional<TimeEntry> time(Attempt attempt) {
        return timeEntries.find(ContextType.ATTEMPT, attempt.id(), attempt.userId());
    }

    private ItemSetContent content(AttemptStarted start) {
        return catalog.pinned(start.itemSetId(), start.itemSetVersion())
                .orElseThrow(() -> new IllegalStateException("Pinned item set " + start.itemSetId() + " is missing"));
    }
}
