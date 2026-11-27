package com.toefly.tutoragent.application.service;

import com.toefly.tutoragent.application.port.in.LearnerModelUseCase;
import com.toefly.tutoragent.application.port.out.AssessmentPort;
import com.toefly.tutoragent.application.port.out.Cause;
import com.toefly.tutoragent.application.port.out.LearnerEventPublisherPort;
import com.toefly.tutoragent.application.port.out.LearnerModelRepository;
import com.toefly.tutoragent.application.port.out.MistakeMemoryRepository;
import com.toefly.tutoragent.application.port.out.MistakeRepository;
import com.toefly.tutoragent.application.port.out.PracticePort;
import com.toefly.tutoragent.application.port.out.PracticeResultRepository;
import com.toefly.tutoragent.application.port.out.PublicationSchedule;
import com.toefly.tutoragent.domain.learner.ClosedItemClassifier;
import com.toefly.tutoragent.domain.learner.ExplanationLanguage;
import com.toefly.tutoragent.domain.learner.LearnerModel;
import com.toefly.tutoragent.domain.learner.MistakeCategory;
import com.toefly.tutoragent.domain.learner.MistakeRecord;
import com.toefly.tutoragent.domain.learner.MistakeStatistics;
import com.toefly.tutoragent.domain.learner.Opportunity;
import com.toefly.tutoragent.domain.learner.Severity;
import com.toefly.tutoragent.domain.learner.SourceType;
import com.toefly.tutoragent.domain.learner.Taxonomy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the learner model (docs/06 §6.2): appends mistake records and opportunities, recomputes the statistics, queues
 * the memory embedding and opens the 5-minute window of learner-model-updated. Reads of owners happen before the
 * transaction of the caller.
 */
public class LearnerModelService implements LearnerModelUseCase {

    static final Duration HISTORY = Duration.ofDays(120);
    static final Duration DEBOUNCE = Duration.ofMinutes(5);
    /** Categories an evaluated writing gives an opportunity to (docs/06 §6.2). */
    static final List<MistakeCategory> WRITING_CATEGORIES = List.of(
            MistakeCategory.TASK_RESPONSE,
            MistakeCategory.COHERENCE,
            MistakeCategory.GRAMMAR,
            MistakeCategory.VOCABULARY,
            MistakeCategory.SPELLING);

    private final LearnerModelRepository models;
    private final MistakeRepository mistakes;
    private final MistakeMemoryRepository memory;
    private final PublicationSchedule publications;
    private final LearnerEventPublisherPort events;
    private final PracticeResultRepository results;
    private final PracticePort practice;
    private final AssessmentPort assessment;
    private final Taxonomy taxonomy;
    private final Transactions transactions;
    private final Clock clock;

    public LearnerModelService(
            LearnerModelRepository models,
            MistakeRepository mistakes,
            MistakeMemoryRepository memory,
            PublicationSchedule publications,
            LearnerEventPublisherPort events,
            PracticeResultRepository results,
            PracticePort practice,
            AssessmentPort assessment,
            Taxonomy taxonomy,
            Transactions transactions,
            Clock clock) {
        this.models = models;
        this.mistakes = mistakes;
        this.memory = memory;
        this.publications = publications;
        this.events = events;
        this.results = results;
        this.practice = practice;
        this.assessment = assessment;
        this.taxonomy = taxonomy;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public LearnerModel get(UUID userId) {
        return models.find(userId).orElseGet(() -> LearnerModel.empty(userId));
    }

    @Override
    public void attemptScored(ScoredAttempt attempt, Cause cause) {
        boolean anyWrong = attempt.items().stream().anyMatch(i -> i.answered() && !i.correct());
        PracticePort.AttemptReview review =
                anyWrong || needsKeys(attempt.taskType()) ? practice.review(attempt.attemptId(), null) : null;
        List<MistakeRecord> records = new ArrayList<>();
        List<Opportunity> opportunities = new ArrayList<>();
        for (ScoredItem item : attempt.items()) {
            if (!item.answered()) {
                continue;
            }
            PracticePort.ItemReview itemReview = review == null ? null : find(review, item.itemId());
            String key = itemReview == null ? null : itemReview.key();
            opportunities.add(new Opportunity(
                    ClosedItemClassifier.skillOf(attempt.taskType(), key),
                    attempt.attemptId(),
                    item.itemId(),
                    attempt.scoredAt()));
            if (!item.correct()) {
                String answer = itemReview == null ? null : itemReview.answer();
                String code = taxonomy.normalize(ClosedItemClassifier.mistakeOf(attempt.taskType(), answer, key));
                records.add(new MistakeRecord(
                        UUID.randomUUID(),
                        attempt.userId(),
                        code,
                        SourceType.CLOSED_ITEM,
                        attempt.attemptId(),
                        item.itemId(),
                        attempt.taskType(),
                        answer == null ? null : "Answered: " + answer,
                        key == null ? null : "Correct: " + key,
                        itemReview == null ? null : itemReview.explanation(),
                        Severity.CLOSED_ITEM,
                        taxonomy.unitRefs(code),
                        attempt.scoredAt()));
            }
        }
        long answered = attempt.items().stream().filter(ScoredItem::answered).count();
        long correct = attempt.items().stream()
                .filter(i -> i.answered() && i.correct())
                .count();
        int total = attempt.items().size();
        PracticeResultRepository.Result result = new PracticeResultRepository.Result(
                attempt.attemptId(),
                attempt.userId(),
                attempt.taskType(),
                total == 0 ? null : (double) correct / total,
                null,
                attempt.scoredAt());
        append(attempt.userId(), records, opportunities, cause, answered == 0 && total == 0 ? null : result);
    }

    @Override
    public void submissionEvaluated(UUID userId, UUID submissionId, int versionNo, Instant evaluatedAt, Cause cause) {
        AssessmentPort.Submission submission = assessment.submission(submissionId, versionNo, null);
        AssessmentPort.Version version = submission.versions().stream()
                .filter(v -> v.versionNo() == versionNo)
                .findFirst()
                .orElse(null);
        if (version == null) {
            return;
        }
        SourceType source = submission.taskType().startsWith("WRITE") ? SourceType.WRITING : SourceType.SPEAKING;
        List<MistakeRecord> records = new ArrayList<>();
        for (AssessmentPort.Comment comment : version.comments()) {
            String code = normalize(comment.category(), comment.subcategory());
            records.add(new MistakeRecord(
                    UUID.randomUUID(),
                    userId,
                    code,
                    source,
                    submissionId,
                    null,
                    submission.taskType(),
                    comment.quote(),
                    comment.suggestion(),
                    comment.comment(),
                    severity(comment.severity()),
                    taxonomy.unitRefs(code),
                    evaluatedAt));
        }
        List<Opportunity> opportunities = WRITING_CATEGORIES.stream()
                .map(c -> new Opportunity(c.name(), submissionId, null, evaluatedAt))
                .toList();
        PracticeResultRepository.Result result = new PracticeResultRepository.Result(
                submissionId, userId, submission.taskType(), null, version.finalScore(), evaluatedAt);
        append(userId, records, opportunities, cause, result);
    }

    @Override
    public void bandsUpdated(UUID userId, Map<String, Double> bands, Cause cause) {
        update(userId, m -> m.withBands(bands, clock.instant()), cause);
    }

    @Override
    public void goalChanged(
            UUID userId,
            @Nullable Double targetBand,
            @Nullable LocalDate examDate,
            @Nullable String timezone,
            ExplanationLanguage language,
            Cause cause) {
        update(userId, m -> m.withGoal(targetBand, examDate, timezone, language, clock.instant()), cause);
    }

    @Override
    public void languageChanged(UUID userId, ExplanationLanguage language, Cause cause) {
        update(userId, m -> m.withLanguage(language, clock.instant()), cause);
    }

    @Override
    public void weekCompleted(UUID userId, int weekNo) {
        transactions.run(() -> models.snapshot(recomputed(get(userId)), weekNo));
    }

    @Override
    public int publishDue(Instant now) {
        List<UUID> due = publications.due(now, 100);
        for (UUID userId : due) {
            transactions.run(() -> {
                events.learnerModelUpdated(get(userId), Cause.none());
                publications.close(userId);
            });
        }
        return due.size();
    }

    private void append(
            UUID userId,
            List<MistakeRecord> records,
            List<Opportunity> opportunities,
            Cause cause,
            PracticeResultRepository.@Nullable Result result) {
        transactions.run(() -> {
            if (result != null) {
                results.save(result);
            }
            mistakes.append(userId, records, opportunities);
            memory.enqueue(
                    records.stream().filter(r -> r.severity().memorable()).toList());
            LearnerModel model = recomputed(get(userId));
            models.save(model);
            publications.open(userId, clock.instant().plus(DEBOUNCE));
        });
    }

    private void update(UUID userId, UnaryOperator<LearnerModel> change, Cause cause) {
        transactions.run(() -> {
            models.save(change.apply(get(userId)));
            publications.open(userId, clock.instant().plus(DEBOUNCE));
        });
    }

    private LearnerModel recomputed(LearnerModel model) {
        Instant now = clock.instant();
        Instant since = now.minus(HISTORY);
        return model.withStats(
                MistakeStatistics.compute(
                        mistakes.records(model.userId(), since),
                        mistakes.opportunities(model.userId(), since),
                        taxonomy,
                        now),
                now);
    }

    private String normalize(String category, String subcategory) {
        String code = subcategory.contains(".") ? subcategory : category + "." + subcategory;
        try {
            return taxonomy.normalize(code.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownCategory) {
            return "TASK_RESPONSE.OTHER";
        }
    }

    private static Severity severity(String value) {
        return switch (value) {
            case "CRITICAL" -> Severity.CRITICAL;
            case "MAJOR" -> Severity.MAJOR;
            default -> Severity.MINOR;
        };
    }

    private static boolean needsKeys(String taskType) {
        return taskType.equals("BUILD_A_SENTENCE");
    }

    private static PracticePort.@Nullable ItemReview find(PracticePort.AttemptReview review, UUID itemId) {
        return review.items().stream()
                .filter(i -> i.itemId().equals(itemId))
                .findFirst()
                .orElse(null);
    }
}
