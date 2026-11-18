package com.toefly.assessment.evals;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** What the grader did with one case; the report keeps it without the text. */
public record CaseResult(
        String caseId,
        String split,
        @Nullable Double gold,
        double predicted,
        List<Integer> passes,
        List<String> flags,
        @Nullable String distortionOf,
        int tutorErrors,
        int tutorErrorsFound,
        int comments,
        int commentsAnchored) {}
