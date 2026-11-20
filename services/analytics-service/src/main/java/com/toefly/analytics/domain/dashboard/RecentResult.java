package com.toefly.analytics.domain.dashboard;

import com.toefly.analytics.domain.common.Section;
import com.toefly.analytics.domain.common.TaskType;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One recent result: a scored attempt ({@code correct} of {@code total}) or an evaluated submission ({@code rubricScore}
 * 0–5). The id is the attempt or the submission, so a later version of a submission replaces the earlier one.
 */
public record RecentResult(
        UUID id,
        Kind kind,
        TaskType taskType,
        @Nullable Section section,
        @Nullable Integer correct,
        @Nullable Integer total,
        @Nullable Integer rubricScore,
        Instant at) {

    public enum Kind {
        ATTEMPT,
        SUBMISSION
    }

    public static RecentResult attempt(
            UUID attemptId, TaskType type, @Nullable Section section, int correct, int total, Instant at) {
        return new RecentResult(attemptId, Kind.ATTEMPT, type, section, correct, total, null, at);
    }

    public static RecentResult submission(
            UUID submissionId, TaskType type, @Nullable Section section, int rubricScore, Instant at) {
        return new RecentResult(submissionId, Kind.SUBMISSION, type, section, null, null, rubricScore, at);
    }
}
