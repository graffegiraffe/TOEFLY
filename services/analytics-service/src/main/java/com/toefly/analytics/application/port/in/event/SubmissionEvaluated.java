package com.toefly.analytics.application.port.in.event;

import com.toefly.analytics.domain.common.Section;
import com.toefly.analytics.domain.common.TaskType;
import java.time.Instant;
import java.util.UUID;

/** {@code toefly.assessment.submission-evaluated.v1}: a Writing or Speaking work with its rubric score 0–5. */
public record SubmissionEvaluated(
        UUID userId, UUID submissionId, TaskType taskType, Section section, int rubricScore, Instant at)
        implements AnalyticsEvent {}
