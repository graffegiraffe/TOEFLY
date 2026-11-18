package com.toefly.assessment.application.port.in;

import com.toefly.assessment.domain.submission.SubmissionStatus;
import com.toefly.assessment.domain.submission.TaskType;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Filters of {@code GET /submissions}. */
public record SubmissionFilter(
        @Nullable TaskType taskType,
        @Nullable SubmissionStatus status,
        @Nullable UUID weekTaskId) {}
