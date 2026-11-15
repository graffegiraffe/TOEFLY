package com.toefly.aigateway.domain.safety;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Mapping {@code placeholder ↔ original} of one request or one chat conversation (§3.2). One person keeps one
 * placeholder across turns. The mapping lives only in memory or, encrypted, in Valkey; never in the journal.
 */
public final class MaskingSession {

    public static final Pattern PLACEHOLDER =
            Pattern.compile("\\[(EMAIL|PHONE|URL|IBAN|CARD|DOC_ID|IP|HANDLE|DATE|PERSON|PLACE|ORG)_(\\d{1,4})]");

    /** One mapping, as stored between turns. */
    public record Entry(String placeholder, String original, String key) {}

    private final Map<String, String> byKey = new LinkedHashMap<>();
    private final Map<String, String> originals = new LinkedHashMap<>();
    private final Map<PiiType, Integer> counters = new EnumMap<>(PiiType.class);
    private int anomalies;

    public static MaskingSession restore(List<Entry> entries) {
        MaskingSession session = new MaskingSession();
        for (Entry entry : entries) {
            session.byKey.put(entry.key(), entry.placeholder());
            session.originals.put(entry.placeholder(), entry.original());
            Matcher m = PLACEHOLDER.matcher(entry.placeholder());
            if (m.matches()) {
                PiiType type = PiiType.valueOf(m.group(1));
                session.counters.merge(type, Integer.parseInt(m.group(2)), Math::max);
            }
        }
        return session;
    }

    public String placeholder(PiiType type, String original, @Nullable String group) {
        String key = type + "|"
                + (group != null ? group : original.toLowerCase(Locale.ROOT).strip());
        return byKey.computeIfAbsent(key, k -> {
            int n = counters.merge(type, 1, Integer::sum);
            String placeholder = "[" + type + "_" + n + "]";
            originals.put(placeholder, original);
            return placeholder;
        });
    }

    /** Puts the originals back; a placeholder the model made up stays as is and is counted as an anomaly. */
    public String unmask(String text) {
        if (originals.isEmpty() && !text.contains("[")) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String original = originals.get(matcher.group());
            if (original == null) {
                anomalies++;
                original = matcher.group();
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(original));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        byKey.forEach((key, placeholder) -> entries.add(new Entry(placeholder, originals.get(placeholder), key)));
        return entries;
    }

    public List<String> originals() {
        return List.copyOf(originals.values());
    }

    public boolean isEmpty() {
        return byKey.isEmpty();
    }

    public int anomalies() {
        return anomalies;
    }
}
