package com.toefly.assessment.application.service;

import com.toefly.assessment.application.port.in.Caller;
import com.toefly.assessment.application.port.in.SubmissionFilter;
import com.toefly.assessment.application.port.out.AiGradingPort;
import com.toefly.assessment.application.port.out.AiUnavailableException;
import com.toefly.assessment.application.port.out.AssessmentEventPublisherPort;
import com.toefly.assessment.application.port.out.Cause;
import com.toefly.assessment.application.port.out.ConcurrencyConflictException;
import com.toefly.assessment.application.port.out.DeferredEvaluationRepository;
import com.toefly.assessment.application.port.out.EstimateInputsRepository;
import com.toefly.assessment.application.port.out.GradingRequest;
import com.toefly.assessment.application.port.out.IdempotencyStorePort;
import com.toefly.assessment.application.port.out.SubmissionRepository;
import com.toefly.assessment.application.port.out.TaskContent;
import com.toefly.assessment.application.port.out.TaskContentPort;
import com.toefly.assessment.application.port.out.TutorLinkRepository;
import com.toefly.assessment.domain.estimate.BandEstimate;
import com.toefly.assessment.domain.estimate.EstimateInputs;
import com.toefly.assessment.domain.estimate.Section;
import com.toefly.assessment.domain.estimate.WeeklyEstimate;
import com.toefly.assessment.domain.evaluation.CriterionScore;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.evaluation.EvaluatorType;
import com.toefly.assessment.domain.evaluation.InlineAnnotation;
import com.toefly.assessment.domain.evaluation.MistakeCategory;
import com.toefly.assessment.domain.evaluation.Severity;
import com.toefly.assessment.domain.grading.BulletCoverage;
import com.toefly.assessment.domain.grading.PassOutput;
import com.toefly.assessment.domain.grading.RawComment;
import com.toefly.assessment.domain.grading.Taxonomy;
import com.toefly.assessment.domain.submission.DeferredEvaluation;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.assessment.domain.submission.TaskType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** In-memory adapters and a wired application for the scenario tests. */
final class Fakes {

    static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID TUTOR = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    static final UUID DISCUSSION_SET = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final UUID EMAIL_SET = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    static final Caller STUDENT_CALLER = new Caller(STUDENT, Set.of(Caller.Role.STUDENT));
    static final Caller TUTOR_CALLER = new Caller(TUTOR, Set.of(Caller.Role.TUTOR));
    static final Caller STRANGER_CALLER = new Caller(STRANGER, Set.of(Caller.Role.STUDENT));
    static final Caller ADMIN_CALLER = new Caller(STRANGER, Set.of(Caller.Role.ADMIN));

    static final String RESPONSE = "I agree with Maya because people lives longer when they eat healthy food. "
            + "Their is also a reason connected to money: healthy products are expensive, so a tax on sugar could "
            + "pay for cheaper vegetables in schools and hospitals in my city. When I was a student, I often bought "
            + "fast food because it was the cheapest option near my university, and I did not think about my health. "
            + "If fresh food had cost less, I would have chosen it, so I believe prices shape our habits more than "
            + "advertising does.";

    final MutableClock clock = new MutableClock(Instant.parse("2026-11-18T10:00:00Z"));
    final Submissions submissions = new Submissions();
    final Deferred deferred = new Deferred();
    final Estimates estimates = new Estimates();
    final Links links = new Links();
    final Events events = new Events();
    final Content content = new Content();
    final ScriptedAi ai = new ScriptedAi();
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final Transactions transactions = new Transactions(new NoTransactions());
    final Access access = new Access(links);
    final GradingKit kit = new GradingKit(
            taxonomy(),
            Map.of(
                    TaskType.WRITE_ACADEMIC_DISCUSSION,
                    List.of(
                            new GradingKit.Anchor("A", 5, "t", "five", "why"),
                            new GradingKit.Anchor("A", 3, "t", "three", "why"),
                            new GradingKit.Anchor("B", 4, "t", "four", "why"),
                            new GradingKit.Anchor("B", 2, "t", "two", "why"))));
    final BandEstimateService estimateService =
            new BandEstimateService(estimates, submissions, events, access, transactions, clock);
    final WritingGrader grader = new WritingGrader(ai, kit);
    final SubmissionService commands =
            new SubmissionService(submissions, content, events, estimateService, access, transactions, clock);
    final EvaluationService worker =
            new EvaluationService(submissions, deferred, content, grader, events, estimateService, clock, meters);
    final DeferredEvaluationService retries =
            new DeferredEvaluationService(deferred, submissions, events, transactions, clock, meters);
    final SubmissionQueryService queries = new SubmissionQueryService(submissions, deferred, content, access);
    final TutorLinkService tutorLinks = new TutorLinkService(links);

    /** commitOnce of the Kafka listener: runs the effect once per delivery. */
    static final Predicate<Runnable> ONCE = effect -> {
        effect.run();
        return true;
    };

    static Taxonomy taxonomy() {
        Map<String, Taxonomy.Entry> entries = new HashMap<>();
        for (String code : List.of(
                "GRAMMAR.SUBJECT_VERB_AGREEMENT",
                "GRAMMAR.OTHER",
                "SPELLING.HOMOPHONES",
                "SPELLING.OTHER",
                "VOCABULARY.OTHER",
                "COHERENCE.OTHER",
                "TASK_RESPONSE.OTHER",
                "TASK_RESPONSE.ELABORATION")) {
            entries.put(
                    code,
                    new Taxonomy.Entry(
                            code,
                            code,
                            code.startsWith("GRAMMAR.S") ? "ROUNDUP5.U10" : null,
                            code.startsWith("GRAMMAR.S") ? "Round-Up Unit 10: Nouns – Articles" : null));
        }
        return new Taxonomy(entries);
    }

    static PassOutput pass(String tag, int score, RawComment... comments) {
        List<CriterionScore> criteria = CriterionScore.WRITING.stream()
                .map(c -> new CriterionScore(c, score, "note"))
                .toList();
        return new PassOutput(
                tag,
                score,
                criteria,
                List.of(new BulletCoverage(1, BulletCoverage.Status.FULL)),
                List.of(comments),
                List.of("Clear position"),
                List.of("Develop the example"),
                List.of(),
                "rationale",
                "writing.grade.discussion",
                1,
                "GROQ",
                tag.equals("B") ? "qwen/qwen3.8-27b" : "openai/gpt-oss-120b",
                false);
    }

    static RawComment lives() {
        return new RawComment(
                "people lives",
                1,
                MistakeCategory.GRAMMAR,
                "SUBJECT_VERB_AGREEMENT",
                Severity.MAJOR,
                "Plural subject takes 'live'.",
                "people live");
    }

    static RawComment their() {
        return new RawComment(
                "Their is", 1, MistakeCategory.SPELLING, "HOMOPHONES", Severity.MAJOR, "Use 'There'.", "There is");
    }

    Submission submittedDiscussion(String text) {
        Submission submission = commands.create(
                STUDENT_CALLER,
                new com.toefly.assessment.application.port.in.CreateSubmissionCommand(
                        DISCUSSION_SET, null, null, com.toefly.assessment.domain.submission.AttemptMode.PRACTICE));
        commands.saveDraft(STUDENT_CALLER, submission.id(), text, 540, null);
        return commands.submit(STUDENT_CALLER, submission.id());
    }

    // ---- infrastructure ----

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final class NoTransactions implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(@Nullable TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    }

    // ---- adapters ----

    static final class Submissions implements SubmissionRepository {
        final Map<UUID, Submission> byId = new LinkedHashMap<>();
        final Map<UUID, Integer> stored = new HashMap<>();
        final List<Evaluation> evaluations = new ArrayList<>();

        @Override
        public void insert(Submission submission) {
            byId.put(submission.id(), submission);
            stored.put(submission.id(), submission.version());
        }

        @Override
        public void update(Submission submission, int expectedVersion) {
            if (stored.get(submission.id()) != expectedVersion) {
                throw new ConcurrencyConflictException("submission changed");
            }
            stored.put(submission.id(), submission.version());
        }

        @Override
        public Optional<Submission> find(UUID submissionId) {
            return Optional.ofNullable(byId.get(submissionId));
        }

        @Override
        public List<Submission> list(UUID userId, SubmissionFilter filter, int offset, int limit) {
            return byId.values().stream()
                    .filter(s -> s.userId().equals(userId))
                    .filter(s -> filter.taskType() == null || s.taskType() == filter.taskType())
                    .filter(s -> filter.status() == null || s.status() == filter.status())
                    .filter(s ->
                            filter.weekTaskId() == null || filter.weekTaskId().equals(s.weekTaskId()))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public void saveEvaluation(Evaluation evaluation) {
            evaluations.add(evaluation);
        }

        @Override
        public void deleteAiEvaluation(UUID submissionId, int versionNo) {
            evaluations.removeIf(e -> e.submissionId().equals(submissionId)
                    && e.versionNo() == versionNo
                    && e.evaluator() == EvaluatorType.AI);
        }

        @Override
        public List<Evaluation> evaluations(UUID submissionId) {
            return evaluations.stream()
                    .filter(e -> e.submissionId().equals(submissionId))
                    .toList();
        }

        @Override
        public void updateAnnotations(UUID evaluationId, List<InlineAnnotation> annotations) {
            evaluations.replaceAll(e -> e.id().equals(evaluationId) ? e.withAnnotations(annotations) : e);
        }

        @Override
        public List<Double> firstVersionScores(UUID userId, TaskType type, int limit) {
            List<Double> scores = new ArrayList<>();
            List<Submission> own = byId.values().stream()
                    .filter(s -> s.userId().equals(userId) && s.taskType() == type)
                    .sorted(Comparator.comparing(Submission::createdAt).reversed())
                    .toList();
            for (Submission submission : own) {
                Evaluation effective = Evaluation.effective(evaluations(submission.id()).stream()
                        .filter(e -> e.versionNo() == 1)
                        .toList());
                if (effective != null) {
                    scores.add(effective.finalScore());
                }
            }
            return scores.stream().limit(limit).toList();
        }

        Evaluation ai(UUID submissionId, int versionNo) {
            return evaluations.stream()
                    .filter(e -> e.submissionId().equals(submissionId)
                            && e.versionNo() == versionNo
                            && e.evaluator() == EvaluatorType.AI)
                    .findFirst()
                    .orElseThrow();
        }
    }

    static final class Deferred implements DeferredEvaluationRepository {
        final Map<String, DeferredEvaluation> rows = new LinkedHashMap<>();

        static String key(UUID id, int no) {
            return id + "/" + no;
        }

        @Override
        public void save(DeferredEvaluation deferred) {
            rows.put(key(deferred.submissionId(), deferred.versionNo()), deferred);
        }

        @Override
        public Optional<DeferredEvaluation> find(UUID submissionId, int versionNo) {
            return Optional.ofNullable(rows.get(key(submissionId, versionNo)));
        }

        @Override
        public void delete(UUID submissionId, int versionNo) {
            rows.remove(key(submissionId, versionNo));
        }

        @Override
        public List<DeferredEvaluation> lockDue(Instant now, int limit) {
            return rows.values().stream().filter(r -> r.due(now)).limit(limit).toList();
        }

        @Override
        public List<DeferredEvaluation> page(int offset, int limit) {
            return rows.values().stream().skip(offset).limit(limit).toList();
        }
    }

    static final class Estimates implements EstimateInputsRepository {
        final Map<UUID, Map<Section, EstimateInputs.Ability>> abilities = new HashMap<>();
        final Map<UUID, List<Boolean>> bas = new HashMap<>();
        final Map<UUID, Instant> onboarded = new HashMap<>();
        final Map<UUID, BandEstimate> current = new HashMap<>();
        final Map<UUID, Map<LocalDate, BandEstimate>> weekly = new HashMap<>();
        final Set<UUID> attempts = new HashSet<>();

        @Override
        public void saveAbility(UUID userId, Section section, double theta, double se, int n, Instant at) {
            abilities
                    .computeIfAbsent(userId, k -> new EnumMap<>(Section.class))
                    .put(section, new EstimateInputs.Ability(theta, se, n));
        }

        @Override
        public void addBuildSentenceItems(UUID userId, UUID attemptId, List<Boolean> correct, Instant at) {
            if (attempts.add(attemptId)) {
                List<Boolean> items = new ArrayList<>(correct);
                items.addAll(bas.getOrDefault(userId, List.of()));
                bas.put(userId, items);
            }
        }

        @Override
        public void onboardingCompleted(UUID userId, Instant at) {
            onboarded.put(userId, at);
        }

        @Override
        public EstimateInputs load(UUID userId, Instant asOf) {
            return new EstimateInputs(
                    onboarded.get(userId),
                    abilities.getOrDefault(userId, Map.of()),
                    bas.getOrDefault(userId, List.of()),
                    List.of(),
                    List.of(),
                    asOf);
        }

        @Override
        public void saveEstimate(UUID userId, BandEstimate estimate, LocalDate weekStart) {
            current.put(userId, estimate);
            weekly.computeIfAbsent(userId, k -> new LinkedHashMap<>()).put(weekStart, estimate);
        }

        @Override
        public Optional<BandEstimate> current(UUID userId) {
            return Optional.ofNullable(current.get(userId));
        }

        @Override
        public List<WeeklyEstimate> history(UUID userId, int limit) {
            return weekly.getOrDefault(userId, Map.of()).entrySet().stream()
                    .map(e -> new WeeklyEstimate(e.getKey(), e.getValue()))
                    .limit(limit)
                    .toList();
        }
    }

    static final class Links implements TutorLinkRepository {
        final Set<String> pairs = new HashSet<>();

        @Override
        public boolean linked(UUID tutorId, UUID studentId) {
            return pairs.contains(tutorId + "/" + studentId);
        }

        @Override
        public void link(UUID tutorId, UUID studentId) {
            pairs.add(tutorId + "/" + studentId);
        }

        @Override
        public void unlink(UUID tutorId, UUID studentId) {
            pairs.remove(tutorId + "/" + studentId);
        }
    }

    record Published(
            String type,
            UUID submissionId,
            int versionNo,
            int attemptNo,
            @Nullable Evaluation evaluation) {}

    static final class Events implements AssessmentEventPublisherPort {
        final List<Published> published = new ArrayList<>();
        final List<BandEstimate> estimates = new ArrayList<>();

        @Override
        public void evaluationRequested(Submission submission, int versionNo, int attemptNo, Cause cause) {
            published.add(new Published("evaluation-requested", submission.id(), versionNo, attemptNo, null));
        }

        @Override
        public void submissionEvaluated(Submission submission, Evaluation evaluation, Cause cause) {
            published.add(
                    new Published("submission-evaluated", submission.id(), evaluation.versionNo(), 0, evaluation));
        }

        @Override
        public void bandEstimateUpdated(UUID userId, BandEstimate estimate, Cause cause) {
            estimates.add(estimate);
        }

        List<String> types() {
            return published.stream().map(Published::type).toList();
        }
    }

    static final class Content implements TaskContentPort {
        boolean down;

        @Override
        public Optional<TaskContent> load(UUID itemSetId) {
            if (down) {
                throw new com.toefly.assessment.application.port.out.DependencyUnavailableException(
                        "content-service", new RuntimeException("down"));
            }
            if (itemSetId.equals(DISCUSSION_SET)) {
                return Optional.of(new TaskContent(
                        DISCUSSION_SET,
                        1,
                        TaskType.WRITE_ACADEMIC_DISCUSSION,
                        "Professor: Should governments tax unhealthy food?",
                        List.of("Should governments tax unhealthy food to improve public health in the long run"),
                        List.of("Maya", "Lucas"),
                        0,
                        100,
                        600,
                        "Model answer",
                        "BOOK"));
            }
            if (itemSetId.equals(EMAIL_SET)) {
                return Optional.of(new TaskContent(
                        EMAIL_SET,
                        2,
                        TaskType.WRITE_EMAIL,
                        "Write to your manager",
                        List.of(),
                        List.of(),
                        3,
                        0,
                        420,
                        null,
                        null));
            }
            return Optional.empty();
        }
    }

    /** Answers per pass tag from queues; an exception in a queue is thrown. */
    static final class ScriptedAi implements AiGradingPort {
        final Map<String, Deque<Object>> answers = new ConcurrentHashMap<>();
        final List<GradingRequest> requests = java.util.Collections.synchronizedList(new ArrayList<>());

        void answer(String tag, Object... outputs) {
            answers.computeIfAbsent(tag, k -> new ArrayDeque<>()).addAll(List.of(outputs));
        }

        void unavailable(Duration retryAfter) {
            for (String tag : List.of("A", "B")) {
                answer(tag, new AiUnavailableException("429 ai-quota-exhausted", retryAfter, false));
            }
        }

        @Override
        public PassOutput grade(GradingRequest request) {
            requests.add(request);
            Deque<Object> queue = answers.get(request.passTag());
            Object next = queue == null ? null : queue.poll();
            if (next == null) {
                throw new AiUnavailableException("no scripted answer for " + request.passTag(), null, false);
            }
            if (next instanceof RuntimeException failure) {
                throw failure;
            }
            return (PassOutput) next;
        }

        long calls(String tag) {
            return requests.stream().filter(r -> r.passTag().equals(tag)).count();
        }
    }

    static final class Idempotency implements IdempotencyStorePort {
        final Map<String, Record> records = new HashMap<>();

        @Override
        public boolean claim(UUID userId, String key, String operation, String requestHash, Instant expiresAt) {
            return records.putIfAbsent(userId + key, new Record(operation, requestHash, false, null, null, null))
                    == null;
        }

        @Override
        public Optional<Record> find(UUID userId, String key) {
            return Optional.ofNullable(records.get(userId + key));
        }

        @Override
        public void complete(UUID userId, String key, int status, String body, @Nullable String resourceUri) {
            Record record = records.get(userId + key);
            records.put(
                    userId + key,
                    new Record(record.operation(), record.requestHash(), true, status, body, resourceUri));
        }

        @Override
        public void release(UUID userId, String key) {
            records.remove(userId + key);
        }
    }
}
