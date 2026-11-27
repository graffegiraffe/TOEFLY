package com.toefly.tutoragent.application.service;

import com.toefly.tutoragent.application.port.in.ProjectionUseCase;
import com.toefly.tutoragent.application.port.out.ProjectionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Keeps the projections; each call runs inside the consumer's transaction. */
public class ProjectionService implements ProjectionUseCase {

    private final ProjectionRepository projections;

    public ProjectionService(ProjectionRepository projections) {
        this.projections = projections;
    }

    @Override
    public void entitlementsChanged(UUID userId, Collection<String> entitlements, long version, Instant at) {
        projections.entitlements(userId, entitlements, version, at);
    }

    @Override
    public void examAttemptStarted(UUID userId, UUID attemptId, Instant at) {
        projections.examStarted(userId, attemptId, at);
    }

    @Override
    public void examAttemptEnded(UUID attemptId) {
        projections.examEnded(attemptId);
    }

    @Override
    public void active(UUID userId, Instant at) {
        projections.activity(userId, at);
    }

    @Override
    public void profileKnown(UUID userId, @Nullable String timezone, @Nullable LocalDate examDate) {
        projections.profile(userId, timezone, examDate);
    }

    @Override
    public void weekProgress(
            UUID userId, UUID weekId, int weekNo, int progressPercent, boolean rebalanced, Instant at) {
        projections.weekProgress(userId, weekId, weekNo, progressPercent, rebalanced, at);
    }

    @Override
    public void accountDeletionRequested(UUID userId) {
        projections.erase(userId);
    }
}
