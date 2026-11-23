package com.toefly.tutoragent.domain.learner;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A chance to make a mistake (docs/06 §6.2). A closed item gives one to the subcategory of its skill; an evaluated
 * piece of writing gives one to every subcategory of a category, so {@code code} is a subcategory or a category.
 */
public record Opportunity(
        String code, UUID sourceId, @Nullable UUID itemId, Instant occurredAt) {

    public boolean categoryWide() {
        return code.indexOf('.') < 0;
    }

    /** True if this opportunity counts for the subcategory. */
    public boolean covers(String subcategory) {
        return categoryWide()
                ? MistakeCategory.ofSubcategory(subcategory).name().equals(code)
                : code.equals(subcategory);
    }
}
