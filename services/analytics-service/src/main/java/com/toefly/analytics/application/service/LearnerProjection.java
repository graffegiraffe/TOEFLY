package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.OnboardingCompleted;
import com.toefly.analytics.application.port.in.event.PreferencesUpdated;
import com.toefly.analytics.application.port.in.event.StudyPlanCreated;
import com.toefly.analytics.application.port.out.LearnerRepository;
import com.toefly.analytics.domain.learner.LearnerSettings;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Settings of the learner the other read models depend on: day boundaries, daily goal, exam goal. */
@Component
class LearnerProjection implements Projection {

    private final LearnerRepository learners;

    LearnerProjection(LearnerRepository learners) {
        this.learners = learners;
    }

    @Override
    public void apply(AnalyticsEvent event) {
        switch (event) {
            case OnboardingCompleted e ->
                learners.save(load(e.userId())
                        .schedule(zone(e.timezone()), e.minutesPerDay(), e.at())
                        .goal(e.examDate(), e.overallTarget(), e.sectionTargets(), e.at()));
            case PreferencesUpdated e ->
                learners.save(load(e.userId()).schedule(zone(e.timezone()), e.minutesPerDay(), e.at()));
            case StudyPlanCreated e ->
                learners.save(load(e.userId())
                        .schedule(zone(e.timezone()), e.minutesPerDay(), e.at())
                        .examDate(e.examDate(), e.at()));
            default -> {
                // other events do not change the settings
            }
        }
    }

    @Override
    public void reset() {
        learners.reset();
    }

    LearnerSettings load(UUID userId) {
        return learners.find(userId).orElseGet(() -> LearnerSettings.unknown(userId));
    }

    static ZoneId zone(String name) {
        try {
            return ZoneId.of(name);
        } catch (DateTimeException unknown) {
            return ZoneOffset.UTC;
        }
    }
}
