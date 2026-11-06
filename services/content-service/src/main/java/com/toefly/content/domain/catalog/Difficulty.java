package com.toefly.content.domain.catalog;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Initial item difficulty without learner data, basis {@code SEED_LABEL} (docs/03-domain-model.md §7.5). */
public final class Difficulty {

    public static final String SEED_LABEL = "SEED_LABEL";

    private static final Map<String, Double> B_CEFR =
            Map.of("A1", -2.5, "A2", -1.5, "B1", -0.5, "B2", 0.5, "C1", 1.5, "C2", 2.5);

    private Difficulty() {}

    /** Difficulty of one item in logits, clamped to [-4, 4]; legacy sets and unlabelled sets sit at 0. */
    public static double initialB(@Nullable String cefr, @Nullable String subskill, boolean legacy) {
        if (legacy || cefr == null || !B_CEFR.containsKey(cefr)) {
            return 0;
        }
        return Math.max(-4, Math.min(4, B_CEFR.get(cefr) + subskillShift(subskill)));
    }

    private static double subskillShift(@Nullable String subskill) {
        if (subskill == null) {
            return 0;
        }
        return switch (subskill) {
            case "INFERENCE", "INSERT_SENTENCE" -> 0.2;
            case "RHETORICAL_PURPOSE" -> 0.15;
            case "DETAIL", "MAIN_IDEA" -> -0.1;
            default -> 0;
        };
    }
}
