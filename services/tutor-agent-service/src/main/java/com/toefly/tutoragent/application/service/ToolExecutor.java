package com.toefly.tutoragent.application.service;

import com.toefly.tutoragent.application.port.DependencyUnavailableException;
import com.toefly.tutoragent.application.port.NotSubmittedException;
import com.toefly.tutoragent.application.port.in.LearnerModelUseCase;
import com.toefly.tutoragent.application.port.out.AiPort;
import com.toefly.tutoragent.application.port.out.AssessmentPort;
import com.toefly.tutoragent.application.port.out.MistakeMemoryRepository;
import com.toefly.tutoragent.application.port.out.MistakeRepository;
import com.toefly.tutoragent.application.port.out.OwnerToolsPort;
import com.toefly.tutoragent.application.port.out.PracticePort;
import com.toefly.tutoragent.application.port.out.PracticeResultRepository;
import com.toefly.tutoragent.application.port.out.WeeklyReviewRepository;
import com.toefly.tutoragent.domain.chat.ChatContextType;
import com.toefly.tutoragent.domain.chat.PendingAction;
import com.toefly.tutoragent.domain.chat.ToolCatalog;
import com.toefly.tutoragent.domain.learner.LearnerModel;
import com.toefly.tutoragent.domain.learner.MistakeStat;
import com.toefly.tutoragent.domain.learner.Taxonomy;
import com.toefly.tutoragent.domain.review.WeeklyReview;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Runs the agent's tools (docs/06 §4.5): one hop to an owner or a local read, never a tool outside the turn's set;
 * owners never give keys or samples of unsubmitted work, so those answer {@code NOT_SUBMITTED}.
 */
public class ToolExecutor {

    static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("getProgress", "Checking your progress"),
            Map.entry("getCurrentWeek", "Looking at your week"),
            Map.entry("getMistakeHistory", "Looking at your recurring mistakes"),
            Map.entry("searchItems", "Finding practice"),
            Map.entry("assignExtraTask", "Preparing a task for your week"),
            Map.entry("explainGrammarRule", "Finding the grammar rule"),
            Map.entry("getSubmission", "Reading your work"),
            Map.entry("getAttemptReview", "Looking at your answers"),
            Map.entry("getBandEstimate", "Checking your band estimate"),
            Map.entry("getTimeStats", "Checking your study time"),
            Map.entry("getWeeklyReview", "Opening your weekly review"));

    private final OwnerToolsPort owners;
    private final PracticeResultRepository results;
    private final PracticePort practice;
    private final AssessmentPort assessment;
    private final LearnerModelUseCase learner;
    private final MistakeRepository mistakes;
    private final MistakeMemoryRepository memory;
    private final WeeklyReviewRepository reviews;
    private final AiPort ai;
    private final Taxonomy taxonomy;
    private final Clock clock;

    public ToolExecutor(
            OwnerToolsPort owners,
            PracticeResultRepository results,
            PracticePort practice,
            AssessmentPort assessment,
            LearnerModelUseCase learner,
            MistakeRepository mistakes,
            MistakeMemoryRepository memory,
            WeeklyReviewRepository reviews,
            AiPort ai,
            Taxonomy taxonomy,
            Clock clock) {
        this.owners = owners;
        this.results = results;
        this.practice = practice;
        this.assessment = assessment;
        this.learner = learner;
        this.mistakes = mistakes;
        this.memory = memory;
        this.reviews = reviews;
        this.ai = ai;
        this.taxonomy = taxonomy;
        this.clock = clock;
    }

    /** Who calls and from which screen; never shown to the model. */
    public record ToolContext(
            UUID userId, @Nullable String bearer, UUID threadId, UUID messageId, ChatContextType screen) {}

    /** {@code action} is set when the tool proposed a change that waits for the learner's confirmation. */
    public record Outcome(
            String status,
            Map<String, Object> result,
            @Nullable PendingAction action) {}

    public static String label(String tool) {
        return LABELS.getOrDefault(tool, "Working");
    }

    public Outcome run(String tool, Map<String, Object> args, ToolContext ctx) {
        if (!ToolCatalog.allowed(tool, ctx.screen())) {
            return new Outcome("DENIED", Map.of("error", "This tool is not available on this screen."), null);
        }
        try {
            return switch (tool) {
                case "getProgress" -> ok(owners.dashboard(ctx.userId(), ctx.bearer()));
                case "getCurrentWeek" -> ok(owners.currentWeek(ctx.userId(), ctx.bearer()));
                case "getTimeStats" ->
                    ok(owners.timeReport(ctx.userId(), string(args, "period", "THIS_WEEK"), ctx.bearer()));
                case "getBandEstimate" ->
                    ok(Map.of("bands", assessment.bandEstimate(ctx.userId(), ctx.bearer()), "label", "AI estimate"));
                case "searchItems" -> ok(owners.searchItemSets(searchQuery(args)));
                case "explainGrammarRule" -> ok(grammar(args));
                case "getMistakeHistory" -> ok(history(args, ctx.userId()));
                case "getWeeklyReview" -> ok(weeklyReview(args, ctx.userId()));
                case "getSubmission" -> submission(args, ctx);
                case "getAttemptReview" -> attempt(args, ctx);
                case "assignExtraTask" -> assign(args, ctx);
                default -> new Outcome("UNKNOWN_TOOL", Map.of("error", "Unknown tool " + tool), null);
            };
        } catch (NotSubmittedException notSubmitted) {
            return new Outcome("NOT_SUBMITTED", Map.of("status", "NOT_SUBMITTED"), null);
        } catch (DependencyUnavailableException unavailable) {
            return new Outcome("UNAVAILABLE", Map.of("error", "This data is unavailable right now."), null);
        } catch (IllegalArgumentException invalid) {
            return new Outcome("INVALID_ARGUMENTS", Map.of("error", invalid.getMessage()), null);
        }
    }

    private static Outcome ok(Map<String, Object> result) {
        return new Outcome("OK", result, null);
    }

    private Outcome submission(Map<String, Object> args, ToolContext ctx) {
        UUID id = uuid(args, "submissionId");
        Integer version = args.get("version") instanceof Number n ? n.intValue() : null;
        AssessmentPort.Submission s = assessment.submission(id, version, ctx.bearer());
        if (!s.userId().equals(ctx.userId())) {
            return new Outcome("NOT_FOUND", Map.of("error", "No such submission."), null);
        }
        if (!s.evaluated() && !s.status().equals("EVALUATING") && !s.status().equals("SUBMITTED")) {
            return new Outcome("NOT_SUBMITTED", Map.of("status", "NOT_SUBMITTED"), null);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("submissionId", s.submissionId().toString());
        out.put("taskType", s.taskType());
        out.put("status", s.status());
        List<Map<String, Object>> versions = new ArrayList<>();
        for (AssessmentPort.Version v : s.versions()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("versionNo", v.versionNo());
            row.put("status", v.status());
            row.put("wordCount", v.wordCount());
            row.put("text", v.text());
            row.put("score", v.finalScore() == null ? "not evaluated" : v.finalScore() + " (AI estimate, 0-5)");
            row.put(
                    "comments",
                    v.comments().stream()
                            .limit(12)
                            .map(c -> Map.of(
                                    "quote", c.quote(),
                                    "comment", c.comment(),
                                    "suggestion", c.suggestion() == null ? "" : c.suggestion(),
                                    "subcategory", c.category() + "." + c.subcategory(),
                                    "severity", c.severity()))
                            .toList());
            versions.add(row);
        }
        out.put("versions", versions);
        if (s.evaluated() && s.modelAnswer() != null) {
            out.put("modelAnswer", s.modelAnswer());
        }
        return ok(out);
    }

    private Outcome attempt(Map<String, Object> args, ToolContext ctx) {
        UUID attemptId = uuid(args, "attemptId");
        if (!results.ownerOf(attemptId).map(ctx.userId()::equals).orElse(false)) {
            practice.review(attemptId, ctx.bearer());
            return new Outcome("NOT_FOUND", Map.of("error", "No such attempt of this student."), null);
        }
        PracticePort.AttemptReview review = practice.review(attemptId, ctx.bearer());
        UUID only = args.get("itemId") == null ? null : uuid(args, "itemId");
        List<Map<String, Object>> items = review.items().stream()
                .filter(i -> only == null || i.itemId().equals(only))
                .map(i -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("question", i.ordinal());
                    row.put("itemId", i.itemId().toString());
                    row.put("correct", i.correct());
                    row.put("studentAnswer", i.answer() == null ? "no answer" : i.answer());
                    row.put("correctAnswer", i.key() == null ? "" : i.key());
                    row.put("explanation", i.explanation() == null ? "" : i.explanation());
                    return row;
                })
                .toList();
        return ok(Map.of("attemptId", review.attemptId().toString(), "taskType", review.taskType(), "items", items));
    }

    private Outcome assign(Map<String, Object> args, ToolContext ctx) {
        String itemSetId = uuid(args, "itemSetId").toString();
        String reason = string(args, "reason", "Extra practice from Toefly Tutor");
        String day = string(args, "day", "NEXT_FREE_DAY");
        if (!List.of("TODAY", "TOMORROW", "NEXT_FREE_DAY").contains(day)) {
            throw new IllegalArgumentException("day must be TODAY, TOMORROW or NEXT_FREE_DAY");
        }
        Instant now = clock.instant();
        PendingAction action = new PendingAction(
                UUID.randomUUID(),
                ctx.userId(),
                ctx.threadId(),
                ctx.messageId(),
                "ASSIGN_EXTRA_TASK",
                clip("Add to my week: " + reason, 120),
                Map.of("itemSetId", itemSetId, "day", day, "reason", clip(reason, 200)),
                PendingAction.Status.PENDING,
                null,
                now,
                now.plus(PendingAction.LIFETIME));
        return new Outcome(
                "PENDING_CONFIRMATION",
                Map.of(
                        "status", "PENDING_CONFIRMATION",
                        "actionId", action.id().toString(),
                        "note", "The student must confirm with the button; the task is not added yet."),
                action);
    }

    private Map<String, Object> searchQuery(Map<String, Object> args) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("taskType", string(args, "taskType", "READ_ACADEMIC_PASSAGE"));
        if (args.get("skillFocus") != null) {
            query.put("skillFocus", args.get("skillFocus").toString());
        }
        if (args.get("topic") != null) {
            query.put("topic", clip(args.get("topic").toString(), 100));
        }
        query.put("difficulty", string(args, "difficulty", "SAME"));
        query.put("limit", Math.max(1, Math.min(5, args.get("limit") instanceof Number n ? n.intValue() : 3)));
        query.put("excludeItemSetIds", List.of());
        return query;
    }

    private Map<String, Object> grammar(Map<String, Object> args) {
        String topic = string(args, "topic", "");
        String subcategory = topic.matches("[A-Z_]+\\.[A-Z_]+") ? topic : null;
        Map<String, Object> out =
                new LinkedHashMap<>(owners.searchGrammarUnits(subcategory == null ? topic : null, subcategory, 3));
        if (subcategory != null && taxonomy.entry(subcategory) != null) {
            out.put("units", out.getOrDefault("units", List.of()));
            out.put(
                    "taxonomyUnits",
                    taxonomy.unitRefs(subcategory).stream()
                            .map(taxonomy::unitTitle)
                            .toList());
        }
        return out;
    }

    private Map<String, Object> history(Map<String, Object> args, UUID userId) {
        LearnerModel model = learner.get(userId);
        int weeks = args.get("sinceWeeks") instanceof Number n ? Math.max(1, Math.min(16, n.intValue())) : 4;
        int limit = args.get("limit") instanceof Number n ? Math.max(1, Math.min(10, n.intValue())) : 5;
        String category = args.get("category") == null
                ? null
                : args.get("category").toString().toUpperCase(Locale.ROOT);
        String subcategory = args.get("subcategory") == null
                ? null
                : args.get("subcategory").toString().toUpperCase(Locale.ROOT);
        Instant since = clock.instant().minus(Duration.ofDays(7L * weeks));
        List<Map<String, Object>> stats = new ArrayList<>();
        for (MistakeStat s : model.mistakeStats()) {
            if ((category != null && !s.category().name().equals(category))
                    || (subcategory != null
                            && !s.subcategory().equals(subcategory)
                            && !s.subcategory().endsWith("." + subcategory))
                    || s.lastSeenAt().isBefore(since)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subcategory", s.subcategory());
            row.put("title", taxonomy.title(s.subcategory(), model.language()));
            row.put("mistakesLast4Weeks", s.occurrences28Days());
            row.put("errorRate", s.errorRate());
            row.put("trend", s.trend().name());
            row.put("status", s.status().name());
            row.put("units", s.unitRefs().stream().map(taxonomy::unitTitle).toList());
            row.put(
                    "examples",
                    mistakes.examples(userId, s.subcategory(), since, 2).stream()
                            .map(r -> Map.of(
                                    "wrong", r.excerpt() == null ? "" : r.excerpt(),
                                    "right", r.correction() == null ? "" : r.correction()))
                            .toList());
            stats.add(row);
            if (stats.size() >= limit) {
                break;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mistakes", stats);
        if (stats.isEmpty()) {
            out.put("note", "No recorded mistakes match; say there is not enough data yet.");
        }
        Object query = args.get("query");
        if (query != null && !query.toString().isBlank()) {
            out.put("similar", similar(userId, clip(query.toString(), 300)));
        }
        return out;
    }

    List<String> similar(UUID userId, String query) {
        try {
            float[] vector = ai.embed(List.of(query), userId).getFirst();
            return memory.nearest(userId, vector, 5, 0.55, List.of()).stream()
                    .map(MistakeMemoryRepository.MemoryHit::content)
                    .toList();
        } catch (RuntimeException unavailable) {
            return List.of();
        }
    }

    private Map<String, Object> weeklyReview(Map<String, Object> args, UUID userId) {
        Optional<WeeklyReview> review = args.get("weekNumber") instanceof Number n
                ? reviews.list(userId, n.intValue() + 1, 1).stream()
                        .filter(r -> r.weekNo() == n.intValue())
                        .findFirst()
                : reviews.list(userId, null, 1).stream().findFirst();
        if (review.isEmpty()) {
            return Map.of("note", "No weekly review yet.");
        }
        WeeklyReview r = review.get();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("weekNo", r.weekNo());
        out.put("headline", r.text().headline());
        out.put("summary", r.text().summary());
        out.put("tasksDone", r.facts().tasksDone());
        out.put("tasksTotal", r.facts().tasksTotal());
        out.put(
                "topMistakes",
                r.text().topMistakes().stream()
                        .map(m -> Map.of("subcategory", m.subcategory(), "count", m.count(), "advice", m.advice()))
                        .toList());
        out.put("strengths", r.text().strengths());
        out.put(
                "nextWeekFocus",
                r.text().nextWeekFocus().stream()
                        .map(WeeklyReview.Focus::subcategory)
                        .toList());
        return out;
    }

    private static UUID uuid(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        try {
            return UUID.fromString(value.toString().strip());
        } catch (IllegalArgumentException bad) {
            throw new IllegalArgumentException(name + " must be an id from a previous tool result");
        }
    }

    private static String string(Map<String, Object> args, String name, String fallback) {
        Object value = args.get(name);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
