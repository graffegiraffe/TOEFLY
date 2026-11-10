package com.toefly.planning.domain.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The plan of a learner up to the exam (docs/03-domain-model.md §6.8). Weeks are composed one at a time, so the
 * plan keeps only the length, the parameters and the number of the current week.
 */
public final class StudyPlan {

    public static final int DEFAULT_WEEKS = 8;
    public static final int MAX_WEEKS = 16;

    private final UUID id;
    private final UUID userId;
    private final LocalDate startDate;
    private final Instant createdAt;
    private @Nullable LocalDate examDate;
    private int weeksTotal;
    private ZoneId timezone;
    private int minutesPerDay;
    private int currentWeekNo;
    private Map<String, Double> targets;
    private int goalRevision;
    private int version;

    public StudyPlan(
            UUID id,
            UUID userId,
            @Nullable LocalDate examDate,
            int weeksTotal,
            LocalDate startDate,
            ZoneId timezone,
            int minutesPerDay,
            int currentWeekNo,
            Map<String, Double> targets,
            int goalRevision,
            Instant createdAt,
            int version) {
        if (weeksTotal < 1) {
            throw new IllegalArgumentException("weeksTotal must be positive");
        }
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.examDate = examDate;
        this.weeksTotal = weeksTotal;
        this.startDate = Objects.requireNonNull(startDate);
        this.timezone = Objects.requireNonNull(timezone);
        this.minutesPerDay = minutesPerDay;
        this.currentWeekNo = currentWeekNo;
        this.targets = Map.copyOf(targets);
        this.goalRevision = goalRevision;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.version = version;
    }

    /** A plan that starts today: {@code clamp(floor(days to the exam / 7), 1, 16)} weeks, 8 without a date. */
    public static StudyPlan create(
            UUID userId,
            @Nullable LocalDate examDate,
            LocalDate today,
            ZoneId timezone,
            int minutesPerDay,
            Map<String, Double> targets,
            int goalRevision,
            Instant now) {
        return new StudyPlan(
                UUID.randomUUID(),
                userId,
                examDate,
                weeks(today, examDate),
                today,
                timezone,
                minutesPerDay,
                1,
                targets,
                goalRevision,
                now,
                0);
    }

    /** Plan length of AC-02.01.4 and AC-02.01.5. */
    public static int weeks(LocalDate today, @Nullable LocalDate examDate) {
        if (examDate == null) {
            return DEFAULT_WEEKS;
        }
        long days = ChronoUnit.DAYS.between(today, examDate);
        return (int) Math.max(1, Math.min(MAX_WEEKS, Math.floorDiv(days, 7)));
    }

    /** Weeks with a mock exam: 4, 8 and the last one (AC-12.01.1). */
    public List<Integer> mockExamWeeks() {
        TreeSet<Integer> weeks = new TreeSet<>();
        for (int week : new int[] {4, 8, weeksTotal}) {
            if (week <= weeksTotal) {
                weeks.add(week);
            }
        }
        return List.copyOf(weeks);
    }

    public boolean mockExamWeek(int weekNo) {
        return mockExamWeeks().contains(weekNo);
    }

    /**
     * The next week after {@code closedWeekNo}, or {@code 0} when the plan is over. The last week of a plan with a
     * future exam extends the plan by one week (AC-04.07.6).
     */
    public int advance(int closedWeekNo, LocalDate nextDay1) {
        int next = closedWeekNo + 1;
        if (next > weeksTotal) {
            if (examDate == null || !nextDay1.isBefore(examDate)) {
                return 0;
            }
            weeksTotal = next;
        }
        currentWeekNo = next;
        return next;
    }

    /** New goal, exam date or daily minutes; the current week keeps its own minutes (AC-02.05.1). */
    public boolean changeGoal(
            int revision,
            @Nullable LocalDate exam,
            int minutes,
            ZoneId zone,
            Map<String, Double> newTargets,
            LocalDate today) {
        if (revision <= goalRevision) {
            return false;
        }
        goalRevision = revision;
        minutesPerDay = minutes;
        timezone = zone;
        targets = Map.copyOf(newTargets);
        if (!Objects.equals(exam, examDate)) {
            examDate = exam;
            int left = weeks(today, exam);
            weeksTotal = Math.max(currentWeekNo, currentWeekNo - 1 + left);
        }
        return true;
    }

    public void changePreferences(ZoneId zone, int minutes) {
        timezone = zone;
        minutesPerDay = minutes;
    }

    /** Days to the exam from {@code today}, or {@code null} without a date (AC-04.09.2). */
    public @Nullable Integer daysToExam(LocalDate today) {
        return examDate == null ? null : (int) ChronoUnit.DAYS.between(today, examDate);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public @Nullable LocalDate examDate() {
        return examDate;
    }

    public int weeksTotal() {
        return weeksTotal;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public ZoneId timezone() {
        return timezone;
    }

    public int minutesPerDay() {
        return minutesPerDay;
    }

    public int currentWeekNo() {
        return currentWeekNo;
    }

    /** Targets by section ({@code OVERALL}, {@code READING} …), band 1.0–6.0. */
    public Map<String, Double> targets() {
        return targets;
    }

    public int goalRevision() {
        return goalRevision;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public int version() {
        return version;
    }

    /** Called by the repository after a write, so the next write of the same instance checks the new version. */
    public void saved(int newVersion) {
        version = newVersion;
    }
}
