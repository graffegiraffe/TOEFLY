package com.toefly.planning.application.service;

import com.toefly.planning.application.port.in.DraftNextWeekUseCase.ReviewMistake;
import com.toefly.planning.application.port.in.DraftNextWeekUseCase.WeeklyReview;
import com.toefly.planning.application.port.out.AiUnavailableException;
import com.toefly.planning.application.port.out.WeekDraftAiPort;
import com.toefly.planning.application.port.out.WeekDraftAiPort.DraftCall;
import com.toefly.planning.application.port.out.WeekDraftAiPort.DraftReply;
import com.toefly.planning.application.service.WeekPlanner.Prepared;
import com.toefly.planning.domain.composition.CandidatePool;
import com.toefly.planning.domain.composition.WeekCandidate;
import com.toefly.planning.domain.composition.WeekDraftValidator;
import com.toefly.planning.domain.composition.WeekFocus;
import com.toefly.planning.domain.composition.WeekFocus.FocusPoint;
import com.toefly.planning.domain.week.DraftInfo;
import com.toefly.planning.domain.week.DraftSource;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * WeekGenerator (docs/06-ai-agent.md §5.2): the model chooses and lays out refs of the candidate pool, the draft
 * passes {@link WeekDraftValidator}, a broken draft gets one retry with its violations, then the week of
 * GreedyWeekPacker is used with {@code aiFallback = true} and its template texts.
 */
@Component
class WeekGenerator {

    static final String WORKER = "WeekGenerator";
    static final int ATTEMPTS = 2;
    static final int RATIONALE_LENGTH = 1000;

    private static final Logger log = LoggerFactory.getLogger(WeekGenerator.class);

    private final WeekDraftAiPort ai;

    WeekGenerator(WeekDraftAiPort ai) {
        this.ai = ai;
    }

    /**
     * The new tasks of {@code week}.
     *
     * @param laidOut the tasks carry their days; otherwise the week distributes them
     */
    record Generated(List<WeekTask> tasks, boolean laidOut, String rationale, DraftInfo info) {}

    Generated generate(Week week, Prepared prepared, WeekFocus focus, WeeklyReview review, String fallbackRationale) {
        CandidatePool pool = prepared.pool();
        Map<String, Object> variables = variables(week, prepared, focus, review);
        List<String> violations = List.of();
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Map<String, Object> call = new LinkedHashMap<>(variables);
            call.put("previousViolations", violationsBlock(violations));
            DraftReply reply;
            try {
                reply = ai.draft(new DraftCall(
                        week.userId(), call, WORKER + ":" + week.id() + ":" + review.reviewId() + ":" + attempt));
            } catch (AiUnavailableException unavailable) {
                log.warn(
                        "WeekGenerator: ai-gateway gave no draft for week {}: {}", week.id(), unavailable.getMessage());
                break;
            }
            violations = WeekDraftValidator.validate(
                    pool, reply.layout(), week.day1(), week.minutesPerDay(), prepared.budgetMax());
            if (violations.isEmpty()) {
                List<WeekTask> tasks = WeekDraftValidator.tasks(
                        pool, reply.layout(), week.day1(), week.minutesPerDay(), prepared.budgetMax());
                String rationale = (reply.layout().weekGoal().strip() + " "
                                + reply.layout().focusExplanation().strip())
                        .strip();
                return new Generated(
                        tasks,
                        true,
                        rationale.isEmpty() ? fallbackRationale : cut(rationale),
                        new DraftInfo(DraftSource.AI, review.reviewId(), focus.codes(), reply.stamp()));
            }
            log.info(
                    "WeekGenerator: attempt {} for week {} broke {} rules: {}",
                    attempt,
                    week.id(),
                    violations.size(),
                    violations);
        }
        return new Generated(
                pool.baseline(),
                false,
                fallbackRationale,
                new DraftInfo(DraftSource.AI_FALLBACK, review.reviewId(), focus.codes(), null));
    }

    // ---- the variables of planning.week.generate ----

    static Map<String, Object> variables(Week week, Prepared prepared, WeekFocus focus, WeeklyReview review) {
        CandidatePool pool = prepared.pool();
        double[] bounds = WeekDraftValidator.dayBounds(
                week.minutesPerDay(),
                pool.baseline().stream().map(WeekTask::estimatedMinutes).toList());
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("weekNumber", week.weekNo());
        variables.put(
                "studyDays",
                IntStream.rangeClosed(1, Week.DAYS)
                        .mapToObj(
                                day -> WeekDraftValidator.dayName(week.date(day).getDayOfWeek()))
                        .collect(Collectors.joining(", ")));
        variables.put("minutesPerDay", week.minutesPerDay());
        variables.put(
                "dayBounds",
                "each day between %d and %d minutes; a task longer than %d minutes stays alone on its day"
                        .formatted(Math.round(bounds[0]), Math.round(bounds[1]), Math.round(bounds[1])));
        variables.put(
                "sectionWeights",
                pool.sectionWeights().entrySet().stream()
                        .map(entry -> String.format(Locale.ROOT, "%s %.2f", entry.getKey(), entry.getValue()))
                        .collect(Collectors.joining(", ")));
        variables.put("focus", focus(focus));
        variables.put("previousWeekReview", review(review));
        variables.put("mandatory", String.join(", ", pool.mandatoryRefs()));
        variables.put(
                "candidates",
                pool.candidates().stream().map(WeekGenerator::line).collect(Collectors.joining("\n")));
        return variables;
    }

    static String focus(WeekFocus focus) {
        List<String> lines = new ArrayList<>();
        for (FocusPoint point : focus.points()) {
            lines.add("- " + point.subcategory()
                    + (point.why().isBlank() ? "" : ": " + point.why().strip()));
        }
        focus.grammarPractice()
                .forEach(topic -> lines.add("- " + topic.subcategory() + ": " + topic.occurrencesLast7Days()
                        + " mistakes in the last 7 days; its grammar practice is mandatory"));
        return lines.isEmpty() ? "none" : "\n" + String.join("\n", lines);
    }

    static String review(WeeklyReview review) {
        String mistakes = review.topMistakes().isEmpty()
                ? "none"
                : review.topMistakes().stream().map(ReviewMistake::subcategory).collect(Collectors.joining(", "));
        return "top mistakes: " + mistakes + (review.lowActivity() ? "; fewer than 3 tasks were done" : "");
    }

    /** One candidate as a compact JSON object; the model sees no ids but the ref. */
    static String line(WeekCandidate candidate) {
        WeekTask task = candidate.task();
        StringBuilder line = new StringBuilder("{");
        field(line, "ref", candidate.ref());
        field(line, "kind", task.kind().name());
        field(line, "section", task.section().name());
        if (task.taskType() != null) {
            field(line, "taskType", task.taskType().name());
        }
        field(line, "title", task.title());
        line.append(",\"minutes\":").append(minutes(task.estimatedMinutes()));
        if (!candidate.skills().isEmpty()) {
            line.append(",\"skills\":[")
                    .append(candidate.skills().stream()
                            .map(WeekGenerator::quote)
                            .collect(Collectors.joining(",")))
                    .append(']');
        }
        if (candidate.mandatory()) {
            line.append(",\"mandatory\":true");
        }
        return line.append('}').toString();
    }

    private static void field(StringBuilder line, String name, String value) {
        if (line.length() > 1) {
            line.append(',');
        }
        line.append(quote(name)).append(':').append(quote(value));
    }

    static String minutes(double minutes) {
        return minutes == Math.rint(minutes) ? String.valueOf((long) minutes) : String.valueOf(minutes);
    }

    static String quote(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r', '\t' -> quoted.append(' ');
                default -> quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }

    static String violationsBlock(List<String> violations) {
        if (violations.isEmpty()) {
            return "";
        }
        return "Your previous draft broke these rules; fix all of them and answer again:\n- "
                + String.join("\n- ", violations);
    }

    static String cut(String text) {
        return text.length() <= RATIONALE_LENGTH ? text : text.substring(0, RATIONALE_LENGTH);
    }
}
