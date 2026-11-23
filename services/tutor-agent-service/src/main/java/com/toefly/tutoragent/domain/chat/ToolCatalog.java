package com.toefly.tutoragent.domain.chat;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tools of an intent intersected with the whitelist of the screen, at most 6 per turn (docs/06 §4.5). Tools of
 * vocabulary-service and classroom-service are not registered until phases 8 and 10 (docs/12, phase 5c, decision 2).
 */
public final class ToolCatalog {

    public static final int MAX_TOOLS = 6;

    static final List<String> ALL = List.of(
            "getProgress",
            "getCurrentWeek",
            "getMistakeHistory",
            "searchItems",
            "assignExtraTask",
            "explainGrammarRule",
            "getSubmission",
            "getAttemptReview",
            "getBandEstimate",
            "getTimeStats",
            "getWeeklyReview");

    private static final Map<TutorIntent, List<String>> BY_INTENT = new EnumMap<>(TutorIntent.class);

    static {
        BY_INTENT.put(
                TutorIntent.EXPLAIN_MISTAKE,
                List.of("getAttemptReview", "getSubmission", "explainGrammarRule", "getMistakeHistory"));
        BY_INTENT.put(
                TutorIntent.WRITING_FEEDBACK, List.of("getSubmission", "explainGrammarRule", "getMistakeHistory"));
        BY_INTENT.put(TutorIntent.SPEAKING_FEEDBACK, List.of("getSubmission", "getMistakeHistory"));
        BY_INTENT.put(TutorIntent.GRAMMAR_QUESTION, List.of("explainGrammarRule", "searchItems", "assignExtraTask"));
        BY_INTENT.put(TutorIntent.VOCABULARY_QUESTION, List.of("searchItems"));
        BY_INTENT.put(TutorIntent.PLAN_QUESTION, List.of("getCurrentWeek", "getProgress", "assignExtraTask"));
        BY_INTENT.put(
                TutorIntent.PROGRESS_QUESTION,
                List.of("getProgress", "getBandEstimate", "getMistakeHistory", "getTimeStats", "getWeeklyReview"));
        BY_INTENT.put(TutorIntent.STRATEGY_QUESTION, List.of("getProgress", "searchItems", "getMistakeHistory"));
        BY_INTENT.put(TutorIntent.MOTIVATION, List.of("getProgress", "getCurrentWeek"));
        BY_INTENT.put(TutorIntent.PRACTICE_REQUEST, List.of("searchItems", "assignExtraTask", "getMistakeHistory"));
        BY_INTENT.put(TutorIntent.SOLVE_IN_PROGRESS, List.of());
        BY_INTENT.put(TutorIntent.OFF_TOPIC, List.of());
    }

    private ToolCatalog() {}

    public static List<String> select(TutorIntent intent, ChatContextType context) {
        Set<String> chosen = new LinkedHashSet<>();
        for (String tool : BY_INTENT.get(intent)) {
            if (allowed(tool, context)) {
                chosen.add(tool);
            }
        }
        return chosen.stream().limit(MAX_TOOLS).toList();
    }

    /** The whitelist of a screen (docs/06 §4.5); unsubmitted tasks get only four harmless tools. */
    public static boolean allowed(String tool, ChatContextType context) {
        if (context.unsubmitted()) {
            return List.of("getProgress", "getCurrentWeek", "explainGrammarRule")
                    .contains(tool);
        }
        return switch (tool) {
            case "getSubmission" ->
                context == ChatContextType.SUBMISSION_REVIEW
                        || context == ChatContextType.GENERAL
                        || context == ChatContextType.WEEKLY_REVIEW;
            case "getAttemptReview" ->
                context == ChatContextType.ATTEMPT_REVIEW
                        || context == ChatContextType.GENERAL
                        || context == ChatContextType.WEEKLY_REVIEW;
            case "getWeeklyReview" ->
                context == ChatContextType.WEEKLY_REVIEW
                        || context == ChatContextType.GENERAL
                        || context == ChatContextType.WEEK;
            default -> ALL.contains(tool);
        };
    }
}
