package com.toefly.analytics.domain.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.toefly.analytics.domain.common.Section;
import com.toefly.analytics.domain.common.TaskType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class StudentDashboardTest {

    static final UUID USER = UUID.randomUUID();
    static final UUID WEEK_1 = UUID.randomUUID();
    static final UUID WEEK_2 = UUID.randomUUID();
    static final LocalDate DAY1 = LocalDate.of(2026, 11, 16);
    static final Instant T0 = Instant.parse("2026-11-16T08:00:00Z");

    @Test
    @DisplayName("AC-05.02.1: the current week shows its number, day and progress")
    void currentWeek() {
        StudentDashboard d = StudentDashboard.empty(USER)
                .weekPublished(WEEK_1, 1, DAY1, 18, T0)
                .weekProgress(WEEK_1, 1, 22.5, T0.plusSeconds(60));

        CurrentWeek week = d.currentWeek();
        assertThat(week).isNotNull();
        assertThat(week.weekNo()).isEqualTo(1);
        assertThat(week.tasksTotal()).isEqualTo(18);
        assertThat(week.progressPercent()).isEqualTo(22.5);
        assertThat(week.dayOfWeek(DAY1.plusDays(4))).isEqualTo(5);
        assertThat(week.dayOfWeek(DAY1.minusDays(1))).isNull();
        assertThat(d.asOf()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    @DisplayName("INV-ANL-02: a later week replaces an earlier one; older progress never overwrites newer")
    void monotonicWeek() {
        StudentDashboard d = StudentDashboard.empty(USER)
                .weekPublished(WEEK_2, 2, DAY1.plusDays(7), 20, T0.plusSeconds(500))
                .weekProgress(WEEK_1, 1, 90, T0.plusSeconds(400))
                .weekCompleted(WEEK_1, 1, DAY1, 18, 90, T0.plusSeconds(450))
                .weekProgress(WEEK_2, 2, 10, T0.plusSeconds(700))
                .weekProgress(WEEK_2, 2, 5, T0.plusSeconds(600));

        assertThat(d.currentWeek().weekId()).isEqualTo(WEEK_2);
        assertThat(d.currentWeek().progressPercent()).isEqualTo(10);
        assertThat(d.currentWeek().day1()).isEqualTo(DAY1.plusDays(7));
        assertThat(d.asOf()).isEqualTo(T0.plusSeconds(700));
    }

    @Test
    @DisplayName("AC-04.06.1: a completed week stays completed with its final progress")
    void completedWeek() {
        StudentDashboard d = StudentDashboard.empty(USER)
                .weekPublished(WEEK_1, 1, DAY1, 18, T0)
                .weekCompleted(WEEK_1, 1, DAY1, 18, 77.8, T0.plusSeconds(10))
                .weekProgress(WEEK_1, 1, 80, T0.plusSeconds(20));

        assertThat(d.currentWeek().completed()).isTrue();
        assertThat(d.currentWeek().progressPercent()).isEqualTo(77.8);
    }

    @Test
    @DisplayName("AC-13.01.1, INV-ANL-02: the newest band estimate and θ by section win")
    void bandAndTheta() {
        BandSnapshot older = new BandSnapshot(4.0, 3.5, 4.5, List.of(), T0);
        BandSnapshot newer = new BandSnapshot(
                4.5, 4.0, 5.0, List.of(new SectionBand(Section.READING, true, 4.5, 4.0, 5.0, 24)), T0.plusSeconds(5));
        StudentDashboard d = StudentDashboard.empty(USER)
                .band(newer)
                .band(older)
                .ability(Section.READING, 0.8, 0.3, T0.plusSeconds(9))
                .ability(Section.READING, 0.1, 0.5, T0.plusSeconds(3))
                .ability(Section.LISTENING, -0.2, 0.4, T0);

        assertThat(d.band()).isEqualTo(newer);
        assertThat(d.theta().get(Section.READING).value()).isEqualTo(0.8);
        assertThat(d.theta()).containsKey(Section.LISTENING);
    }

    @Test
    @DisplayName("AC-05.02.1: the last five results, newest first, one row per attempt or submission")
    void lastResults() {
        StudentDashboard d = StudentDashboard.empty(USER);
        UUID submission = UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            d = d.result(RecentResult.attempt(
                    UUID.randomUUID(), TaskType.READ_DAILY_LIFE, Section.READING, i, 3, T0.plusSeconds(i)));
        }
        d = d.result(RecentResult.submission(submission, TaskType.WRITE_EMAIL, Section.WRITING, 3, T0.plusSeconds(10)))
                .result(RecentResult.submission(
                        submission, TaskType.WRITE_EMAIL, Section.WRITING, 4, T0.plusSeconds(20)))
                .result(RecentResult.submission(
                        submission, TaskType.WRITE_EMAIL, Section.WRITING, 2, T0.plusSeconds(15)));

        assertThat(d.lastResults()).hasSize(StudentDashboard.LAST_RESULTS);
        assertThat(d.lastResults().getFirst().rubricScore()).isEqualTo(4);
        assertThat(d.lastResults().stream().filter(r -> r.id().equals(submission)))
                .hasSize(1);
        assertThat(d.lastResults().getLast().correct()).isEqualTo(2);
    }

    @RepeatedTest(5)
    @DisplayName("ADR-0011: the dashboard does not depend on the order the events arrive in")
    void orderIndependent() {
        List<UnaryOperator<StudentDashboard>> events = new ArrayList<>(List.of(
                d -> d.weekPublished(WEEK_1, 1, DAY1, 18, T0),
                d -> d.weekProgress(WEEK_1, 1, 30, T0.plusSeconds(30)),
                d -> d.weekProgress(WEEK_1, 1, 40, T0.plusSeconds(40)),
                d -> d.band(new BandSnapshot(4.0, 3.5, 4.5, List.of(), T0.plusSeconds(35))),
                d -> d.ability(Section.WRITING, 0.4, 0.2, T0.plusSeconds(12)),
                d -> d.result(RecentResult.attempt(
                        new UUID(0, 1), TaskType.COMPLETE_THE_WORDS, Section.READING, 8, 10, T0.plusSeconds(20)))));
        StudentDashboard expected = apply(events);
        Collections.shuffle(events, new Random());

        assertThat(apply(events)).isEqualTo(expected);
    }

    private static StudentDashboard apply(List<UnaryOperator<StudentDashboard>> events) {
        StudentDashboard d = StudentDashboard.empty(USER);
        for (UnaryOperator<StudentDashboard> event : events) {
            d = event.apply(d);
        }
        return d;
    }
}
