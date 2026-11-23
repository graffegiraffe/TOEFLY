package com.toefly.planning.domain.week;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Where the tasks of a week came from.
 *
 * @param reviewId the weekly review whose focus the week follows; null for a week built by the rules
 * @param focus mistake subcategories the week works on, for example {@code GRAMMAR.ARTICLES}
 * @param stamp the AI stamp of WeekGenerator; null without a valid AI draft
 */
public record DraftInfo(
        DraftSource source,
        @Nullable UUID reviewId,
        List<String> focus,
        @Nullable AiStamp stamp) {

    public DraftInfo {
        focus = List.copyOf(focus);
    }

    public static DraftInfo rules(List<String> focus) {
        return new DraftInfo(DraftSource.RULES, null, focus, null);
    }

    /** The AI was asked and did not give a valid draft (docs/06 §5.2, {@code aiFallback}). */
    public boolean aiFallback() {
        return source == DraftSource.AI_FALLBACK;
    }
}
