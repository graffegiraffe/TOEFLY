package com.toefly.assessment.application.service;

import com.toefly.assessment.domain.evaluation.CriterionScore;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.evaluation.EvaluationAgreement;
import com.toefly.assessment.domain.evaluation.EvaluatorType;
import com.toefly.assessment.domain.evaluation.InlineAnnotation;
import com.toefly.assessment.domain.evaluation.PassRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What WritingGrader decided for a version, before it becomes an {@link Evaluation}. */
record GradingResult(
        double finalScore,
        @Nullable EvaluationAgreement agreement,
        boolean needsHumanReview,
        List<CriterionScore> criteria,
        List<String> strengths,
        List<String> priorities,
        List<InlineAnnotation> annotations,
        List<PassRecord> passes,
        List<String> capsApplied,
        List<String> integrityFlags,
        @Nullable String promptKey,
        int promptVersion,
        @Nullable String modelId,
        boolean degraded) {

    Evaluation toEvaluation(UUID submissionId, int versionNo, String rubricId, Instant now) {
        return new Evaluation(
                UUID.randomUUID(),
                submissionId,
                versionNo,
                EvaluatorType.AI,
                null,
                rubricId,
                finalScore,
                criteria,
                strengths,
                priorities,
                annotations,
                passes,
                agreement,
                needsHumanReview,
                capsApplied,
                integrityFlags,
                promptKey,
                promptVersion,
                modelId,
                degraded,
                now);
    }
}
