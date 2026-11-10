package com.toefly.practice.application.service;

import com.toefly.practice.application.port.in.AttemptQuery;
import com.toefly.practice.application.port.in.UserDataUseCase;
import com.toefly.practice.application.port.out.AbilityStorePort;
import com.toefly.practice.application.port.out.AttemptStorePort;
import com.toefly.practice.application.port.out.AttemptViewPort;
import com.toefly.practice.application.port.out.TimeEntryPort;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GDPR export and erasure (docs/03-domain-model.md §14). Erasure is the one exception to the immutable event store:
 * the streams of the user are deleted (NFR-DUR-07, docs/04-architecture.md §6.6).
 */
@Service
public class UserDataService implements UserDataUseCase {

    private static final int EXPORT_LIMIT = 10_000;

    private final AttemptStorePort store;
    private final AttemptViewPort views;
    private final AbilityStorePort abilities;
    private final TimeEntryPort timeEntries;
    private final Clock clock;

    public UserDataService(
            AttemptStorePort store,
            AttemptViewPort views,
            AbilityStorePort abilities,
            TimeEntryPort timeEntries,
            Clock clock) {
        this.store = store;
        this.views = views;
        this.abilities = abilities;
        this.timeEntries = timeEntries;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserData export(UUID userId) {
        AttemptQuery all = new AttemptQuery(userId, null, null, null, Set.of(), null, null, null, false);
        return new UserData(
                userId,
                clock.instant(),
                views.page(all, null, EXPORT_LIMIT),
                abilities.findAll(userId),
                timeEntries.findAll(userId));
    }

    @Override
    @Transactional
    public void erase(UUID userId) {
        store.eraseUser(userId);
        views.eraseUser(userId);
        abilities.eraseUser(userId);
        timeEntries.eraseUser(userId);
    }
}
