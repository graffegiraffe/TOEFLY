package com.toefly.tutoragent.domain.learner;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Subcategories {@code <CATEGORY>.<NAME>} and the textbook units they lead to (docs/06 §6.3). An unknown subcategory
 * falls back to {@code <CATEGORY>.OTHER}.
 */
public record Taxonomy(Map<String, Entry> entries) {

    public record Entry(
            String code,
            String title,
            String titleRu,
            @Nullable String unitCode,
            @Nullable String unitTitle) {}

    public Taxonomy {
        entries = Map.copyOf(entries);
    }

    /** A known code, or {@code <CATEGORY>.OTHER}; throws for an unknown category. */
    public String normalize(String subcategory) {
        MistakeCategory category = MistakeCategory.ofSubcategory(subcategory);
        return entries.containsKey(subcategory) ? subcategory : category.name() + ".OTHER";
    }

    public @Nullable Entry entry(String code) {
        return entries.get(code);
    }

    public List<String> unitRefs(String subcategory) {
        Entry entry = entries.get(subcategory);
        return entry == null || entry.unitCode() == null ? List.of() : List.of(entry.unitCode());
    }

    public String title(String subcategory, ExplanationLanguage language) {
        Entry entry = entries.get(subcategory);
        if (entry == null) {
            return subcategory;
        }
        return language == ExplanationLanguage.RU ? entry.titleRu() : entry.title();
    }

    /** Title of a textbook unit, e.g. "Round-Up Unit 10: Nouns – Articles"; the code itself when unknown. */
    public String unitTitle(String unitCode) {
        return entries.values().stream()
                .filter(e -> unitCode.equals(e.unitCode()) && e.unitTitle() != null)
                .map(Entry::unitTitle)
                .findFirst()
                .orElse(unitCode);
    }
}
