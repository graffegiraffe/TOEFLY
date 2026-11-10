package com.toefly.practice.domain.time;

import com.toefly.practice.domain.attempt.TaskType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Active time of one context, for example an attempt (docs/03-domain-model.md §13). State-based: heartbeats are
 * de-duplicated by their sequence and credited by {@link ActiveTimePolicy}.
 */
public final class TimeEntry {

    private final UUID id;
    private final UUID userId;
    private final ContextType contextType;
    private final UUID contextId;
    private final @Nullable TaskType taskType;
    private final @Nullable String weekSection;
    private final LocalDate localDate;
    private final Instant startedAt;
    private final int estimatedSeconds;
    private int activeSeconds;
    private long lastSequence;
    private @Nullable Instant lastHeartbeatAt;
    private boolean paused;
    private int audioReplays;
    private boolean suspicious;
    private @Nullable Instant closedAt;
    private final int version;

    public TimeEntry(
            UUID id,
            UUID userId,
            ContextType contextType,
            UUID contextId,
            @Nullable TaskType taskType,
            @Nullable String weekSection,
            LocalDate localDate,
            Instant startedAt,
            int estimatedSeconds,
            int activeSeconds,
            long lastSequence,
            @Nullable Instant lastHeartbeatAt,
            boolean paused,
            int audioReplays,
            boolean suspicious,
            @Nullable Instant closedAt,
            int version) {
        this.id = id;
        this.userId = userId;
        this.contextType = contextType;
        this.contextId = contextId;
        this.taskType = taskType;
        this.weekSection = weekSection;
        this.localDate = localDate;
        this.startedAt = startedAt;
        this.estimatedSeconds = estimatedSeconds;
        this.activeSeconds = activeSeconds;
        this.lastSequence = lastSequence;
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.paused = paused;
        this.audioReplays = audioReplays;
        this.suspicious = suspicious;
        this.closedAt = closedAt;
        this.version = version;
    }

    public static TimeEntry open(
            UUID userId,
            ContextType contextType,
            UUID contextId,
            @Nullable TaskType taskType,
            @Nullable String weekSection,
            LocalDate localDate,
            Instant startedAt,
            int estimatedSeconds) {
        return new TimeEntry(
                UUID.randomUUID(),
                userId,
                contextType,
                contextId,
                taskType,
                weekSection,
                localDate,
                startedAt,
                estimatedSeconds,
                0,
                0,
                null,
                false,
                0,
                false,
                null,
                0);
    }

    /**
     * Applies a heartbeat; returns false for a repeated or older sequence, which changes nothing. The first
     * heartbeat is measured from the start of the context.
     */
    public boolean heartbeat(long sequence, HeartbeatState state, int delta, Instant now, @Nullable Integer replays) {
        if (closedAt != null || sequence <= lastSequence) {
            return false;
        }
        lastSequence = sequence;
        Instant previous = lastHeartbeatAt == null ? startedAt : lastHeartbeatAt;
        switch (state) {
            case ACTIVE -> {
                // an ACTIVE heartbeat during a pause is the first action after it: the pause itself is not credited
                if (!paused) {
                    activeSeconds += ActiveTimePolicy.credit(previous, now, delta);
                }
                paused = false;
            }
            case PAUSED -> {
                if (!paused) {
                    activeSeconds += ActiveTimePolicy.credit(previous, now, delta);
                }
                paused = true;
            }
            case RESUMED -> paused = false;
        }
        lastHeartbeatAt = now;
        if (replays != null) {
            audioReplays = Math.max(audioReplays, replays);
        }
        return true;
    }

    /** Closes the entry when the attempt ends; caps the total by the wall clock minus pauses (§13.2). */
    public void close(Instant now, long pausedSeconds) {
        if (closedAt != null) {
            return;
        }
        long wallClock = Math.max(0, Duration.between(startedAt, now).toSeconds() - pausedSeconds);
        suspicious = ActiveTimePolicy.suspicious(activeSeconds, wallClock);
        activeSeconds = ActiveTimePolicy.cap(activeSeconds, wallClock);
        closedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public ContextType contextType() {
        return contextType;
    }

    public UUID contextId() {
        return contextId;
    }

    public @Nullable TaskType taskType() {
        return taskType;
    }

    public @Nullable String weekSection() {
        return weekSection;
    }

    public LocalDate localDate() {
        return localDate;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public int estimatedSeconds() {
        return estimatedSeconds;
    }

    public int activeSeconds() {
        return activeSeconds;
    }

    public long lastSequence() {
        return lastSequence;
    }

    public @Nullable Instant lastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public boolean paused() {
        return paused;
    }

    public int audioReplays() {
        return audioReplays;
    }

    public boolean suspicious() {
        return suspicious;
    }

    public @Nullable Instant closedAt() {
        return closedAt;
    }

    public int version() {
        return version;
    }
}
