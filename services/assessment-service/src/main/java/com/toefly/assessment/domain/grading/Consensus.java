package com.toefly.assessment.domain.grading;

import com.toefly.assessment.domain.evaluation.EvaluationAgreement;
import org.jspecify.annotations.Nullable;

/**
 * Agreement of the passes and the final score (docs/06-ai-agent.md §5.4.5). Pass C resolves a split when it is
 * within one point of A or B; otherwise the median stands and the evaluation is flagged for a human.
 */
public record Consensus(double finalScore, EvaluationAgreement agreement, boolean needsHumanReview) {

    /** Pass C runs when A and B differ by more than one point. */
    public static boolean needsThirdPass(int a, int b) {
        return Math.abs(a - b) > 1;
    }

    public static Consensus of(int a, int b, @Nullable Integer c) {
        if (c == null) {
            if (needsThirdPass(a, b)) {
                throw new IllegalArgumentException("A and B differ by more than 1: pass C is required");
            }
            return new Consensus(Scores.roundHalf((a + b) / 2.0), EvaluationAgreement.AGREED, false);
        }
        double median = Scores.median(a, b, c);
        boolean confirmsOne = Math.min(Math.abs(c - a), Math.abs(c - b)) <= 1;
        return confirmsOne
                ? new Consensus(median, EvaluationAgreement.RESOLVED_BY_THIRD_PASS, false)
                : new Consensus(median, EvaluationAgreement.FLAGGED, true);
    }

    /** With one pass only (the other failed for good), the score stands and a human should look at it. */
    public static Consensus single(int score) {
        return new Consensus(score, EvaluationAgreement.FLAGGED, true);
    }
}
