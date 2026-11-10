package com.toefly.planning.application.port.in;

import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.domain.week.TaskType;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Attempts of practice-service move the tasks of the week (docs/03-domain-model.md §10.8). */
public interface TrackAttemptUseCase {

    void attemptStarted(UUID userId, @Nullable UUID weekTaskId, UUID attemptId, boolean retry, Instant startedAt);

    void attemptScored(
            UUID userId,
            @Nullable UUID weekTaskId,
            UUID attemptId,
            TaskType taskType,
            int correct,
            int total,
            boolean practice,
            Instant scoredAt,
            Cause cause);
}
