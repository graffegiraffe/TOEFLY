package com.toefly.tutoragent.application.service;

import com.toefly.tutoragent.domain.learner.Taxonomy;
import com.toefly.tutoragent.domain.review.Emoji;
import com.toefly.tutoragent.domain.review.WeekFacts;
import com.toefly.tutoragent.domain.review.WeeklyReview;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Blocks of the {@code tutor.weekly-review} prompt and the parsing of its JSON answer (WeeklyReviewOutput). */
final class ReviewOutput {

    private ReviewOutput() {}

    static String stats(WeekFacts f) {
        StringBuilder text = new StringBuilder();
        text.append("Week ")
                .append(f.weekNo())
                .append(": ")
                .append(f.tasksDone())
                .append(" of ")
                .append(f.tasksTotal())
                .append(" tasks done (")
                .append(number(f.progressPercent()))
                .append("%).");
        for (WeekFacts.SectionLine s : f.sections()) {
            text.append("\n- ")
                    .append(s.section())
                    .append(": ")
                    .append(s.tasksDone())
                    .append(" of ")
                    .append(s.tasksTotal())
                    .append(" tasks, ")
                    .append(s.minutesDone())
                    .append(" of ")
                    .append(s.minutesPlanned())
                    .append(" planned minutes");
        }
        if (!f.bands().isEmpty()) {
            text.append("\nBand estimate (AI estimate, 1-6): ").append(bands(f.bands()));
            if (f.bandDelta() != null) {
                text.append("; overall change since last week ").append(number(f.bandDelta()));
            }
        }
        return text.toString();
    }

    static String scores(WeekFacts f) {
        if (f.scores().isEmpty()) {
            return "No scored tasks.";
        }
        return f.scores().stream()
                .map(s -> "- " + s.taskType() + ": " + s.attempts() + " attempts"
                        + (s.accuracyPercent() == null ? "" : ", accuracy " + number(s.accuracyPercent()) + "%")
                        + (s.averageScore() == null ? "" : ", average score " + number(s.averageScore()) + " of 5"))
                .collect(Collectors.joining("\n"));
    }

    static String mistakes(WeekFacts f, Taxonomy taxonomy) {
        if (f.mistakes().isEmpty()) {
            return "No mistakes recorded.";
        }
        List<String> lines = new ArrayList<>();
        for (WeekFacts.MistakeLine m : f.mistakes()) {
            StringBuilder line = new StringBuilder("- ")
                    .append(m.subcategory())
                    .append(" (")
                    .append(m.title())
                    .append("): count ")
                    .append(m.count())
                    .append("; units ")
                    .append(m.unitRefs().stream()
                            .map(u -> u + " (" + taxonomy.unitTitle(u) + ")")
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("none"));
            for (WeekFacts.Example e : m.examples()) {
                line.append("\n  example wrong: \"").append(e.wrong()).append('"');
                if (e.right() != null) {
                    line.append(" right: \"").append(e.right()).append('"');
                }
            }
            lines.add(line.toString());
        }
        return String.join("\n", lines);
    }

    static String candidates(WeekFacts f) {
        return f.focusCandidates().stream()
                .map(c -> "- " + c.subcategory() + " (" + c.title() + ")")
                .collect(Collectors.joining("\n"));
    }

    /** Text of the answer; counts and unit references always come from the facts, not from the model. */
    static WeeklyReview.Text parse(Map<String, Object> out, WeekFacts facts, Taxonomy taxonomy) {
        List<WeeklyReview.TopMistake> top = new ArrayList<>();
        for (Map<String, Object> m : maps(out.get("topMistakes"))) {
            String code = string(m.get("subcategory"));
            Map<String, Object> example = map(m.get("example"));
            List<String> units = facts.mistakes().stream()
                    .filter(line -> line.subcategory().equals(code))
                    .findFirst()
                    .map(WeekFacts.MistakeLine::unitRefs)
                    .orElse(taxonomy.unitRefs(code));
            top.add(new WeeklyReview.TopMistake(
                    code,
                    integer(m.get("count")),
                    clean(m.get("pattern"), 160),
                    example == null || string(example.get("wrong")).isBlank()
                            ? null
                            : new WeekFacts.Example(clean(example.get("wrong"), 200), clean(example.get("right"), 200)),
                    clean(m.get("advice"), 200),
                    units));
        }
        List<WeeklyReview.Focus> focus = maps(out.get("nextWeekFocus")).stream()
                .limit(3)
                .map(f -> new WeeklyReview.Focus(string(f.get("subcategory")), clean(f.get("why"), 160)))
                .toList();
        List<String> strengths = list(out.get("strengths")).stream()
                .limit(3)
                .map(s -> clean(s, 160))
                .toList();
        return new WeeklyReview.Text(
                clean(out.get("headline"), 90),
                clean(out.get("summary"), 600),
                top,
                strengths,
                focus,
                clean(out.get("motivation"), 200));
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String bands(Map<String, Double> bands) {
        return bands.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + " " + number(e.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static String clean(@Nullable Object value, int max) {
        String text = Emoji.strip(string(value)).strip();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String string(@Nullable Object value) {
        return value == null ? "" : value.toString();
    }

    private static int integer(@Nullable Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Map<String, Object> map(@Nullable Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(@Nullable Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(m -> (Map<String, Object>) m)
                .toList();
    }

    private static List<String> list(@Nullable Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
