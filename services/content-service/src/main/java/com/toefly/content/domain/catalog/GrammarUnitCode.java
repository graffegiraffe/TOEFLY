package com.toefly.content.domain.catalog;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The code of a grammar unit in the mistake taxonomy (docs/06-ai-agent.md §6.3), such as {@code ROUNDUP5.U10} for unit
 * 10 of New Round-Up 5 or {@code COLLINS_GR.U07} for unit 7 of Collins Grammar.
 */
public record GrammarUnitCode(String framework, int unitNo) {

    private static final Map<String, String> FRAMEWORK_BY_PREFIX =
            Map.of("ROUNDUP5", "ROUND_UP_5", "COLLINS_GR", "COLLINS_GRAMMAR");
    private static final Map<String, String> PREFIX_BY_FRAMEWORK =
            Map.of("ROUND_UP_5", "ROUNDUP5", "COLLINS_GRAMMAR", "COLLINS_GR");
    private static final Pattern CODE = Pattern.compile("([A-Z0-9_]+)\\.U(\\d{1,3})");

    public GrammarUnitCode {
        if (!PREFIX_BY_FRAMEWORK.containsKey(framework)) {
            throw new IllegalArgumentException("not a grammar framework: " + framework);
        }
        if (unitNo < 1) {
            throw new IllegalArgumentException("unit numbers start at 1: " + unitNo);
        }
    }

    /** The unit of a taxonomy code; empty for vocabulary units and anything else. */
    public static Optional<GrammarUnitCode> parse(String code) {
        Matcher m = CODE.matcher(code.strip().toUpperCase(java.util.Locale.ROOT));
        if (!m.matches() || !FRAMEWORK_BY_PREFIX.containsKey(m.group(1))) {
            return Optional.empty();
        }
        return Optional.of(new GrammarUnitCode(FRAMEWORK_BY_PREFIX.get(m.group(1)), Integer.parseInt(m.group(2))));
    }

    /** {@code ROUNDUP5.U01}. */
    public String code() {
        return PREFIX_BY_FRAMEWORK.get(framework) + ".U" + (unitNo < 10 ? "0" : "") + unitNo;
    }
}
