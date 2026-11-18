package com.toefly.assessment.domain.evaluation;

/** Level 0–5 of one rubric criterion with a one-sentence note (docs/06-ai-agent.md §5.4.3). */
public record CriterionScore(String criterion, int level, String note) {

    public static final java.util.List<String> WRITING = java.util.List.of(
            "taskAchievement", "development", "organization", "languageRange", "accuracy", "conventions");

    public CriterionScore {
        if (level < 0 || level > 5) {
            throw new IllegalArgumentException("criterion level is 0..5: " + level);
        }
    }
}
