package com.toefly.practice.domain.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.practice.domain.attempt.TaskType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeEntryTest {

    static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    @Test
    @DisplayName("AC-16.01.1: heartbeats every 15 s add the active seconds they report")
    void accumulates() {
        TimeEntry entry = entry();

        entry.heartbeat(1, HeartbeatState.ACTIVE, 15, T0.plusSeconds(15), null);
        entry.heartbeat(2, HeartbeatState.ACTIVE, 12, T0.plusSeconds(30), 1);

        assertThat(entry.activeSeconds()).isEqualTo(27);
        assertThat(entry.audioReplays()).isEqualTo(1);
        assertThat(entry.lastHeartbeatAt()).isEqualTo(T0.plusSeconds(30));
    }

    @Test
    @DisplayName("a repeated or older sequence is ignored")
    void deduplicates() {
        TimeEntry entry = entry();

        assertThat(entry.heartbeat(2, HeartbeatState.ACTIVE, 15, T0.plusSeconds(15), null))
                .isTrue();
        assertThat(entry.heartbeat(2, HeartbeatState.ACTIVE, 15, T0.plusSeconds(16), null))
                .isFalse();
        assertThat(entry.heartbeat(1, HeartbeatState.ACTIVE, 15, T0.plusSeconds(17), null))
                .isFalse();

        assertThat(entry.activeSeconds()).isEqualTo(15);
        assertThat(entry.lastSequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("a delta never exceeds the wall clock since the previous heartbeat")
    void capsByWallClock() {
        TimeEntry entry = entry();

        entry.heartbeat(1, HeartbeatState.ACTIVE, 30, T0.plusSeconds(5), null);

        assertThat(entry.activeSeconds()).isEqualTo(7);
    }

    @Test
    @DisplayName("AC-16.01.6: after more than 60 s without heartbeats the missed interval is not credited")
    void gap() {
        TimeEntry entry = entry();
        entry.heartbeat(1, HeartbeatState.ACTIVE, 15, T0.plusSeconds(15), null);

        entry.heartbeat(2, HeartbeatState.ACTIVE, 15, T0.plusSeconds(90), null);
        entry.heartbeat(3, HeartbeatState.ACTIVE, 15, T0.plusSeconds(105), null);

        assertThat(entry.activeSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("AC-16.01.4: time between PAUSED and RESUMED is not credited")
    void pause() {
        TimeEntry entry = entry();
        entry.heartbeat(1, HeartbeatState.PAUSED, 10, T0.plusSeconds(10), null);
        entry.heartbeat(2, HeartbeatState.ACTIVE, 15, T0.plusSeconds(25), null);
        entry.heartbeat(3, HeartbeatState.PAUSED, 0, T0.plusSeconds(40), null);
        entry.heartbeat(4, HeartbeatState.RESUMED, 0, T0.plusSeconds(55), null);
        entry.heartbeat(5, HeartbeatState.ACTIVE, 15, T0.plusSeconds(70), null);

        assertThat(entry.activeSeconds()).isEqualTo(25);
        assertThat(entry.paused()).isFalse();
    }

    @Test
    @DisplayName("docs/03 §13.2: closing caps active time by the wall clock and marks large overshoots")
    void close() {
        TimeEntry entry = entry();
        entry.heartbeat(1, HeartbeatState.ACTIVE, 15, T0.plusSeconds(15), null);
        entry.heartbeat(2, HeartbeatState.ACTIVE, 15, T0.plusSeconds(30), null);

        entry.close(T0.plusSeconds(20), 0);
        entry.close(T0.plusSeconds(300), 0);

        assertThat(entry.activeSeconds()).isEqualTo(20);
        assertThat(entry.suspicious()).isTrue();
        assertThat(entry.closedAt()).isEqualTo(T0.plusSeconds(20));
        assertThat(entry.heartbeat(3, HeartbeatState.ACTIVE, 15, T0.plusSeconds(45), null))
                .isFalse();
    }

    @Test
    @DisplayName("the policy gives nothing for a heartbeat from the past")
    void policy() {
        assertThat(ActiveTimePolicy.credit(T0, T0.minusSeconds(1), 10)).isZero();
        assertThat(ActiveTimePolicy.credit(T0, T0.plusSeconds(20), 40)).isEqualTo(22);
        assertThat(ActiveTimePolicy.cap(100, 60)).isEqualTo(60);
        assertThat(ActiveTimePolicy.suspicious(60, 60)).isFalse();
    }

    private static TimeEntry entry() {
        TimeEntry entry = TimeEntry.open(
                UUID.randomUUID(),
                ContextType.ATTEMPT,
                UUID.randomUUID(),
                TaskType.READ_DAILY_LIFE,
                "READING",
                LocalDate.of(2026, 11, 10),
                T0,
                180);
        assertThat(entry.contextType()).isEqualTo(ContextType.ATTEMPT);
        assertThat(entry.estimatedSeconds()).isEqualTo(180);
        return entry;
    }
}
