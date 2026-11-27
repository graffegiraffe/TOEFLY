package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.learner.LearnerModelView;
import com.toefly.planning.domain.learner.MistakeStat;
import com.toefly.planning.domain.week.TaskType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * What the next week works on: the focus of the weekly review (or, before a review, the top mistakes of the learner
 * model) and the grammar topics that need AI practice (AC-14.05.2).
 *
 * @param points focus subcategories, the most important first
 * @param grammarPractice grammar topics met at least 3 times in the last 7 days, at most {@link #MAX_PRACTICE}
 */
public record WeekFocus(List<FocusPoint> points, List<MistakeStat> grammarPractice) {

    public static final int MAX_POINTS = 3;
    public static final int MAX_PRACTICE = 2;

    /** One focus subcategory with the short reason shown to the learner (may be empty). */
    public record FocusPoint(String subcategory, String why) {

        /** {@code "articles"} for {@code GRAMMAR.ARTICLES}. */
        public String label() {
            String name = subcategory.contains(".") ? subcategory.substring(subcategory.indexOf('.') + 1) : subcategory;
            return name.replace('_', ' ').toLowerCase(Locale.ROOT);
        }
    }

    public WeekFocus {
        points = List.copyOf(points.subList(0, Math.min(MAX_POINTS, points.size())));
        grammarPractice = List.copyOf(grammarPractice.subList(0, Math.min(MAX_PRACTICE, grammarPractice.size())));
    }

    public static WeekFocus none() {
        return new WeekFocus(List.of(), List.of());
    }

    /** The focus of the review when it has one, else the active mistakes of the learner model. */
    public static WeekFocus of(List<FocusPoint> reviewFocus, LearnerModelView model) {
        List<FocusPoint> points = new ArrayList<>(reviewFocus);
        if (points.isEmpty()) {
            for (MistakeStat stat : model.active()) {
                points.add(new FocusPoint(stat.subcategory(), ""));
            }
        }
        return new WeekFocus(points, model.grammarPractice());
    }

    public boolean empty() {
        return points.isEmpty() && grammarPractice.isEmpty();
    }

    /** Focus subcategories, for {@code Week.focus}. */
    public List<String> codes() {
        Set<String> codes = new LinkedHashSet<>();
        points.forEach(point -> codes.add(point.subcategory()));
        grammarPractice.forEach(stat -> codes.add(stat.subcategory()));
        return List.copyOf(codes);
    }

    /** Exam task types that practise the focus, in the order of the focus, limited to {@code available}. */
    public List<TaskType> types(Set<TaskType> available) {
        Set<TaskType> types = new LinkedHashSet<>();
        for (FocusPoint point : points) {
            for (TaskType type : typesOf(point.subcategory())) {
                if (available.contains(type)) {
                    types.add(type);
                }
            }
        }
        return List.copyOf(types);
    }

    /** The first focus point that a task type practises. */
    public Optional<FocusPoint> pointFor(TaskType type) {
        return points.stream()
                .filter(point -> typesOf(point.subcategory()).contains(type))
                .findFirst();
    }

    /** Subcategories of the focus that a task type practises. */
    public List<String> skillsOf(TaskType type) {
        return points.stream()
                .filter(point -> typesOf(point.subcategory()).contains(type))
                .map(FocusPoint::subcategory)
                .toList();
    }

    /**
     * Exam task types where a mistake subcategory shows up (docs/06-ai-agent.md §6.3): grammar and writing in the
     * Writing tasks, vocabulary in Complete the Words, comprehension in Reading and Listening, pronunciation in
     * Speaking. Time management has no task type of its own.
     */
    static List<TaskType> typesOf(String subcategory) {
        String code = subcategory.toUpperCase(Locale.ROOT);
        String category = code.contains(".") ? code.substring(0, code.indexOf('.')) : code;
        return switch (category) {
            case "GRAMMAR" ->
                List.of(TaskType.BUILD_A_SENTENCE, TaskType.WRITE_EMAIL, TaskType.WRITE_ACADEMIC_DISCUSSION);
            case "VOCABULARY" ->
                code.endsWith("IN_CONTEXT")
                        ? List.of(TaskType.COMPLETE_THE_WORDS, TaskType.READ_ACADEMIC_PASSAGE)
                        : List.of(TaskType.COMPLETE_THE_WORDS, TaskType.WRITE_ACADEMIC_DISCUSSION);
            case "SPELLING" -> List.of(TaskType.COMPLETE_THE_WORDS, TaskType.WRITE_EMAIL);
            case "COHERENCE", "TASK_RESPONSE" -> List.of(TaskType.WRITE_ACADEMIC_DISCUSSION, TaskType.WRITE_EMAIL);
            case "COMPREHENSION" ->
                code.contains("LISTEN")
                        ? List.of(TaskType.LISTEN_ACADEMIC_TALK, TaskType.LISTEN_CONVERSATION)
                        : List.of(TaskType.READ_ACADEMIC_PASSAGE, TaskType.READ_DAILY_LIFE);
            case "PRONUNCIATION", "FLUENCY" -> List.of(TaskType.LISTEN_AND_REPEAT, TaskType.TAKE_AN_INTERVIEW);
            default -> List.of();
        };
    }
}
