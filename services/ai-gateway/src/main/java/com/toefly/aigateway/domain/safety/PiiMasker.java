package com.toefly.aigateway.domain.safety;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies the spans of all stages to a text (§3.2): overlapping spans resolve to the longer one, then the earlier
 * stage; names of the task itself ({@code allowList}) stay.
 */
public final class PiiMasker {

    private PiiMasker() {}

    public static String mask(String text, List<PiiSpan> spans, MaskingSession session, Set<String> allowList) {
        Set<String> allowed =
                allowList.stream().map(a -> a.strip().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<PiiSpan> chosen = resolve(spans.stream()
                .filter(span -> span.end() <= text.length())
                .filter(span -> !allowed.contains(
                        text.substring(span.start(), span.end()).strip().toLowerCase(Locale.ROOT)))
                .toList());
        chosen.sort(Comparator.comparingInt(PiiSpan::start));
        StringBuilder out = new StringBuilder(text.length());
        int at = 0;
        for (PiiSpan span : chosen) {
            out.append(text, at, span.start());
            out.append(session.placeholder(span.type(), text.substring(span.start(), span.end()), span.group()));
            at = span.end();
        }
        out.append(text, at, text.length());
        return out.toString();
    }

    static List<PiiSpan> resolve(List<PiiSpan> spans) {
        List<PiiSpan> ordered = new ArrayList<>(spans);
        ordered.sort(Comparator.comparingInt(PiiSpan::length)
                .reversed()
                .thenComparing(PiiSpan::stage)
                .thenComparingInt(PiiSpan::start));
        List<PiiSpan> chosen = new ArrayList<>();
        for (PiiSpan span : ordered) {
            if (chosen.stream().noneMatch(span::overlaps)) {
                chosen.add(span);
            }
        }
        return chosen;
    }
}
