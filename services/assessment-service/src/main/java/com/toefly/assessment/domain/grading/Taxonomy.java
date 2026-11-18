package com.toefly.assessment.domain.grading;

import com.toefly.assessment.domain.evaluation.MistakeCategory;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Subcategories {@code <CATEGORY>.<NAME>} and the book units they lead to (docs/06-ai-agent.md §6.3). An unknown
 * subcategory falls back to {@code <CATEGORY>.OTHER}.
 */
public record Taxonomy(Map<String, Entry> entries) {

    public record Entry(
            String code,
            String description,
            @Nullable String unitCode,
            @Nullable String unitTitle) {}

    public Taxonomy {
        entries = Map.copyOf(entries);
    }

    public String normalize(MistakeCategory category, String subcategory) {
        String code = subcategory.contains(".") ? subcategory : category.name() + "." + subcategory;
        return entries.containsKey(code) && code.startsWith(category.name() + ".") ? code : category.name() + ".OTHER";
    }

    public @Nullable Entry entry(String code) {
        return entries.get(code);
    }
}
