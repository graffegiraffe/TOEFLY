package com.toefly.planning.domain.week;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A unit of work of the week on one day (docs/03-domain-model.md §6.8). Changed only through its {@link Week}. */
public final class WeekTask {

    private final UUID id;
    private final WeekTaskKind kind;
    private final WeekSection section;
    private final @Nullable TaskType taskType;
    private final @Nullable String refId;
    private final String title;
    private final double estimatedMinutes;
    private final TaskOrigin origin;
    private final @Nullable String rationale;
    private final boolean repeat;
    private WeekTaskStatus status;
    private @Nullable WeekTaskSkipReason skippedReason;
    private int dayNo;
    private int sortOrder;
    private @Nullable UUID attemptId;
    private @Nullable Instant startedAt;
    private @Nullable Instant completedAt;
    private @Nullable LocalDate completedOn;

    public WeekTask(
            UUID id,
            WeekTaskKind kind,
            WeekSection section,
            @Nullable TaskType taskType,
            @Nullable String refId,
            String title,
            double estimatedMinutes,
            TaskOrigin origin,
            @Nullable String rationale,
            boolean repeat,
            WeekTaskStatus status,
            @Nullable WeekTaskSkipReason skippedReason,
            int dayNo,
            int sortOrder,
            @Nullable UUID attemptId,
            @Nullable Instant startedAt,
            @Nullable Instant completedAt,
            @Nullable LocalDate completedOn) {
        this.id = Objects.requireNonNull(id);
        this.kind = Objects.requireNonNull(kind);
        this.section = Objects.requireNonNull(section);
        this.taskType = taskType;
        this.refId = refId;
        this.title = Objects.requireNonNull(title);
        if (estimatedMinutes < 0) {
            throw new IllegalArgumentException("estimatedMinutes must not be negative");
        }
        this.estimatedMinutes = estimatedMinutes;
        this.origin = Objects.requireNonNull(origin);
        this.rationale = rationale;
        this.repeat = repeat;
        this.status = Objects.requireNonNull(status);
        this.skippedReason = skippedReason;
        this.dayNo = dayNo;
        this.sortOrder = sortOrder;
        this.attemptId = attemptId;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.completedOn = completedOn;
    }

    /** A new pending task; the day is assigned when the week is distributed. */
    public static WeekTask pending(
            WeekTaskKind kind,
            WeekSection section,
            @Nullable TaskType taskType,
            @Nullable String refId,
            String title,
            double estimatedMinutes,
            TaskOrigin origin,
            @Nullable String rationale,
            boolean repeat) {
        return new WeekTask(
                UUID.randomUUID(),
                kind,
                section,
                taskType,
                refId,
                title,
                estimatedMinutes,
                origin,
                rationale,
                repeat,
                WeekTaskStatus.PENDING,
                null,
                1,
                0,
                null,
                null,
                null,
                null);
    }

    void start(UUID attempt, Instant now) {
        if (status == WeekTaskStatus.PENDING) {
            status = WeekTaskStatus.IN_PROGRESS;
        }
        attemptId = attempt;
        startedAt = now;
    }

    void complete(@Nullable UUID attempt, LocalDate today, Instant now) {
        status = WeekTaskStatus.DONE;
        skippedReason = null;
        if (attempt != null) {
            attemptId = attempt;
        }
        completedAt = now;
        completedOn = today;
    }

    void reopen() {
        status = WeekTaskStatus.PENDING;
        completedAt = null;
        completedOn = null;
    }

    void release() {
        status = WeekTaskStatus.PENDING;
        startedAt = null;
    }

    void skip(WeekTaskSkipReason reason) {
        status = WeekTaskStatus.SKIPPED;
        skippedReason = reason;
    }

    void moveTo(int day) {
        dayNo = day;
    }

    void order(int value) {
        sortOrder = value;
    }

    /** Counted in the denominator of the week progress (AC-04.02.3). */
    public boolean countsInProgress() {
        return !(status == WeekTaskStatus.SKIPPED && skippedReason != null && skippedReason.excludedFromProgress());
    }

    public boolean legacy() {
        return taskType != null && taskType.legacy();
    }

    public UUID id() {
        return id;
    }

    public WeekTaskKind kind() {
        return kind;
    }

    public WeekSection section() {
        return section;
    }

    public @Nullable TaskType taskType() {
        return taskType;
    }

    public @Nullable String refId() {
        return refId;
    }

    public String title() {
        return title;
    }

    public double estimatedMinutes() {
        return estimatedMinutes;
    }

    public TaskOrigin origin() {
        return origin;
    }

    public @Nullable String rationale() {
        return rationale;
    }

    public boolean repeat() {
        return repeat;
    }

    public WeekTaskStatus status() {
        return status;
    }

    public @Nullable WeekTaskSkipReason skippedReason() {
        return skippedReason;
    }

    public int dayNo() {
        return dayNo;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public @Nullable UUID attemptId() {
        return attemptId;
    }

    public @Nullable Instant startedAt() {
        return startedAt;
    }

    public @Nullable Instant completedAt() {
        return completedAt;
    }

    public @Nullable LocalDate completedOn() {
        return completedOn;
    }
}
