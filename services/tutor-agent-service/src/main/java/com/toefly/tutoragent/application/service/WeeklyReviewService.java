package com.toefly.tutoragent.application.service;

import com.toefly.tutoragent.application.port.AiUnavailableException;
import com.toefly.tutoragent.application.port.in.EntitlementRequiredException;
import com.toefly.tutoragent.application.port.in.LearnerModelUseCase;
import com.toefly.tutoragent.application.port.in.WeeklyReviewUseCase;
import com.toefly.tutoragent.application.port.out.AiPort;
import com.toefly.tutoragent.application.port.out.Cause;
import com.toefly.tutoragent.application.port.out.ClosedWeek;
import com.toefly.tutoragent.application.port.out.DeferredReviewRepository;
import com.toefly.tutoragent.application.port.out.EntitlementPort;
import com.toefly.tutoragent.application.port.out.LearnerModelRepository;
import com.toefly.tutoragent.application.port.out.MistakeRepository;
import com.toefly.tutoragent.application.port.out.PracticeResultRepository;
import com.toefly.tutoragent.application.port.out.ReviewEventPublisherPort;
import com.toefly.tutoragent.application.port.out.WeeklyReviewRepository;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.learner.LearnerModel;
import com.toefly.tutoragent.domain.learner.MistakeRecord;
import com.toefly.tutoragent.domain.learner.MistakeStat;
import com.toefly.tutoragent.domain.learner.MistakeStatus;
import com.toefly.tutoragent.domain.learner.Taxonomy;
import com.toefly.tutoragent.domain.review.ReviewValidator;
import com.toefly.tutoragent.domain.review.WeekFacts;
import com.toefly.tutoragent.domain.review.WeeklyReview;
import com.toefly.tutoragent.domain.review.WeeklyReviewTemplate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WeeklyReviewer (docs/06 §5.3): collects the facts of the week without AI, lets the model word them through
 * {@code tutor.weekly-review}, validates the wording against the facts, retries once with the violations and falls
 * back to the template. When AI is unavailable the review is deferred: every 10 minutes, 12 times, then the template.
 */
public class WeeklyReviewService implements WeeklyReviewUseCase {

    static final String PROMPT = "tutor.weekly-review";
    static final String ENTITLEMENT = "WEEKLY_REVIEW";
    static final Duration RETRY_DELAY = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 12;

    private static final Logger log = LoggerFactory.getLogger(WeeklyReviewService.class);

    private final LearnerModelUseCase learner;
    private final LearnerModelRepository models;
    private final MistakeRepository mistakes;
    private final PracticeResultRepository results;
    private final WeeklyReviewRepository reviews;
    private final DeferredReviewRepository deferred;
    private final EntitlementPort entitlements;
    private final AiPort ai;
    private final ReviewEventPublisherPort events;
    private final Taxonomy taxonomy;
    private final Transactions transactions;
    private final Clock clock;

    public WeeklyReviewService(
            LearnerModelUseCase learner,
            LearnerModelRepository models,
            MistakeRepository mistakes,
            PracticeResultRepository results,
            WeeklyReviewRepository reviews,
            DeferredReviewRepository deferred,
            EntitlementPort entitlements,
            AiPort ai,
            ReviewEventPublisherPort events,
            Taxonomy taxonomy,
            Transactions transactions,
            Clock clock) {
        this.learner = learner;
        this.models = models;
        this.mistakes = mistakes;
        this.results = results;
        this.reviews = reviews;
        this.deferred = deferred;
        this.entitlements = entitlements;
        this.ai = ai;
        this.events = events;
        this.taxonomy = taxonomy;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public void weekCompleted(ClosedWeek week) {
        learner.weekCompleted(week.userId(), week.weekNo());
        if (!entitlements.has(week.userId(), ENTITLEMENT)) {
            log.info("No {} right for user {}, week {} has no review", ENTITLEMENT, week.userId(), week.weekNo());
            return;
        }
        generate(week, 0);
    }

    @Override
    public int retryDeferred(Instant now) {
        List<DeferredReviewRepository.Deferred> due = deferred.due(now, 20);
        due.forEach(d -> generate(d.week(), d.attempts()));
        return due.size();
    }

    @Override
    public Optional<WeeklyReview> get(UUID userId, UUID reviewId) {
        requireRight(userId);
        return reviews.find(reviewId).filter(r -> r.userId().equals(userId));
    }

    @Override
    public List<WeeklyReview> list(UUID userId, @Nullable UUID weekId, @Nullable Integer beforeWeekNo, int limit) {
        requireRight(userId);
        if (weekId != null) {
            return reviews.findByWeek(weekId).filter(r -> r.userId().equals(userId)).stream()
                    .toList();
        }
        return reviews.list(userId, beforeWeekNo, limit);
    }

    private void requireRight(UUID userId) {
        if (!entitlements.has(userId, ENTITLEMENT)) {
            throw new EntitlementRequiredException(ENTITLEMENT);
        }
    }

    void generate(ClosedWeek week, int attempts) {
        LearnerModel model = learner.get(week.userId());
        ExplanationLanguage language = model.language();
        WeekFacts facts = facts(week, model, language);
        WeeklyReview.Text text;
        boolean fallback;
        if (facts.lowActivity()) {
            text = WeeklyReviewTemplate.of(facts, language);
            fallback = true;
        } else {
            try {
                text = worded(week, facts, language);
                fallback = text == null;
                if (text == null) {
                    text = WeeklyReviewTemplate.of(facts, language);
                }
            } catch (AiUnavailableException unavailable) {
                if (attempts + 1 < MAX_ATTEMPTS && !unavailable.rejected()) {
                    Duration delay = unavailable.retryAfter() != null
                                    && unavailable.retryAfter().compareTo(RETRY_DELAY) > 0
                            ? unavailable.retryAfter()
                            : RETRY_DELAY;
                    transactions.run(() -> deferred.defer(
                            week, attempts + 1, clock.instant().plus(delay), clip(unavailable.getMessage())));
                    log.warn("Weekly review of week {} deferred: {}", week.weekId(), unavailable.getMessage());
                    return;
                }
                text = WeeklyReviewTemplate.of(facts, language);
                fallback = true;
            }
        }
        store(week, facts, language, text, fallback);
    }

    private void store(
            ClosedWeek week, WeekFacts facts, ExplanationLanguage language, WeeklyReview.Text text, boolean fallback) {
        UUID id = reviews.findByWeek(week.weekId()).map(WeeklyReview::id).orElseGet(UUID::randomUUID);
        WeeklyReview review = new WeeklyReview(
                id,
                week.userId(),
                week.weekId(),
                week.weekNo(),
                clock.instant(),
                language,
                fallback,
                facts.lowActivity(),
                text,
                facts,
                null);
        transactions.run(() -> {
            reviews.save(review);
            deferred.remove(week.weekId());
            events.weeklyReviewGenerated(review, new Cause(week.correlationId(), week.causationId()));
        });
    }

    /** The model's wording, or null when it failed validation twice. */
    private WeeklyReview.@Nullable Text worded(ClosedWeek week, WeekFacts facts, ExplanationLanguage language) {
        List<String> violations = List.of();
        for (int round = 0; round < 2; round++) {
            AiPort.Completion completion = ai.complete(new AiPort.CompletionCall(
                    "WEEKLY_REVIEW",
                    PROMPT,
                    null,
                    variables(facts, language, violations),
                    week.userId(),
                    "weekly-review",
                    "BACKGROUND",
                    90_000,
                    "weekly-review:" + week.weekId() + ":" + round,
                    null));
            if (completion.output() == null) {
                violations = List.of("the answer must be the JSON object of the schema");
                continue;
            }
            WeeklyReview.Text text = ReviewOutput.parse(completion.output(), facts, taxonomy);
            violations = ReviewValidator.violations(text, facts);
            if (violations.isEmpty()) {
                return text;
            }
            log.info("Weekly review of week {} failed validation: {}", week.weekId(), violations);
        }
        return null;
    }

    WeekFacts facts(ClosedWeek week, LearnerModel model, ExplanationLanguage language) {
        Instant from = week.startDate().atStartOfDay().toInstant(ZoneOffset.UTC).minus(Duration.ofHours(14));
        Instant to = week.closedAt();
        List<MistakeRecord> weekRecords = mistakes.records(week.userId(), from).stream()
                .filter(r -> !r.createdAt().isAfter(to))
                .toList();
        Map<String, Long> counts = weekRecords.stream()
                .collect(Collectors.groupingBy(MistakeRecord::subcategory, TreeMap::new, Collectors.counting()));
        Map<String, Double> priority = new LinkedHashMap<>();
        model.mistakeStats().forEach(s -> priority.put(s.subcategory(), s.priority()));
        List<WeekFacts.MistakeLine> lines = counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingDouble(e -> priority.getOrDefault(e.getKey(), 0.0))
                        .reversed()
                        .thenComparing(
                                Map.Entry.<String, Long>comparingByValue().reversed()))
                .limit(5)
                .map(e -> new WeekFacts.MistakeLine(
                        e.getKey(),
                        e.getKey().substring(0, e.getKey().indexOf('.')),
                        taxonomy.title(e.getKey(), language),
                        e.getValue().intValue(),
                        weekRecords.stream()
                                .filter(r -> r.subcategory().equals(e.getKey()) && r.excerpt() != null)
                                .sorted(Comparator.comparingDouble((MistakeRecord r) ->
                                                r.severity().weight())
                                        .reversed())
                                .limit(2)
                                .map(r -> new WeekFacts.Example(r.excerpt(), r.correction()))
                                .toList(),
                        taxonomy.unitRefs(e.getKey())))
                .toList();
        List<WeekFacts.FocusCandidate> candidates = model.mistakeStats().stream()
                .filter(s -> s.status() != MistakeStatus.RESOLVED)
                .sorted(Comparator.comparingDouble(MistakeStat::weight).reversed())
                .limit(5)
                .map(s -> new WeekFacts.FocusCandidate(
                        s.subcategory(), taxonomy.title(s.subcategory(), language), s.weight()))
                .toList();
        Map<String, Double> previousBands = models.snapshotOf(week.userId(), week.weekNo() - 1)
                .map(LearnerModel::bands)
                .orElse(Map.of());
        List<String> previousTop = reviews.latest(week.userId(), week.weekNo())
                .map(r -> r.text().topMistakes().stream()
                        .map(WeeklyReview.TopMistake::subcategory)
                        .toList())
                .orElse(List.of());
        return new WeekFacts(
                week.weekNo(),
                week.tasksTotal(),
                week.tasksDone(),
                Math.round(week.progressPercent() * 10) / 10.0,
                null,
                week.sections().stream()
                        .map(s -> new WeekFacts.SectionLine(
                                s.section(), s.tasksTotal(), s.tasksDone(), s.minutesPlanned(), s.minutesDone()))
                        .toList(),
                scores(results.between(week.userId(), from, to)),
                model.bands(),
                previousBands,
                lines,
                previousTop,
                candidates);
    }

    static List<WeekFacts.ScoreLine> scores(List<PracticeResultRepository.Result> rows) {
        Map<String, List<PracticeResultRepository.Result>> byType = rows.stream()
                .collect(Collectors.groupingBy(
                        PracticeResultRepository.Result::taskType, TreeMap::new, Collectors.toList()));
        List<WeekFacts.ScoreLine> lines = new ArrayList<>();
        byType.forEach((type, list) -> {
            Double accuracy = average(
                    list.stream().map(PracticeResultRepository.Result::accuracy).toList());
            Double score = average(
                    list.stream().map(PracticeResultRepository.Result::score).toList());
            lines.add(new WeekFacts.ScoreLine(
                    type,
                    list.size(),
                    accuracy == null ? null : Math.round(accuracy * 1000) / 10.0,
                    score == null ? null : Math.round(score * 10) / 10.0));
        });
        return lines;
    }

    private static @Nullable Double average(List<@Nullable Double> values) {
        List<Double> present =
                values.stream().filter(java.util.Objects::nonNull).toList();
        return present.isEmpty()
                ? null
                : present.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static Map<String, Object> variables(
            WeekFacts facts, ExplanationLanguage language, List<String> violations) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("weekStats", ReviewOutput.stats(facts));
        variables.put("scores", ReviewOutput.scores(facts));
        variables.put("mistakes", ReviewOutput.mistakes(facts));
        variables.put("previous", String.join(", ", facts.previousTopMistakes()));
        variables.put("focusCandidates", ReviewOutput.candidates(facts));
        variables.put("explanationLanguage", language == ExplanationLanguage.RU ? "Russian" : "English");
        variables.put(
                "violations",
                violations.isEmpty()
                        ? ""
                        : "Your previous answer broke these rules; fix them: " + String.join("; ", violations));
        return variables;
    }

    private static String clip(@Nullable String message) {
        String text = message == null ? "" : message;
        return text.length() <= 300 ? text : text.substring(0, 300);
    }
}
