package com.toefly.aigateway.domain.routing;

import java.util.EnumSet;
import java.util.Set;

/** What an AI call is for; picks the route of models (docs/06-ai-agent.md §2.3, canon §5.4). */
public enum AiUseCase {
    TUTOR_CHAT,
    TUTOR_ROUTER,
    TUTOR_ESCALATION,
    HINT,
    WRITING_GRADING,
    SPEAKING_CONTENT_GRADING,
    REPEAT_MEANING_CHECK,
    MISTAKE_EXPLANATION,
    TRANSLATION_RU,
    PLAN_GENERATION,
    WEEK_GENERATION,
    WEEKLY_REVIEW,
    REENGAGEMENT,
    ITEM_GENERATION,
    ITEM_SOLVING,
    ITEM_VALIDATION,
    INTERVIEW_SCRIPT,
    MATERIAL_EXTRACTION,
    EVAL_JUDGE,
    GUARD,
    EMBEDDING,
    TRANSCRIPTION,
    PRONUNCIATION,
    TTS;

    private static final Set<AiUseCase> GRADING =
            EnumSet.of(WRITING_GRADING, SPEAKING_CONTENT_GRADING, REPEAT_MEANING_CHECK);

    /** A reserve model grading a learner marks the result degraded (§2.5.3). */
    public boolean grades() {
        return GRADING.contains(this);
    }
}
