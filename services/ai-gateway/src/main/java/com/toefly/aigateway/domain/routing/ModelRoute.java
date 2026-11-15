package com.toefly.aigateway.domain.routing;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Ordered candidates of one use case (and pass of a grader): primary, another Groq model, local reserve. */
public record ModelRoute(AiUseCase useCase, @Nullable String passTag, List<ModelRef> candidates, RouteParams params) {

    public ModelRoute {
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("route " + useCase + " has no candidates");
        }
    }

    /**
     * Candidates in the order to try: an override goes first, and a model that must not answer (the generator of
     * an item for its solver, §2.4) is left out.
     */
    public List<ModelRef> order(@Nullable ModelRef override, @Nullable String excludedModel) {
        java.util.ArrayList<ModelRef> ordered = new java.util.ArrayList<>();
        if (override != null) {
            ordered.add(override);
        }
        for (ModelRef candidate : candidates) {
            if (!candidate.equals(override) && !candidate.model().equals(excludedModel)) {
                ordered.add(candidate);
            }
        }
        return List.copyOf(ordered);
    }
}
