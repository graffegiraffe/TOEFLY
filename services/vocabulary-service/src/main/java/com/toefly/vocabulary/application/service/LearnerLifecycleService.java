package com.toefly.vocabulary.application.service;

import com.toefly.vocabulary.application.port.in.LearnerLifecycleUseCase;
import com.toefly.vocabulary.application.port.out.EntryRepository;
import com.toefly.vocabulary.application.port.out.LearnerSettingsRepository;
import com.toefly.vocabulary.application.port.out.ReviewSessionRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The learner's zone from profile and the erasure of the account (docs/04 §5.11). */
@Service
@Transactional
public class LearnerLifecycleService implements LearnerLifecycleUseCase {

    private static final Logger log = LoggerFactory.getLogger(LearnerLifecycleService.class);

    private final LearnerSettingsRepository settings;
    private final EntryRepository entries;
    private final ReviewSessionRepository sessions;

    public LearnerLifecycleService(
            LearnerSettingsRepository settings, EntryRepository entries, ReviewSessionRepository sessions) {
        this.settings = settings;
        this.entries = entries;
        this.sessions = sessions;
    }

    @Override
    public void preferencesUpdated(UUID userId, String timezone) {
        try {
            settings.saveTimezone(userId, ZoneId.of(timezone));
        } catch (DateTimeException unknown) {
            log.warn("Unknown time zone '{}' of user {} is ignored", timezone, userId);
        }
    }

    @Override
    public void accountDeletionRequested(UUID userId) {
        entries.deleteAllOf(userId);
        sessions.deleteAllOf(userId);
        settings.delete(userId);
    }
}
