package com.toefly.vocabulary.application.service;

import com.toefly.vocabulary.application.port.in.InternalVocabularyUseCase;
import com.toefly.vocabulary.application.port.in.VocabularyException;
import com.toefly.vocabulary.application.port.out.CardRepository;
import com.toefly.vocabulary.application.port.out.EntryRepository;
import com.toefly.vocabulary.application.port.out.LearnerSettingsRepository;
import com.toefly.vocabulary.application.port.out.ReviewLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Summary for tutor-agent and the GDPR export (docs/05 §9.3.4). */
@Service
@Transactional(readOnly = true)
public class InternalVocabularyService implements InternalVocabularyUseCase {

    static final int MAX_RECENT = 50;
    static final int LAPSED = 10;

    private final EntryRepository entries;
    private final CardRepository cards;
    private final ReviewLogRepository logs;
    private final LearnerSettingsRepository settings;
    private final Clock clock;

    public InternalVocabularyService(
            EntryRepository entries,
            CardRepository cards,
            ReviewLogRepository logs,
            LearnerSettingsRepository settings,
            Clock clock) {
        this.entries = entries;
        this.cards = cards;
        this.logs = logs;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public Summary summary(UUID userId, int recentLimit) {
        if (recentLimit < 0 || recentLimit > MAX_RECENT) {
            throw VocabularyException.invalid("recentLimit must be 0..50.");
        }
        Instant now = clock.instant();
        LearnerDay day = LearnerDay.of(settings, userId, now);
        return new Summary(
                entries.count(userId),
                cards.countDue(userId, day.end()),
                entries.recent(userId, recentLimit),
                cards.mostLapsed(userId, LAPSED));
    }

    @Override
    public Export export(UUID userId) {
        LearnerDay day = LearnerDay.of(settings, userId, clock.instant());
        return new Export(
                userId, day.zone().getId(), day.limits().newCardsPerDay(), entries.all(userId), logs.all(userId));
    }
}
