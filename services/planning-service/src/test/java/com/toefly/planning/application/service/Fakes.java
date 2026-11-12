package com.toefly.planning.application.service;

import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.application.port.out.ConcurrencyConflictException;
import com.toefly.planning.application.port.out.ItemSetCatalogPort;
import com.toefly.planning.application.port.out.LearnerSignalPort;
import com.toefly.planning.application.port.out.PlanningEventPublisherPort;
import com.toefly.planning.application.port.out.StudyPlanRepository;
import com.toefly.planning.application.port.out.UnitCatalogPort;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.domain.composition.CompositionRules;
import com.toefly.planning.domain.composition.ResourceLink;
import com.toefly.planning.domain.composition.UnitRef;
import com.toefly.planning.domain.composition.WeakTypes.Accuracy;
import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.Rebalance;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import com.toefly.planning.domain.week.WeekTaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/** In-memory ports and a clock that tests move by hand. */
final class Fakes {

    final MutableClock clock;
    final Plans plans = new Plans();
    final Weeks weeks = new Weeks();
    final Catalog catalog = new Catalog();
    final Units units = new Units();
    final Signals signals = new Signals();
    final Events events = new Events();
    final StudyPlanService planService;
    final AttemptTrackingService attempts;
    final WeekTaskService tasks;
    final PlanQueryService queries;
    final RolloverService rollover;
    final LearnerSyncService sync;

    static final Set<TaskType> CLOSED = EnumSet.of(
            TaskType.COMPLETE_THE_WORDS,
            TaskType.READ_DAILY_LIFE,
            TaskType.READ_ACADEMIC_PASSAGE,
            TaskType.LISTEN_CHOOSE_RESPONSE,
            TaskType.LISTEN_CONVERSATION,
            TaskType.LISTEN_ANNOUNCEMENT,
            TaskType.LISTEN_ACADEMIC_TALK,
            TaskType.BUILD_A_SENTENCE,
            TaskType.GRAMMAR_EXERCISE);

    Fakes(Instant start) {
        clock = new MutableClock(start);
        PlanningSettings settings = new PlanningSettings(
                CLOSED,
                false,
                false,
                "TOEFL_2026",
                List.of(
                        new ResourceLink(
                                "https://www.toeflresources.com/reading-section/",
                                "Reading",
                                Set.of(WeekSection.READING)),
                        new ResourceLink(
                                "https://www.toeflresources.com/listening-section/",
                                "Listening",
                                Set.of(WeekSection.LISTENING))));
        WeekPlanner planner = new WeekPlanner(weeks, catalog, units, signals, CompositionRules.standard(), settings);
        WeekLifecycle lifecycle = new WeekLifecycle(plans, weeks, planner, events);
        Transactions transactions = new Transactions(new NoTransactions());
        planService = new StudyPlanService(plans, weeks, planner, events, clock);
        attempts = new AttemptTrackingService(weeks, plans, signals, events, clock);
        tasks = new WeekTaskService(weeks, plans, events, lifecycle, clock);
        queries = new PlanQueryService(plans, weeks, clock);
        rollover = new RolloverService(plans, weeks, lifecycle, events, transactions, clock);
        sync = new LearnerSyncService(signals, catalog, plans, weeks);
    }

    // ---- clock ----

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
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

    static final class NoTransactions implements org.springframework.transaction.PlatformTransactionManager {
        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                org.springframework.transaction.TransactionDefinition definition) {
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status) {}

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status) {}
    }

    // ---- repositories ----

    static final class Plans implements StudyPlanRepository {
        final Map<UUID, StudyPlan> byId = new LinkedHashMap<>();

        @Override
        public Optional<StudyPlan> findByUser(UUID userId) {
            return byId.values().stream()
                    .filter(plan -> plan.userId().equals(userId))
                    .findFirst();
        }

        @Override
        public Optional<StudyPlan> findById(UUID planId) {
            return Optional.ofNullable(byId.get(planId));
        }

        @Override
        public void insert(StudyPlan plan) {
            byId.put(plan.id(), plan);
        }

        @Override
        public int update(StudyPlan plan) {
            plan.saved(plan.version() + 1);
            byId.put(plan.id(), plan);
            return plan.version();
        }

        @Override
        public void deleteByUser(UUID userId) {
            byId.values().removeIf(plan -> plan.userId().equals(userId));
        }
    }

    static final class Weeks implements WeekRepository {
        final Map<UUID, Week> byId = new LinkedHashMap<>();
        final Map<UUID, Integer> versions = new HashMap<>();

        @Override
        public Optional<Week> findById(UUID weekId) {
            return Optional.ofNullable(byId.get(weekId));
        }

        @Override
        public Optional<Week> findByTask(UUID weekTaskId) {
            return byId.values().stream()
                    .filter(week -> week.hasTask(weekTaskId))
                    .findFirst();
        }

        @Override
        public List<Week> findByPlan(UUID planId) {
            return byId.values().stream()
                    .filter(week -> week.planId().equals(planId))
                    .sorted(Comparator.comparingInt(Week::weekNo))
                    .toList();
        }

        @Override
        public List<UUID> plansWithOpenWeeks() {
            return byId.values().stream()
                    .filter(week -> week.status().open())
                    .map(Week::planId)
                    .distinct()
                    .toList();
        }

        @Override
        public void insert(Week week) {
            byId.put(week.id(), week);
            versions.put(week.id(), week.version());
        }

        @Override
        public int update(Week week) {
            if (!versions.get(week.id()).equals(week.version())) {
                throw new ConcurrencyConflictException("week " + week.id());
            }
            week.saved(week.version() + 1);
            versions.put(week.id(), week.version());
            return week.version();
        }

        @Override
        public Set<String> assignedRefs(UUID userId) {
            Set<String> refs = new HashSet<>();
            byId.values().stream()
                    .filter(week -> week.userId().equals(userId))
                    .flatMap(week -> week.tasks().stream())
                    .forEach(task -> {
                        if (task.refId() != null) {
                            refs.add(task.refId());
                        }
                    });
            return refs;
        }

        @Override
        public Map<TaskType, List<UUID>> doneItemSets(UUID userId) {
            Map<TaskType, List<UUID>> done = new EnumMap<>(TaskType.class);
            byId.values().stream()
                    .filter(week -> week.userId().equals(userId))
                    .flatMap(week -> week.tasks().stream())
                    .filter(task -> task.kind() == WeekTaskKind.ITEM_SET && task.status() == WeekTaskStatus.DONE)
                    .forEach(task -> done.computeIfAbsent(task.taskType(), type -> new ArrayList<>())
                            .add(UUID.fromString(task.refId())));
            return done;
        }

        @Override
        public Map<TaskType, Integer> typeCounts(UUID planId, int fromWeekNo) {
            Map<TaskType, Integer> counts = new EnumMap<>(TaskType.class);
            findByPlan(planId).stream()
                    .filter(week -> week.weekNo() >= fromWeekNo)
                    .flatMap(week -> week.tasks().stream())
                    .filter(task -> task.taskType() != null)
                    .forEach(task -> counts.merge(task.taskType(), 1, Integer::sum));
            return counts;
        }

        @Override
        public void deleteByUser(UUID userId) {
            byId.values().removeIf(week -> week.userId().equals(userId));
        }
    }

    static final class Catalog implements ItemSetCatalogPort {
        final Map<UUID, CatalogEntry> entries = new LinkedHashMap<>();
        final Set<UUID> hidden = new HashSet<>();

        Catalog() {
            for (TaskType type : TaskType.values()) {
                IntStream.range(0, 120)
                        .forEach(
                                i -> { // enough that the share of the bank (docs/03 §10.2) does not bind
                                    UUID id = UUID.randomUUID();
                                    entries.put(
                                            id,
                                            new CatalogEntry(
                                                    id,
                                                    1,
                                                    type,
                                                    type.legacy() ? "TOEFL_LEGACY" : "TOEFL_2026",
                                                    (i % 9 - 4) * 0.3,
                                                    CompositionRules.standard().minutesOf(type),
                                                    "topic " + i,
                                                    null));
                                });
            }
        }

        @Override
        public void published(CatalogEntry entry, Instant occurredAt) {
            entries.put(entry.itemSetId(), entry);
        }

        @Override
        public void hidden(UUID itemSetId, Instant occurredAt) {
            hidden.add(itemSetId);
        }

        @Override
        public List<CatalogEntry> visible(TaskType type) {
            return entries.values().stream()
                    .filter(entry -> entry.taskType() == type && !hidden.contains(entry.itemSetId()))
                    .toList();
        }
    }

    static final class Units implements UnitCatalogPort {
        final List<UnitRef> roundUp = units("Round-Up Unit");
        final List<UnitRef> grammar = units("Collins Grammar Unit");
        final List<UnitRef> vocabulary = units("Collins Vocabulary Unit");

        private static List<UnitRef> units(String prefix) {
            return IntStream.rangeClosed(1, 20)
                    .mapToObj(i -> new UnitRef(UUID.randomUUID(), prefix + " " + i))
                    .toList();
        }

        @Override
        public List<UnitRef> roundUp() {
            return roundUp;
        }

        @Override
        public List<UnitRef> collinsGrammar() {
            return grammar;
        }

        @Override
        public List<UnitRef> collinsVocabulary() {
            return vocabulary;
        }
    }

    static final class Signals implements LearnerSignalPort {
        record Result(UUID userId, TaskType type, int correct, int total, Instant at) {}

        final List<Result> results = new ArrayList<>();
        final Map<UUID, Map<WeekSection, Double>> theta = new HashMap<>();

        @Override
        public void recordResult(UUID userId, UUID attemptId, TaskType type, int correct, int total, Instant scoredAt) {
            results.add(new Result(userId, type, correct, total, scoredAt));
        }

        @Override
        public Map<TaskType, Accuracy> accuracySince(UUID userId, Instant since) {
            Map<TaskType, Accuracy> accuracy = new EnumMap<>(TaskType.class);
            for (Result result : results) {
                if (result.userId().equals(userId) && !result.at().isBefore(since)) {
                    accuracy.merge(
                            result.type(),
                            new Accuracy(result.correct(), result.total()),
                            (a, b) -> new Accuracy(a.correct() + b.correct(), a.total() + b.total()));
                }
            }
            return accuracy;
        }

        @Override
        public void recordTheta(UUID userId, WeekSection section, double value, Instant occurredAt) {
            theta.computeIfAbsent(userId, id -> new EnumMap<>(WeekSection.class))
                    .put(section, value);
        }

        @Override
        public Map<WeekSection, Double> theta(UUID userId) {
            return theta.getOrDefault(userId, Map.of());
        }

        @Override
        public void deleteByUser(UUID userId) {
            results.removeIf(result -> result.userId().equals(userId));
            theta.remove(userId);
        }
    }

    static final class Events implements PlanningEventPublisherPort {
        record Published(String topic, UUID weekId, Object detail) {}

        final List<Published> published = new ArrayList<>();

        List<Published> of(String topic) {
            return published.stream()
                    .filter(event -> event.topic().equals(topic))
                    .toList();
        }

        @Override
        public List<String> topics() {
            return List.of();
        }

        @Override
        public void studyPlanCreated(StudyPlan plan, Cause cause) {
            published.add(new Published("study-plan-created", plan.id(), plan.weeksTotal()));
        }

        @Override
        public void weekPublished(Week week, Cause cause) {
            published.add(new Published("week-published", week.id(), week.weekNo()));
        }

        @Override
        public void weekTaskCompleted(Week week, WeekTask task, LocalDate localDate, Cause cause) {
            published.add(new Published("week-task-completed", week.id(), task.id()));
        }

        @Override
        public void weekRebalanced(Week week, Rebalance rebalance, Instant occurredAt, Cause cause) {
            published.add(new Published("week-rebalanced", week.id(), rebalance));
        }

        @Override
        public void weekCompleted(Week week, Cause cause) {
            published.add(new Published("week-completed", week.id(), week.closedBy()));
        }
    }
}
