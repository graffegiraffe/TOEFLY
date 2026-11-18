package com.toefly.assessment.evals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controlled distortions of an eval response (docs/06-ai-agent.md §7.1): each must not score higher than the
 * original. Deterministic for a case id, so a report can be reproduced.
 */
public final class Distortions {

    private static final Pattern WORD = Pattern.compile("\\p{L}{4,}");

    private Distortions() {}

    /** One distortion per case, the kind rotating by case id, to stay inside the free daily quotas of Groq. */
    public static List<EvalCase> of(EvalCase original) {
        List<EvalCase> all = new ArrayList<>();
        all.add(copy(original, "TRUNCATED", truncate(original.text())));
        all.add(copy(original, "TYPOS", typos(original.text(), new Random(original.id().hashCode()))));
        all.add(copy(original, "RUN_ON", runOn(original.text())));
        return List.of(all.get(Math.floorMod(original.id().hashCode(), all.size())));
    }

    static String truncate(String text) {
        String[] words = text.split("\\s+");
        int keep = Math.max(1, words.length * 2 / 5);
        return String.join(" ", java.util.Arrays.copyOf(words, keep));
    }

    static String typos(String text, Random random) {
        Matcher matcher = WORD.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String word = matcher.group();
            if (random.nextInt(100) < 20) {
                int i = 1 + random.nextInt(word.length() - 2);
                word = word.substring(0, i) + word.charAt(i + 1) + word.charAt(i) + word.substring(i + 2);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(word));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static String runOn(String text) {
        return text.replaceAll("[.;:!?]", "").replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private static EvalCase copy(EvalCase original, String kind, String text) {
        return new EvalCase(
                original.id() + "-" + kind.toLowerCase(java.util.Locale.ROOT),
                original.taskType(),
                original.topic(),
                original.taskPrompt(),
                original.version(),
                text,
                List.of(),
                Map.of(),
                "distortion",
                original.id(),
                kind);
    }
}
