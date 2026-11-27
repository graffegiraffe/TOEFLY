package com.toefly.content.domain.catalog;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Subcategories of the mistake taxonomy with their grammar units (docs/06-ai-agent.md §6.3), the same table that
 * assessment-service links its comments with: {@code GRAMMAR.ARTICLES} → {@code ROUNDUP5.U10}.
 */
public final class MistakeTaxonomy {

    private final Map<String, GrammarUnitCode> units;

    /** {@code unitCodes} maps a subcategory code to a unit code; vocabulary units and nulls are left out. */
    public MistakeTaxonomy(Map<String, String> unitCodes) {
        Map<String, GrammarUnitCode> parsed = new HashMap<>();
        unitCodes.forEach((subcategory, code) -> {
            if (code != null) {
                GrammarUnitCode.parse(code).ifPresent(unit -> parsed.put(normalize(subcategory), unit));
            }
        });
        this.units = Map.copyOf(parsed);
    }

    /** The grammar unit of a subcategory such as {@code GRAMMAR.ARTICLES}, case-insensitive. */
    public Optional<GrammarUnitCode> unitOf(String subcategory) {
        return Optional.ofNullable(units.get(normalize(subcategory)));
    }

    public boolean isSubcategory(String text) {
        return units.containsKey(normalize(text));
    }

    private static String normalize(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }
}
