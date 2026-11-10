package com.toefly.practice.domain.time;

import java.time.Duration;
import java.time.Instant;

/**
 * Server side of the active-time rules (docs/03-domain-model.md §13.1): the client counts only visible time with
 * input in the last two minutes and sends deltas every 15 seconds; the server never credits more than the wall
 * clock since the previous heartbeat and nothing after a gap of more than 60 seconds.
 */
public final class ActiveTimePolicy {

    /** A heartbeat later than this after the previous one means the connection was lost. */
    public static final Duration MAX_GAP = Duration.ofSeconds(60);

    /** Upper bound of one delta (HeartbeatRequest.activeSecondsDelta). */
    public static final int MAX_DELTA = 30;

    /** Clock skew between client and server tolerated per heartbeat. */
    static final int TOLERANCE_SECONDS = 2;

    /** A closed entry is suspicious when active time exceeds the wall clock by more than 5 %. */
    static final double SUSPICIOUS_RATIO = 1.05;

    private ActiveTimePolicy() {}

    /** Seconds credited for a heartbeat {@code delta} that arrives at {@code now} after {@code previous}. */
    public static int credit(Instant previous, Instant now, int delta) {
        Duration gap = Duration.between(previous, now);
        if (gap.isNegative() || gap.compareTo(MAX_GAP) > 0) {
            return 0;
        }
        long allowed = (gap.toMillis() + 999) / 1000 + TOLERANCE_SECONDS;
        return (int) Math.max(0, Math.min(Math.min(delta, MAX_DELTA), allowed));
    }

    /** Active time may not exceed the wall clock of the context minus its pauses. */
    public static int cap(int activeSeconds, long wallClockSeconds) {
        return (int) Math.max(0, Math.min(activeSeconds, wallClockSeconds));
    }

    public static boolean suspicious(int activeSeconds, long wallClockSeconds) {
        return wallClockSeconds >= 0 && activeSeconds > wallClockSeconds * SUSPICIOUS_RATIO + TOLERANCE_SECONDS;
    }
}
