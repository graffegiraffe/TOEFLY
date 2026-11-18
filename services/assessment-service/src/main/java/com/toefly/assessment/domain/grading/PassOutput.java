package com.toefly.assessment.domain.grading;

import com.toefly.assessment.domain.evaluation.CriterionScore;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** One pass of WritingGrader: output of {@code WritingEvaluationOutput} plus the stamp of ai-gateway. */
public record PassOutput(
        String passTag,
        int score,
        List<CriterionScore> criteria,
        List<BulletCoverage> bulletCoverage,
        List<RawComment> comments,
        List<String> strengths,
        List<String> priorities,
        List<String> integrityFlags,
        @Nullable String rationale,
        String promptKey,
        int promptVersion,
        String provider,
        String model,
        boolean degraded) {

    public PassOutput {
        if (score < 0 || score > 5) {
            throw new IllegalArgumentException("pass score is 0..5: " + score);
        }
        criteria = List.copyOf(criteria);
        bulletCoverage = List.copyOf(bulletCoverage);
        comments = List.copyOf(comments);
        strengths = List.copyOf(strengths);
        priorities = List.copyOf(priorities);
        integrityFlags = List.copyOf(integrityFlags);
    }
}
