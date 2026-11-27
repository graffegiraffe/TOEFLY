package com.toefly.analytics.application.service;

import com.toefly.analytics.application.port.in.RebuildJob;
import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.EventMeta;
import com.toefly.analytics.application.port.out.ActivityRepository;
import com.toefly.analytics.application.port.out.AiUsageRepository;
import com.toefly.analytics.application.port.out.DashboardRepository;
import com.toefly.analytics.application.port.out.EventJournalRepository;
import com.toefly.analytics.application.port.out.LearnerRepository;
import com.toefly.analytics.application.port.out.ProductMetricsRepository;
import com.toefly.analytics.application.port.out.RebuildJobRepository;
import com.toefly.analytics.domain.activity.ActivityDay;
import com.toefly.analytics.domain.activity.PlannedTask;
import com.toefly.analytics.domain.dashboard.BandPoint;
import com.toefly.analytics.domain.dashboard.StudentDashboard;
import com.toefly.analytics.domain.dashboard.WeekSummary;
import com.toefly.analytics.domain.learner.LearnerSettings;
import com.toefly.analytics.domain.metrics.AiUsage;
import com.toefly.analytics.domain.metrics.LearnerMilestones;
import com.toefly.analytics.domain.metrics.ProductCounters;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;

/** In-memory adapters: the whole write and read side of the service without a database. */
final class Fakes {

    final Journal journal = new Journal();
    final Learners learners = new Learners();
    final Dashboards dashboards = new Dashboards();
    final Activity activity = new Activity();
    final Metrics metrics = new Metrics();
    final Usage usage = new Usage();
    final Jobs jobs = new Jobs();
    final MutableClock clock = new MutableClock(Instant.parse("2026-11-20T10:00:00Z"));

    final Projections projections = new Projections(
            new LearnerProjection(learners),
            new StudentDashboardProjection(dashboards),
            new ActivityProjection(activity, learners),
            new ProductMetricsProjection(metrics),
            new AiUsageProjection(usage));
    final EventRecorder recorder = new EventRecorder(journal, projections, learners, dashboards, activity, metrics);
    final ProjectionReplayer replayer = new ProjectionReplayer(journal, projections);
    final DashboardQueryService dashboardQuery = new DashboardQueryService(dashboards, activity, learners, clock);
    final ActivityQueryService activityQuery = new ActivityQueryService(activity, learners, dashboards, clock);
    final AdminMetricsService adminMetrics = new AdminMetricsService(metrics, activity, usage, clock);

    boolean record(AnalyticsEvent event) {
        return record(UUID.randomUUID(), event);
    }

    boolean record(UUID eventId, AnalyticsEvent event) {
        return recorder.record(
                new EventMeta(eventId, event.getClass().getSimpleName(), event.at(), event.userId()), event);
    }

    /** Every table of every read model, to compare an incremental run with a rebuild. */
    Map<String, Object> snapshot() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("learners", new TreeMap<>(learners.rows));
        state.put("dashboards", new TreeMap<>(dashboards.rows));
        state.put("weeks", new TreeMap<>(dashboards.weeks));
        state.put("bands", new TreeMap<>(dashboards.bands));
        state.put("days", new TreeMap<>(activity.days));
        state.put("norms", new TreeMap<>(activity.norms));
        state.put("tasks", new TreeMap<>(activity.tasks));
        state.put("streaks", new TreeMap<>(activity.study));
        state.put("milestones", new TreeMap<>(metrics.milestones));
        state.put("counters", new TreeMap<>(metrics.counters));
        state.put("usage", new TreeMap<>(usage.rows));
        return state;
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant at) {
            now = at;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static class Journal implements EventJournalRepository {

        final Map<UUID, Entry> entries = new LinkedHashMap<>();
        int locks;

        record Entry(EventMeta meta, AnalyticsEvent event) {}

        @Override
        public boolean append(EventMeta meta, AnalyticsEvent event) {
            return entries.putIfAbsent(meta.eventId(), new Entry(meta, event)) == null;
        }

        @Override
        public void lockForReplay() {
            locks++;
        }

        @Override
        public long replay(Consumer<AnalyticsEvent> sink) {
            List<Entry> ordered = new ArrayList<>(entries.values());
            ordered.sort(Comparator.comparing((Entry e) -> e.meta().occurredAt())
                    .thenComparing(e -> e.meta().eventId()));
            ordered.forEach(e -> sink.accept(e.event()));
            return ordered.size();
        }

        @Override
        public void deleteUser(UUID userId) {
            entries.values().removeIf(e -> userId.equals(e.meta().userId()));
        }

        @Override
        public void ensurePartitions(YearMonth from, YearMonth to) {}

        @Override
        public void deleteBefore(Instant before) {
            entries.values().removeIf(e -> e.meta().occurredAt().isBefore(before));
        }
    }

    static final class Learners implements LearnerRepository {

        final Map<UUID, LearnerSettings> rows = new HashMap<>();

        @Override
        public Optional<LearnerSettings> find(UUID userId) {
            return Optional.ofNullable(rows.get(userId));
        }

        @Override
        public void save(LearnerSettings settings) {
            rows.put(settings.userId(), settings);
        }

        @Override
        public void reset() {
            rows.clear();
        }

        @Override
        public void deleteUser(UUID userId) {
            rows.remove(userId);
        }
    }

    static final class Dashboards implements DashboardRepository {

        final Map<UUID, StudentDashboard> rows = new HashMap<>();
        final Map<UUID, WeekSummary> weeks = new HashMap<>();
        final Map<UUID, BandPoint> bands = new HashMap<>();
        final Map<UUID, UUID> bandOwners = new HashMap<>();

        @Override
        public Optional<StudentDashboard> find(UUID userId) {
            return Optional.ofNullable(rows.get(userId));
        }

        @Override
        public void save(StudentDashboard dashboard) {
            rows.put(dashboard.userId(), dashboard);
        }

        @Override
        public Optional<WeekSummary> findWeek(UUID weekId) {
            return Optional.ofNullable(weeks.get(weekId));
        }

        @Override
        public void saveWeek(WeekSummary week) {
            weeks.put(week.weekId(), week);
        }

        @Override
        public List<WeekSummary> weeks(UUID userId) {
            return weeks.values().stream()
                    .filter(w -> w.userId().equals(userId))
                    .sorted(Comparator.comparingInt(WeekSummary::weekNo))
                    .toList();
        }

        @Override
        public void addBandPoint(UUID userId, BandPoint point) {
            bands.putIfAbsent(point.eventId(), point);
            bandOwners.put(point.eventId(), userId);
        }

        @Override
        public List<BandPoint> bandPoints(UUID userId) {
            return bands.values().stream()
                    .filter(p -> userId.equals(bandOwners.get(p.eventId())))
                    .sorted(Comparator.comparing(BandPoint::at))
                    .toList();
        }

        @Override
        public void reset() {
            rows.clear();
            weeks.clear();
            bands.clear();
            bandOwners.clear();
        }

        @Override
        public void deleteUser(UUID userId) {
            rows.remove(userId);
            weeks.values().removeIf(w -> w.userId().equals(userId));
            bands.keySet().removeIf(id -> userId.equals(bandOwners.get(id)));
        }
    }

    static final class Activity implements ActivityRepository {

        final Map<String, ActivityDay> days = new HashMap<>();
        final Map<UUID, Integer> norms = new HashMap<>();
        final Map<UUID, UUID> normOwners = new HashMap<>();
        final Map<UUID, PlannedTask> tasks = new HashMap<>();
        final Map<UUID, Set<LocalDate>> study = new HashMap<>();
        final Map<UUID, Instant> streakAsOf = new HashMap<>();

        @Override
        public Optional<ActivityDay> findDay(UUID userId, LocalDate date) {
            return Optional.ofNullable(days.get(key(userId, date)));
        }

        @Override
        public void saveDay(ActivityDay day) {
            days.put(key(day.userId(), day.date()), day);
        }

        @Override
        public List<ActivityDay> days(UUID userId, LocalDate from, LocalDate to) {
            return days.values().stream()
                    .filter(d -> d.userId().equals(userId)
                            && !d.date().isBefore(from)
                            && d.date().isBefore(to))
                    .sorted(Comparator.comparing(ActivityDay::date))
                    .toList();
        }

        @Override
        public void saveAttemptNorm(UUID attemptId, UUID userId, int normSeconds) {
            norms.put(attemptId, normSeconds);
            normOwners.put(attemptId, userId);
        }

        @Override
        public Optional<Integer> attemptNorm(UUID attemptId) {
            return Optional.ofNullable(norms.get(attemptId));
        }

        @Override
        public Optional<PlannedTask> plannedTask(UUID weekTaskId) {
            return Optional.ofNullable(tasks.get(weekTaskId));
        }

        @Override
        public List<PlannedTask> plannedTasks(UUID userId, LocalDate plannedDate) {
            return tasks.values().stream()
                    .filter(t -> t.userId().equals(userId) && t.plannedDate().equals(plannedDate))
                    .toList();
        }

        @Override
        public void savePlannedTask(PlannedTask task) {
            tasks.put(task.weekTaskId(), task);
        }

        @Override
        public void addStudyDay(UUID userId, LocalDate day, Instant at) {
            study.computeIfAbsent(userId, id -> new TreeSet<>()).add(day);
            streakAsOf.merge(userId, at, (a, b) -> a.isAfter(b) ? a : b);
        }

        @Override
        public Set<LocalDate> studyDays(UUID userId) {
            return study.getOrDefault(userId, Set.of());
        }

        @Override
        public Map<UUID, Set<LocalDate>> studyDaysSince(LocalDate since) {
            Map<UUID, Set<LocalDate>> result = new HashMap<>();
            days.values().stream()
                    .filter(d -> d.goalMet() && !d.date().isBefore(since))
                    .forEach(d -> result.computeIfAbsent(d.userId(), id -> new TreeSet<>())
                            .add(d.date()));
            return result;
        }

        @Override
        public Optional<Instant> streakAsOf(UUID userId) {
            return Optional.ofNullable(streakAsOf.get(userId));
        }

        @Override
        public int activeLearners(LocalDate from, LocalDate to) {
            return (int) days.values().stream()
                    .filter(d -> d.activeSeconds() > 0
                            && !d.date().isBefore(from)
                            && d.date().isBefore(to))
                    .map(ActivityDay::userId)
                    .distinct()
                    .count();
        }

        @Override
        public void reset() {
            days.clear();
            norms.clear();
            normOwners.clear();
            tasks.clear();
            study.clear();
            streakAsOf.clear();
        }

        @Override
        public void deleteUser(UUID userId) {
            days.values().removeIf(d -> d.userId().equals(userId));
            norms.keySet().removeIf(id -> userId.equals(normOwners.get(id)));
            tasks.values().removeIf(t -> t.userId().equals(userId));
            study.remove(userId);
            streakAsOf.remove(userId);
        }

        private static String key(UUID userId, LocalDate date) {
            return userId + "/" + date;
        }
    }

    static final class Metrics implements ProductMetricsRepository {

        final Map<UUID, LearnerMilestones> milestones = new HashMap<>();
        final Map<LocalDate, ProductCounters> counters = new HashMap<>();

        @Override
        public Optional<LearnerMilestones> milestones(UUID userId) {
            return Optional.ofNullable(milestones.get(userId));
        }

        @Override
        public void saveMilestones(LearnerMilestones m) {
            milestones.put(m.userId(), m);
        }

        @Override
        public List<LearnerMilestones> allMilestones() {
            return List.copyOf(milestones.values());
        }

        @Override
        public void add(LocalDate day, ProductCounters.Counter counter, int delta) {
            counters.put(
                    day, counters.getOrDefault(day, ProductCounters.empty(day)).plus(counter, delta));
        }

        @Override
        public List<ProductCounters> counters(LocalDate from, LocalDate to) {
            return counters.values().stream()
                    .filter(c -> !c.day().isBefore(from) && c.day().isBefore(to))
                    .sorted(Comparator.comparing(ProductCounters::day))
                    .toList();
        }

        @Override
        public void reset() {
            milestones.clear();
            counters.clear();
        }

        @Override
        public void deleteUser(UUID userId) {
            milestones.remove(userId);
        }
    }

    static final class Usage implements AiUsageRepository {

        final Map<String, AiUsage> rows = new HashMap<>();

        @Override
        public void add(AiUsage u) {
            rows.merge(u.day() + "/" + u.provider() + "/" + u.model() + "/" + u.promptKey(), u, AiUsage::plus);
        }

        @Override
        public List<AiUsage> rows(LocalDate from, LocalDate to) {
            return rows.values().stream()
                    .filter(r -> !r.day().isBefore(from) && r.day().isBefore(to))
                    .toList();
        }

        @Override
        public void reset() {
            rows.clear();
        }
    }

    static final class Jobs implements RebuildJobRepository {

        final Map<UUID, RebuildJob> rows = new HashMap<>();
        final Map<String, UUID> keys = new HashMap<>();

        @Override
        public boolean start(RebuildJob job, UUID idempotencyKey) {
            boolean running = rows.values().stream()
                    .anyMatch(j -> j.projection() == job.projection() && j.status() == RebuildJob.Status.RUNNING);
            if (running) {
                return false;
            }
            rows.put(job.id(), job);
            keys.put(job.requestedBy() + "/" + idempotencyKey, job.id());
            return true;
        }

        @Override
        public void update(RebuildJob job) {
            rows.put(job.id(), job);
        }

        @Override
        public Optional<RebuildJob> find(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<RebuildJob> findByKey(UUID adminId, UUID idempotencyKey) {
            return Optional.ofNullable(keys.get(adminId + "/" + idempotencyKey)).map(rows::get);
        }

        @Override
        public void failRunning(String reason) {
            rows.replaceAll((id, job) -> job.status() == RebuildJob.Status.RUNNING
                    ? job.failed(reason, Objects.requireNonNull(job.requestedAt()))
                    : job);
        }
    }
}
