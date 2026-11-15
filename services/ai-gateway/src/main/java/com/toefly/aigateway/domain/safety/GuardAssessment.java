package com.toefly.aigateway.domain.safety;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** What heuristics and the guard model said about one text. {@code model} is null when only heuristics ran. */
public record GuardAssessment(
        GuardVerdict verdict,
        List<String> categories,
        List<HeuristicHit> heuristicHits,
        @Nullable String model,
        long latencyMs,
        boolean modelAvailable) {

    public static final String INJECTION_CATEGORY = "S15";
    public static final String DISHONESTY_CATEGORY = "S16";
    public static final String SELF_HARM_CATEGORY = "S11";

    public GuardAssessment {
        categories = List.copyOf(categories);
        heuristicHits = List.copyOf(heuristicHits);
    }

    public boolean allowed() {
        return verdict == GuardVerdict.SAFE;
    }

    public boolean selfHarm() {
        return categories.contains(SELF_HARM_CATEGORY);
    }

    /** Heuristics first: a strong hit is an injection whatever the model says (§3.5). */
    public static GuardAssessment combine(
            List<HeuristicHit> hits, @Nullable GuardModelVerdict model, long latencyMs, boolean modelAvailable) {
        Set<String> categories = new LinkedHashSet<>();
        GuardVerdict verdict = GuardVerdict.SAFE;
        if (model != null) {
            verdict = model.verdict();
            categories.addAll(model.categories());
        }
        if (hits.stream().anyMatch(HeuristicHit::strong)) {
            verdict = GuardVerdict.INJECTION;
            categories.add(INJECTION_CATEGORY);
        }
        return new GuardAssessment(
                verdict,
                new ArrayList<>(categories),
                hits,
                model == null ? null : model.model(),
                latencyMs,
                modelAvailable);
    }

    /** Integrity flags a grader stores with the result (§3.3). */
    public List<String> integrityFlags() {
        List<String> flags = new ArrayList<>();
        if (!modelAvailable) {
            flags.add("GUARD_UNAVAILABLE");
        }
        if (verdict == GuardVerdict.INJECTION) {
            flags.add("INSTRUCTION_IN_RESPONSE");
        }
        if (selfHarm()) {
            flags.add("SELF_HARM");
        }
        if (verdict == GuardVerdict.UNSAFE && !selfHarm()) {
            flags.add("UNSAFE_CONTENT");
        }
        return flags;
    }
}
