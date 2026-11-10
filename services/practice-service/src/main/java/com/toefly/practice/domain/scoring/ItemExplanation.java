package com.toefly.practice.domain.scoring;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Static explanation of one item from the content: why the key is right and, for each option, why it is wrong
 * (docs/04-architecture.md §2.3, AC-11.01.1). {@code null} parts are missing in the source.
 */
public record ItemExplanation(@Nullable String summary, Map<String, String> rationales) {

    public ItemExplanation {
        rationales = Map.copyOf(rationales);
    }
}
