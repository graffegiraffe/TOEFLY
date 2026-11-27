package com.toefly.planning.domain.week;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * A week of the plan with its tasks (docs/03-domain-model.md §6.8, §10). Every rule takes the learner's local date
 * from the caller; the aggregate never reads a clock.
 */
public final class Week {

    public static final int DAYS = 7;
    public static final int MAX_DAYS = 10;
    public static final double CLOSE_PROGRESS = 0.70;
    public static final double REBALANCE_LAG_DAYS = 3;
    public static final Duration REBALANCE_INTERVAL = Duration.ofHours(48);
    public static final Duration ABANDONED_AFTER = Duration.ofDays(7);
    /** Extra tasks of Toefly Tutor in one week (docs/06-ai-agent.md §4.5). */
    public static final int MAX_AGENT_TASKS = 3;
    /** The load a day may reach with an extra task: the tolerance of a day (AC-04.03.1). */
    public static final double EXTRA_DAY_LOAD = 1.25;

    private final UUID id;
    private final UUID planId;
    private final UUID userId;
    private final int weekNo;
    private final int minutesPerDay;
    private final boolean mockExam;
    private final List<TaskType> weakTypes;
    private @Nullable String rationale;
    private Instant generatedAt;
    private DraftInfo draft;
    private final List<WeekTask> tasks;
    private WeekStatus status;
    private LocalDate day1;
    private @Nullable Instant publishedAt;
    private @Nullable Instant completedAt;
    private @Nullable LocalDate closedOn;
    private @Nullable WeekCloseReason closedBy;
    private @Nullable Instant rebalancedAt;
    private int rebalanceCount;
    private List<UUID> rebalanceSkipped;
    private @Nullable LocalDate lastRolloverDate;
    private int version;

    public Week(
            UUID id,
            UUID planId,
            UUID userId,
            int weekNo,
            WeekStatus status,
            LocalDate day1,
            int minutesPerDay,
            boolean mockExam,
            List<TaskType> weakTypes,
            @Nullable String rationale,
            Instant generatedAt,
            @Nullable Instant publishedAt,
            @Nullable Instant completedAt,
            @Nullable LocalDate closedOn,
            @Nullable WeekCloseReason closedBy,
            @Nullable Instant rebalancedAt,
            int rebalanceCount,
            List<UUID> rebalanceSkipped,
            @Nullable LocalDate lastRolloverDate,
            int version,
            List<WeekTask> tasks) {
        this(
                id,
                planId,
                userId,
                weekNo,
                status,
                day1,
                minutesPerDay,
                mockExam,
                weakTypes,
                rationale,
                generatedAt,
                publishedAt,
                completedAt,
                closedOn,
                closedBy,
                rebalancedAt,
                rebalanceCount,
                rebalanceSkipped,
                lastRolloverDate,
                version,
                tasks,
                DraftInfo.rules(List.of()));
    }

    public Week(
            UUID id,
            UUID planId,
            UUID userId,
            int weekNo,
            WeekStatus status,
            LocalDate day1,
            int minutesPerDay,
            boolean mockExam,
            List<TaskType> weakTypes,
            @Nullable String rationale,
            Instant generatedAt,
            @Nullable Instant publishedAt,
            @Nullable Instant completedAt,
            @Nullable LocalDate closedOn,
            @Nullable WeekCloseReason closedBy,
            @Nullable Instant rebalancedAt,
            int rebalanceCount,
            List<UUID> rebalanceSkipped,
            @Nullable LocalDate lastRolloverDate,
            int version,
            List<WeekTask> tasks,
            DraftInfo draft) {
        if (weekNo < 1) {
            throw new IllegalArgumentException("weekNo must be positive");
        }
        if (minutesPerDay < 1) {
            throw new IllegalArgumentException("minutesPerDay must be positive");
        }
        this.id = Objects.requireNonNull(id);
        this.planId = Objects.requireNonNull(planId);
        this.userId = Objects.requireNonNull(userId);
        this.weekNo = weekNo;
        this.status = Objects.requireNonNull(status);
        this.day1 = Objects.requireNonNull(day1);
        this.minutesPerDay = minutesPerDay;
        this.mockExam = mockExam;
        this.weakTypes = List.copyOf(weakTypes);
        this.rationale = rationale;
        this.generatedAt = Objects.requireNonNull(generatedAt);
        this.publishedAt = publishedAt;
        this.completedAt = completedAt;
        this.closedOn = closedOn;
        this.closedBy = closedBy;
        this.rebalancedAt = rebalancedAt;
        this.rebalanceCount = rebalanceCount;
        this.rebalanceSkipped = List.copyOf(rebalanceSkipped);
        this.lastRolloverDate = lastRolloverDate;
        this.version = version;
        this.tasks = new ArrayList<>(tasks);
        this.draft = Objects.requireNonNull(draft);
    }

    /**
     * A week for a learner without a tutor: composed and published at once (AC-04.01.2, AC-04.07.1). Tasks are
     * spread over days 1–7 starting at {@code day1}.
     */
    public static Week publish(
            UUID planId,
            UUID userId,
            int weekNo,
            LocalDate day1,
            int minutesPerDay,
            boolean mockExam,
            List<TaskType> weakTypes,
            @Nullable String rationale,
            List<WeekTask> tasks,
            Instant now) {
        return publish(
                planId,
                userId,
                weekNo,
                day1,
                minutesPerDay,
                mockExam,
                weakTypes,
                rationale,
                tasks,
                now,
                DraftInfo.rules(List.of()));
    }

    /** {@link #publish} with the origin of the tasks. */
    public static Week publish(
            UUID planId,
            UUID userId,
            int weekNo,
            LocalDate day1,
            int minutesPerDay,
            boolean mockExam,
            List<TaskType> weakTypes,
            @Nullable String rationale,
            List<WeekTask> tasks,
            Instant now,
            DraftInfo draft) {
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("A published week has at least one task (INV-WK-03)");
        }
        Week week = new Week(
                UUID.randomUUID(),
                planId,
                userId,
                weekNo,
                WeekStatus.PUBLISHED,
                day1,
                minutesPerDay,
                mockExam,
                weakTypes,
                rationale,
                now,
                now,
                null,
                null,
                null,
                null,
                0,
                List.of(),
                null,
                0,
                tasks,
                draft);
        DayDistributor.distribute(day1, minutesPerDay, 1, DAYS, List.of(), week.tasks);
        DayDistributor.order(week.tasks);
        return week;
    }

    // ---- the draft of WeekGenerator (docs/06-ai-agent.md §5.2) ----

    /**
     * The week of the rules may still follow a weekly review: it is published, nobody started a task in it, and no
     * review has drafted it yet. A week is drafted by the AI at most once.
     */
    public boolean redraftable() {
        return status == WeekStatus.PUBLISHED
                && draft.source() == DraftSource.RULES
                && tasks.stream().allMatch(task -> task.status() == WeekTaskStatus.PENDING && task.attemptId() == null);
    }

    /**
     * Replaces the tasks of a week that has not started with the draft of WeekGenerator or of its fallback; the id,
     * the number and the dates stay, so links to the week keep working.
     *
     * @param laidOut the tasks already carry their days (a valid AI draft); otherwise they are distributed here
     */
    public void redraft(List<WeekTask> drafted, boolean laidOut, @Nullable String why, DraftInfo info, Instant now) {
        if (!redraftable()) {
            throw new WeekRuleException("week-not-editable", "Week " + weekNo + " has started or was drafted already");
        }
        if (drafted.isEmpty()) {
            throw new IllegalArgumentException("A published week has at least one task (INV-WK-03)");
        }
        tasks.clear();
        tasks.addAll(drafted);
        if (!laidOut) {
            DayDistributor.distribute(day1, minutesPerDay, 1, DAYS, List.of(), tasks);
        }
        DayDistributor.order(tasks);
        rationale = why;
        draft = info;
        generatedAt = now;
        publishedAt = now;
    }

    // ---- days and progress ----

    /** Number of the day of {@code date} in this week: 1 for day 1, 8–10 for the extension. */
    public int dayNo(LocalDate date) {
        return (int) ChronoUnit.DAYS.between(day1, date) + 1;
    }

    public LocalDate date(int dayNo) {
        return day1.plusDays(dayNo - 1L);
    }

    /** Days shown for the week: 7, up to 10 while it is extended, the actual length once closed. */
    public int dayCount(LocalDate today) {
        int last = closedOn != null ? dayNo(closedOn) : dayNo(today);
        int used = tasks.stream().mapToInt(WeekTask::dayNo).max().orElse(DAYS);
        return Math.max(DAYS, Math.min(MAX_DAYS, Math.max(last, used)));
    }

    /** Share of done estimated time, without the tasks excluded by a rebalance, the tutor or the plan (§10.6). */
    public double progress() {
        double total = 0;
        double done = 0;
        for (WeekTask task : tasks) {
            if (!task.countsInProgress()) {
                continue;
            }
            total += task.estimatedMinutes();
            if (task.status() == WeekTaskStatus.DONE) {
                done += task.estimatedMinutes();
            }
        }
        return total == 0 ? 0 : done / total;
    }

    public int progressPercent() {
        return (int) Math.floor(100 * progress() + 1e-9);
    }

    /** Open tasks of the days before {@code today}; their day does not change (AC-04.04.1). */
    public List<WeekTask> overdue(LocalDate today) {
        int dayNo = dayNo(today);
        return tasks.stream()
                .filter(task -> task.status().open() && task.dayNo() < dayNo)
                .sorted(Comparator.comparingInt(WeekTask::sortOrder))
                .toList();
    }

    /** Tasks planned on the day of {@code date}, without skipped ones. */
    public List<WeekTask> planned(LocalDate date) {
        int dayNo = dayNo(date);
        return tasks.stream()
                .filter(task -> task.dayNo() == dayNo && task.status() != WeekTaskStatus.SKIPPED)
                .sorted(Comparator.comparingInt(WeekTask::sortOrder))
                .toList();
    }

    /** {@code Σ estimatedMinutes(overdue) / minutesPerDay} (AC-04.04.2). */
    public double lagDays(LocalDate today) {
        return overdue(today).stream().mapToDouble(WeekTask::estimatedMinutes).sum() / minutesPerDay;
    }

    // ---- commands of the learner and of events ----

    /** The learner opened a task: the attempt runs and the week is in progress (AC-04.06.4). */
    public boolean start(UUID taskId, UUID attemptId, Instant now) {
        WeekTask task = task(taskId);
        if (status == WeekStatus.COMPLETED || task.status() == WeekTaskStatus.DONE) {
            return false; // a closed week and a done task do not change (INV-WK-06, AC-06.01.7)
        }
        task.start(attemptId, now);
        begin();
        return true;
    }

    /** A task is done; empty when nothing changed (a repeat, a closed week). */
    public Optional<WeekTask> complete(UUID taskId, @Nullable UUID attemptId, LocalDate today, Instant now) {
        WeekTask task = task(taskId);
        if (status == WeekStatus.COMPLETED || task.status() == WeekTaskStatus.DONE) {
            return Optional.empty();
        }
        task.complete(attemptId, today, now);
        begin();
        return Optional.of(task);
    }

    /** "Mark as done" of a unit, a material or a resource (AC-04.03.3). */
    public Optional<WeekTask> completeManually(UUID taskId, LocalDate today, Instant now) {
        WeekTask task = task(taskId);
        requireOpen();
        if (!task.kind().manuallyCompletable()) {
            throw new WeekRuleException(
                    "week-task-not-completable", "A task of kind " + task.kind() + " closes by itself");
        }
        return complete(taskId, null, today, now);
    }

    /** Removes the mark of an external resource; other done tasks stay done (INV-WK-05). */
    public WeekTask uncomplete(UUID taskId) {
        WeekTask task = task(taskId);
        requireOpen();
        if (task.status() != WeekTaskStatus.DONE) {
            return task;
        }
        if (task.kind() != WeekTaskKind.EXTERNAL_RESOURCE) {
            throw new WeekRuleException("conflict", "Only an external resource can be unmarked");
        }
        task.reopen();
        return task;
    }

    public WeekTask skip(UUID taskId) {
        WeekTask task = task(taskId);
        requireOpen();
        if (task.status().open()) {
            task.skip(WeekTaskSkipReason.USER_SKIPPED);
        }
        return task;
    }

    /** Moves a task to another day of the week; the rules of the close do not change (AC-04.03.5). */
    public WeekTask move(UUID taskId, int dayNo) {
        WeekTask task = task(taskId);
        requireOpen();
        if (dayNo < 1 || dayNo > MAX_DAYS) {
            throw new WeekRuleException("validation-failed", "dayIndex must be 1 to 10");
        }
        task.moveTo(dayNo);
        DayDistributor.order(tasks);
        return task;
    }

    /**
     * An extra task of Toefly Tutor ({@code assignExtraTask}, docs/06-ai-agent.md §4.5): only in an open week, at most
     * {@value #MAX_AGENT_TASKS} a week, not the same set twice, on a day that keeps its load within 1.25 × the daily
     * minutes and at most 2 tasks of one type (AC-04.03.1).
     */
    public WeekTask addAgentTask(WeekTask task, ExtraTaskDay day, LocalDate today) {
        requireOpen();
        if (task.origin() != TaskOrigin.TUTOR_AGENT) {
            throw new IllegalArgumentException("An extra task of the agent has the origin TUTOR_AGENT");
        }
        if (tasks.stream()
                        .filter(other -> other.origin() == TaskOrigin.TUTOR_AGENT)
                        .count()
                >= MAX_AGENT_TASKS) {
            throw new WeekRuleException(
                    "extra-task-limit-reached",
                    "Toefly Tutor already added " + MAX_AGENT_TASKS + " tasks to week " + weekNo);
        }
        if (task.refId() != null
                && tasks.stream().anyMatch(other -> task.refId().equals(other.refId()))) {
            throw new WeekRuleException("item-set-already-in-week", "The set is already in week " + weekNo);
        }
        int todayNo = Math.max(1, dayNo(today));
        int lastDay = todayNo > DAYS ? MAX_DAYS : DAYS;
        int first = day == ExtraTaskDay.TOMORROW ? todayNo + 1 : todayNo;
        int last = day == ExtraTaskDay.NEXT_FREE_DAY ? lastDay : first;
        if (first > lastDay) {
            throw new WeekRuleException("validation-failed", "Day " + first + " is outside week " + weekNo);
        }
        for (int candidate = first; candidate <= last; candidate++) {
            if (fits(candidate, task)) {
                task.moveTo(candidate);
                tasks.add(task);
                DayDistributor.order(tasks);
                return task;
            }
        }
        throw new WeekRuleException(
                "day-budget-exceeded",
                "No day from " + date(first) + " has room for " + Math.round(task.estimatedMinutes())
                        + " more minutes");
    }

    private boolean fits(int day, WeekTask task) {
        double load = 0;
        int sameType = 0;
        for (WeekTask other : tasks) {
            if (other.dayNo() != day || other.status() == WeekTaskStatus.SKIPPED) {
                continue;
            }
            load += other.estimatedMinutes();
            if (task.taskType() != null && task.taskType() == other.taskType()) {
                sameType++;
            }
        }
        return load + task.estimatedMinutes() <= EXTRA_DAY_LOAD * minutesPerDay + 1e-9 && sameType < 2;
    }

    /** Tasks whose attempt ran 7 days without a score go back to PENDING (attempt ABANDONED). */
    public List<WeekTask> releaseAbandoned(Instant now) {
        List<WeekTask> released = new ArrayList<>();
        for (WeekTask task : tasks) {
            if (task.status() == WeekTaskStatus.IN_PROGRESS
                    && task.startedAt() != null
                    && !task.startedAt().plus(ABANDONED_AFTER).isAfter(now)) {
                task.release();
                released.add(task);
            }
        }
        return released;
    }

    // ---- rebalance (§10.5) ----

    /**
     * Rebuilds the rest of a lagging week. The nightly check calls it with {@code manual = false} and gets empty
     * unless the lag is over 3 days and the last rebalance is 48 hours old; a manual call fails inside 48 hours.
     */
    public Optional<Rebalance> rebalance(LocalDate today, Instant now, boolean manual) {
        if (!status.open()) {
            if (manual) {
                throw new WeekRuleException("week-not-editable", "Only an open week can be rebalanced");
            }
            return Optional.empty();
        }
        double lag = lagDays(today);
        if (!manual && (status != WeekStatus.IN_PROGRESS || lag <= REBALANCE_LAG_DAYS)) {
            return Optional.empty();
        }
        if (rebalancedAt != null && now.isBefore(rebalancedAt.plus(REBALANCE_INTERVAL))) {
            if (manual) {
                throw new RebalanceTooSoonException(rebalancedAt.plus(REBALANCE_INTERVAL));
            }
            return Optional.empty();
        }
        int todayNo = Math.max(1, dayNo(today));
        int endDay = todayNo > DAYS ? MAX_DAYS : DAYS;
        int remainingDays = Math.max(1, endDay - todayNo + 1);
        int firstDay = Math.min(todayNo, endDay);
        double doneToday = tasks.stream()
                .filter(task -> task.status() == WeekTaskStatus.DONE && today.equals(task.completedOn()))
                .mapToDouble(WeekTask::estimatedMinutes)
                .sum();
        double capacity = remainingDays * (double) minutesPerDay - doneToday;
        List<WeekTask> pending =
                tasks.stream().filter(task -> task.status().open()).toList();
        double need = pending.stream().mapToDouble(WeekTask::estimatedMinutes).sum() - capacity;

        List<UUID> skipped = new ArrayList<>();
        if (need > 1e-9) {
            for (WeekTask task : exclusionOrder(pending)) {
                if (need <= 1e-9) {
                    break;
                }
                task.skip(WeekTaskSkipReason.REBALANCE);
                skipped.add(task.id());
                need -= task.estimatedMinutes();
            }
        }
        List<WeekTask> movable = tasks.stream()
                .filter(task -> task.status() == WeekTaskStatus.PENDING
                        && task.kind() != WeekTaskKind.MOCK_EXAM
                        && task.kind() != WeekTaskKind.VOCABULARY_REVIEW)
                .toList();
        Map<UUID, Integer> before = movable.stream().collect(Collectors.toMap(WeekTask::id, WeekTask::dayNo));
        List<WeekTask> fixed = tasks.stream()
                .filter(task -> !movable.contains(task) && (task.status().open() || today.equals(task.completedOn())))
                .toList();
        DayDistributor.distribute(day1, minutesPerDay, firstDay, endDay, fixed, new ArrayList<>(movable));
        DayDistributor.order(tasks);
        List<Rebalance.Move> moves = movable.stream()
                .filter(task -> task.dayNo() != before.get(task.id()))
                .map(task -> new Rebalance.Move(task.id(), date(task.dayNo())))
                .toList();
        if (skipped.isEmpty()) {
            return Optional.of(new Rebalance(lag, skipped, moves, manual)); // only redistributed (§10.5)
        }
        rebalancedAt = now;
        rebalanceCount++;
        rebalanceSkipped = List.copyOf(skipped);
        return Optional.of(new Rebalance(lag, skipped, moves, manual));
    }

    /**
     * Local order of exclusion when tutor-agent is not asked: repeats, strong item-set types (longest first),
     * grammar exercises, external resources, then the rest from the shortest. Never the mock exam, tasks of the
     * tutor, running tasks or vocabulary reviews.
     */
    private List<WeekTask> exclusionOrder(List<WeekTask> pending) {
        Set<TaskType> weak = Set.copyOf(weakTypes);
        Function<WeekTask, Integer> group = task -> {
            if (task.repeat()) {
                return 0;
            }
            if (task.kind() == WeekTaskKind.ITEM_SET
                    && task.taskType() != null
                    && task.taskType() != TaskType.GRAMMAR_EXERCISE
                    && !weak.contains(task.taskType())) {
                return 1;
            }
            if (task.taskType() == TaskType.GRAMMAR_EXERCISE) {
                return 2;
            }
            if (task.kind() == WeekTaskKind.EXTERNAL_RESOURCE) {
                return 3;
            }
            return 4;
        };
        return pending.stream()
                .filter(task -> task.status() == WeekTaskStatus.PENDING
                        && task.kind() != WeekTaskKind.MOCK_EXAM
                        && task.kind() != WeekTaskKind.VOCABULARY_REVIEW
                        && task.origin() != TaskOrigin.TUTOR)
                .sorted(Comparator.comparing(group)
                        .thenComparing(
                                task -> group.apply(task) == 1 ? -task.estimatedMinutes() : task.estimatedMinutes())
                        .thenComparing(WeekTask::sortOrder))
                .toList();
    }

    // ---- close (§10.6) ----

    /**
     * The check at the start of {@code date} in the learner's time zone: closes the week at 70% after day 7 or when
     * day 10 is over; the closing date is the day before {@code date}.
     */
    public Optional<WeekCloseReason> closeIfDue(LocalDate date, Instant now) {
        if (!status.open()) {
            return Optional.empty();
        }
        int dayNo = dayNo(date);
        if (dayNo >= DAYS + 1 && progress() >= CLOSE_PROGRESS - 1e-9) {
            close(WeekCloseReason.PROGRESS_70, date.minusDays(1), now);
            return Optional.of(WeekCloseReason.PROGRESS_70);
        }
        if (dayNo >= MAX_DAYS + 1) {
            close(WeekCloseReason.DAY_10, date.minusDays(1), now);
            return Optional.of(WeekCloseReason.DAY_10);
        }
        return Optional.empty();
    }

    /** "Finish week early": all done before the end of day 7 (AC-04.06.3). */
    public void finishEarly(LocalDate today, Instant now) {
        requireOpen();
        if (progress() < 1 - 1e-9 || dayNo(today) > DAYS) {
            throw new WeekRuleException("week-not-completable", "Finish week early needs 100% before the end of day 7");
        }
        close(WeekCloseReason.EARLY, today, now);
    }

    private void close(WeekCloseReason reason, LocalDate closingDate, Instant now) {
        for (WeekTask task : tasks) {
            if (task.status().open()) {
                task.skip(WeekTaskSkipReason.WEEK_CLOSE);
            }
        }
        status = WeekStatus.COMPLETED;
        completedAt = now;
        closedOn = closingDate;
        closedBy = reason;
    }

    /** Day 8 to 10 of a week under 70% (AC-04.06.2). */
    public boolean extended(LocalDate today) {
        return status.open() && dayNo(today) > DAYS;
    }

    public void rolledOver(LocalDate date) {
        lastRolloverDate = date;
    }

    private void begin() {
        if (status == WeekStatus.PUBLISHED) {
            status = WeekStatus.IN_PROGRESS;
        }
    }

    private void requireOpen() {
        if (!status.open()) {
            throw new WeekRuleException("week-not-editable", "Week " + weekNo + " is " + status);
        }
    }

    public WeekTask task(UUID taskId) {
        return tasks.stream()
                .filter(task -> task.id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new WeekRuleException("not-found", "No task " + taskId + " in the week"));
    }

    public boolean hasTask(UUID taskId) {
        return tasks.stream().anyMatch(task -> task.id().equals(taskId));
    }

    // ---- accessors ----

    public UUID id() {
        return id;
    }

    public UUID planId() {
        return planId;
    }

    public UUID userId() {
        return userId;
    }

    public int weekNo() {
        return weekNo;
    }

    public WeekStatus status() {
        return status;
    }

    public LocalDate day1() {
        return day1;
    }

    /** Day 7 of the week. */
    public LocalDate endsOn() {
        return day1.plusDays(DAYS - 1L);
    }

    public int minutesPerDay() {
        return minutesPerDay;
    }

    public boolean mockExam() {
        return mockExam;
    }

    public List<TaskType> weakTypes() {
        return weakTypes;
    }

    public @Nullable String rationale() {
        return rationale;
    }

    public DraftInfo draft() {
        return draft;
    }

    public Instant generatedAt() {
        return generatedAt;
    }

    public @Nullable Instant publishedAt() {
        return publishedAt;
    }

    public @Nullable Instant completedAt() {
        return completedAt;
    }

    public @Nullable LocalDate closedOn() {
        return closedOn;
    }

    public @Nullable WeekCloseReason closedBy() {
        return closedBy;
    }

    public @Nullable Instant rebalancedAt() {
        return rebalancedAt;
    }

    public @Nullable Instant nextRebalanceAllowedAt() {
        return rebalancedAt == null ? null : rebalancedAt.plus(REBALANCE_INTERVAL);
    }

    public int rebalanceCount() {
        return rebalanceCount;
    }

    public List<UUID> rebalanceSkipped() {
        return rebalanceSkipped;
    }

    public @Nullable LocalDate lastRolloverDate() {
        return lastRolloverDate;
    }

    public int version() {
        return version;
    }

    /** Called by the repository after a write, so the next write of the same instance checks the new version. */
    public void saved(int newVersion) {
        version = newVersion;
    }

    public List<WeekTask> tasks() {
        return tasks.stream()
                .sorted(Comparator.comparingInt(WeekTask::sortOrder))
                .toList();
    }
}
