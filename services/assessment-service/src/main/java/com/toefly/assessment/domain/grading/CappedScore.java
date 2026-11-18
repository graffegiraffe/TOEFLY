package com.toefly.assessment.domain.grading;

import com.toefly.assessment.domain.submission.TaskType;
import java.util.ArrayList;
import java.util.List;

/**
 * Final score after the deterministic caps of docs/06-ai-agent.md §5.4.1; each applied cap is named so the review
 * can explain it.
 */
public record CappedScore(double score, List<String> capsApplied) {

    public static final String EMPTY = "EMPTY_RESPONSE";
    public static final String COPIED = "COPIED_FROM_PROMPT";
    public static final String NOT_ENGLISH = "NOT_ENGLISH";
    public static final String SHORT_50 = "UNDER_50_WORDS";
    public static final String SHORT_80 = "UNDER_80_WORDS";
    public static final String BULLETS = "EMAIL_BULLETS_MISSING";

    public CappedScore {
        capsApplied = List.copyOf(capsApplied);
    }

    public static CappedScore apply(TaskType type, double score, TextFacts facts, List<BulletCoverage> bullets) {
        List<String> caps = new ArrayList<>();
        double result = score;
        if (facts.wordCount() == 0) {
            return new CappedScore(0, List.of(EMPTY));
        }
        if (facts.copiedShare() >= 0.70) {
            return new CappedScore(0, List.of(COPIED));
        }
        if (type == TaskType.WRITE_ACADEMIC_DISCUSSION) {
            if (facts.wordCount() < 50 && result > 2) {
                result = 2;
                caps.add(SHORT_50);
            } else if (facts.wordCount() >= 50 && facts.wordCount() < 80 && result > 3) {
                result = 3;
                caps.add(SHORT_80);
            }
        }
        if (type == TaskType.WRITE_EMAIL && !bullets.isEmpty()) {
            long attempted = bullets.stream().filter(BulletCoverage::attempted).count();
            if (attempted < 2 && result > 2) {
                result = 2;
                caps.add(BULLETS);
            }
        }
        if (facts.nonEnglishShare() > 0.30 && result > 1) {
            result = 1;
            caps.add(NOT_ENGLISH);
        }
        return new CappedScore(result, caps);
    }
}
