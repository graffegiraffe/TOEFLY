package com.toefly.assessment.domain.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Score of one version by AI or by a tutor (docs/03-domain-model.md §5.1, docs/06-ai-agent.md §5.4.5). The final
 * score has steps of 0.5; {@code overallScore} is its round half up for the event and older clients.
 */
public record Evaluation(
        UUID id,
        UUID submissionId,
        int versionNo,
        EvaluatorType evaluator,
        @Nullable UUID tutorId,
        String rubricId,
        double finalScore,
        List<CriterionScore> criteria,
        List<String> strengths,
        List<String> priorities,
        List<InlineAnnotation> annotations,
        List<PassRecord> passes,
        @Nullable EvaluationAgreement agreement,
        boolean needsHumanReview,
        List<String> capsApplied,
        List<String> integrityFlags,
        @Nullable String promptKey,
        int promptVersion,
        @Nullable String modelId,
        boolean degraded,
        Instant createdAt) {

    public Evaluation {
        if (finalScore < 0 || finalScore > 5 || finalScore * 2 != Math.rint(finalScore * 2)) {
            throw new IllegalArgumentException("final score is 0..5 in steps of 0.5: " + finalScore);
        }
        criteria = List.copyOf(criteria);
        strengths = List.copyOf(strengths);
        priorities = List.copyOf(priorities);
        annotations = List.copyOf(annotations);
        passes = List.copyOf(passes);
        capsApplied = List.copyOf(capsApplied);
        integrityFlags = List.copyOf(integrityFlags);
    }

    public int overallScore() {
        return (int) Math.floor(finalScore + 0.5);
    }

    public Evaluation withAnnotations(List<InlineAnnotation> next) {
        return new Evaluation(
                id,
                submissionId,
                versionNo,
                evaluator,
                tutorId,
                rubricId,
                finalScore,
                criteria,
                strengths,
                priorities,
                next,
                passes,
                agreement,
                needsHumanReview,
                capsApplied,
                integrityFlags,
                promptKey,
                promptVersion,
                modelId,
                degraded,
                createdAt);
    }

    /** Effective score of a version: the tutor's if there is one, else the AI's (INV-SUB-10). */
    public static @Nullable Evaluation effective(List<Evaluation> ofVersion) {
        Evaluation ai = null;
        for (Evaluation evaluation : ofVersion) {
            if (evaluation.evaluator() == EvaluatorType.TUTOR) {
                return evaluation;
            }
            ai = evaluation;
        }
        return ai;
    }
}
