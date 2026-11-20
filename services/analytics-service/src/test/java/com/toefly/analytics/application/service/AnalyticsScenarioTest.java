package com.toefly.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.analytics.application.port.in.AccessDeniedException;
import com.toefly.analytics.application.port.in.Caller;
import com.toefly.analytics.application.port.in.InvalidPeriodException;
import com.toefly.analytics.application.port.in.ProjectionName;
import com.toefly.analytics.application.port.in.StreakResult;
import com.toefly.analytics.application.port.in.StudentDashboardResult;
import com.toefly.analytics.application.port.in.event.AbilityUpdated;
import com.toefly.analytics.application.port.in.event.AnalyticsEvent;
import com.toefly.analytics.application.port.in.event.AttemptScored;
import com.toefly.analytics.application.port.in.event.AttemptStarted;
import com.toefly.analytics.application.port.in.event.AttemptSubmitted;
import com.toefly.analytics.application.port.in.event.BandEstimateUpdated;
import com.toefly.analytics.application.port.in.event.OnboardingCompleted;
import com.toefly.analytics.application.port.in.event.PreferencesUpdated;
import com.toefly.analytics.application.port.in.event.ProfileCreated;
import com.toefly.analytics.application.port.in.event.StudyPlanCreated;
import com.toefly.analytics.application.port.in.event.SubmissionEvaluated;
import com.toefly.analytics.application.port.in.event.TimeTracked;
import com.toefly.analytics.application.port.in.event.UsageRecorded;
import com.toefly.analytics.application.port.in.event.WeekCompleted;
import com.toefly.analytics.application.port.in.event.WeekPublished;
import com.toefly.analytics.application.port.in.event.WeekRebalanced;
import com.toefly.analytics.application.port.in.event.WeekTaskCompleted;
import com.toefly.analytics.domain.activity.PaceTally;
import com.toefly.analytics.domain.activity.TimeReport;
import com.toefly.analytics.domain.common.Section;
import com.toefly.analytics.domain.common.TaskType;
import com.toefly.analytics.domain.common.WeekSection;
import com.toefly.analytics.domain.dashboard.SectionBand;
import com.toefly.analytics.domain.metrics.AiUsageReport;
import com.toefly.analytics.domain.metrics.ProductReport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The service end to end on in-memory adapters: events in, dashboards out. */
class AnalyticsScenarioTest {

    static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    static final LocalDate DAY1 = LocalDate.of(2026, 11, 16);
    static final Instant NOW = TODAY.atTime(13, 0).atZone(MINSK).toInstant();

    final Fakes fakes = new Fakes();
    final UUID user = UUID.randomUUID();
    final Caller learner = new Caller(user, Set.of(Caller.Role.STUDENT));
    final UUID week = UUID.randomUUID();
    final UUID task1 = UUID.randomUUID();
    final UUID task2 = UUID.randomUUID();
    final UUID task3 = UUID.randomUUID();

    {
        fakes.clock.set(NOW);
    }

    @Test
    @DisplayName(
            "AC-05.02.1, AC-05.02.3: Home gets week, estimate, streak, goal and week time from the read model with asOf")
    void dashboard() {
        List<AnalyticsEvent> events = plan();
        events.add(new BandEstimateUpdated(
                user, 4.5, 4.0, 5.0, List.of(new SectionBand(Section.READING, true, 4.5, 4.0, 5.0, 30)), at(TODAY, 9)));
        events.add(new AbilityUpdated(user, Section.READING, 0.7, 0.3, at(TODAY, 9)));
        events.add(new AttemptScored(
                user, UUID.randomUUID(), TaskType.READ_DAILY_LIFE, Section.READING, 2, 3, at(TODAY, 10)));
        events.add(new SubmissionEvaluated(
                user, UUID.randomUUID(), TaskType.WRITE_EMAIL, Section.WRITING, 4, at(TODAY, 11)));
        events.forEach(fakes::record);

        StudentDashboardResult result = fakes.dashboardQuery.student(learner, null);

        assertThat(result.today()).isEqualTo(TODAY);
        assertThat(result.dashboard().currentWeek().weekNo()).isEqualTo(1);
        assertThat(result.dashboard().currentWeek().dayOfWeek(TODAY)).isEqualTo(5);
        assertThat(result.dashboard().band().overallPoint()).isEqualTo(4.5);
        assertThat(result.dashboard().theta()).containsKey(Section.READING);
        assertThat(result.dashboard().lastResults()).hasSize(2);
        assertThat(result.examCountdownDays()).isEqualTo(37);
        assertThat(result.weekActiveSeconds()).isEqualTo(600 + 300);
        assertThat(result.weeks())
                .singleElement()
                .satisfies(w -> assertThat(w.overallPoint()).isEqualTo(4.5));
        assertThat(result.asOf()).isEqualTo(at(TODAY, 11));
    }

    @Test
    @DisplayName(
            "AC-19.02.1, AC-19.01.2: active time reaching minutesPerDay meets the goal and the streak counts today at once")
    void goalByTime() {
        plan().forEach(fakes::record);
        StreakResult before = fakes.dashboardQuery.streak(learner);
        assertThat(before.streak().todayDone()).isFalse();
        assertThat(before.todayGoalProgress()).isEqualTo(0.25);

        fakes.record(time(TODAY, TaskType.READ_DAILY_LIFE, 900, at(TODAY, 12)));

        StreakResult after = fakes.dashboardQuery.streak(learner);
        assertThat(after.streak().todayDone()).isTrue();
        assertThat(after.streak().current()).isEqualTo(1);
        assertThat(after.todayGoalProgress()).isEqualTo(1.0);
        assertThat(after.timezone()).isEqualTo("Europe/Minsk");
        assertThat(after.asOf()).isEqualTo(at(TODAY, 12));
    }

    @Test
    @DisplayName(
            "AC-19.02.1: all tasks planned for the day done meet the goal even below minutesPerDay; overdue ones do not count")
    void goalByDayPlan() {
        plan().forEach(fakes::record);
        fakes.record(new WeekTaskCompleted(user, week, task2, 1, TODAY, 40, at(TODAY, 11)));
        assertThat(fakes.dashboardQuery.streak(learner).streak().todayDone()).isFalse();

        fakes.record(new WeekTaskCompleted(user, week, task3, 1, TODAY, 60, at(TODAY, 12)));

        StreakResult streak = fakes.dashboardQuery.streak(learner);
        assertThat(streak.streak().todayDone()).isTrue();
        assertThat(fakes.activity.findDay(user, TODAY).orElseThrow().tasksDone())
                .isEqualTo(2);
        assertThat(fakes.activity.findDay(user, DAY1).orElseThrow().goalMet()).isFalse();
    }

    @Test
    @DisplayName("AC-04.05.1: a rebalance moves a task to another day, so it no longer blocks the plan of today")
    void rebalanceMovesTask() {
        plan().forEach(fakes::record);
        fakes.record(new WeekRebalanced(
                user, week, List.of(new WeekPublished.TaskDay(task3, TODAY.plusDays(1))), at(TODAY, 8)));

        fakes.record(new WeekTaskCompleted(user, week, task2, 1, TODAY, 40, at(TODAY, 11)));

        assertThat(fakes.dashboardQuery.streak(learner).streak().todayDone()).isTrue();
    }

    @Test
    @DisplayName(
            "AC-19.01.1, AC-19.01.4: days already counted stay after a time zone change; today follows the new zone")
    void timezoneChange() {
        plan().forEach(fakes::record);
        fakes.record(time(TODAY.minusDays(1), TaskType.WRITE_EMAIL, 1800, at(TODAY.minusDays(1), 20)));
        fakes.record(time(TODAY, TaskType.WRITE_EMAIL, 1800, at(TODAY, 12)));
        assertThat(fakes.dashboardQuery.streak(learner).streak().current()).isEqualTo(2);

        fakes.record(new PreferencesUpdated(
                user, "America/Los_Angeles", 30, at(TODAY, 12).plusSeconds(60)));
        fakes.clock.set(TODAY.atTime(9, 0).atZone(MINSK).toInstant());

        StreakResult streak = fakes.dashboardQuery.streak(learner);
        assertThat(streak.today()).isEqualTo(TODAY.minusDays(1));
        assertThat(streak.streak().current()).isEqualTo(1);
        assertThat(fakes.activity.studyDays(user)).contains(TODAY.minusDays(1), TODAY);
    }

    @Test
    @DisplayName(
            "AC-16.02.1, AC-16.02.2, AC-16.03.1: time by section and type against the ETS norm of the attempt and the plan")
    void timeReport() {
        plan().forEach(fakes::record);
        UUID slow = UUID.randomUUID();
        fakes.record(new AttemptStarted(user, slow, TaskType.READ_ACADEMIC_PASSAGE, 360, at(TODAY, 11)));
        fakes.record(new TimeTracked(
                user, slow, TaskType.READ_ACADEMIC_PASSAGE, WeekSection.READING, TODAY, 522, false, at(TODAY, 12)));

        TimeReport report = fakes.activityQuery.timeReport(learner, null, null, null);

        assertThat(report.from()).isEqualTo(TODAY.minusDays(6));
        assertThat(report.to()).isEqualTo(TODAY.plusDays(1));
        assertThat(report.totalSeconds()).isEqualTo(600 + 300 + 522);
        assertThat(report.plannedSeconds()).isEqualTo(7 * 20 * 60);
        PaceTally pace = report.byTaskType().stream()
                .filter(t -> t.taskType() == TaskType.READ_ACADEMIC_PASSAGE)
                .findFirst()
                .orElseThrow()
                .pace();
        assertThat(pace.verdict()).isEqualTo(PaceTally.Verdict.SLOWER);
        assertThat(report.bySection()).containsEntry(WeekSection.READING, 600 + 300 + 522);
        assertThat(fakes.activityQuery.days(learner, null, TODAY.minusDays(7), TODAY.plusDays(1)))
                .hasSize(2);
    }

    @Test
    @DisplayName("INV-ANL-01: a redelivered event is journaled and applied once")
    void duplicate() {
        UUID eventId = UUID.randomUUID();
        TimeTracked event = time(TODAY, TaskType.READ_DAILY_LIFE, 120, at(TODAY, 9));

        assertThat(fakes.record(eventId, event)).isTrue();
        assertThat(fakes.record(eventId, event)).isFalse();

        assertThat(fakes.activity.findDay(user, TODAY).orElseThrow().activeSeconds())
                .isEqualTo(120);
    }

    @Test
    @DisplayName(
            "ADR-0011, docs/12 phase 11 DoD: rebuilding every read model from the journal gives the incremental result")
    void rebuildMatchesIncremental() {
        everything().forEach(fakes::record);
        var incremental = fakes.snapshot();

        for (ProjectionName projection : ProjectionName.values()) {
            long replayed = fakes.replayer.replay(projection);
            assertThat(replayed).isEqualTo(fakes.journal.entries.size());
        }

        assertThat(fakes.snapshot()).isEqualTo(incremental);
        assertThat(fakes.journal.locks).isEqualTo(ProjectionName.values().length);
    }

    @Test
    @DisplayName("INV-ANL-05: account deletion removes the learner from every read model and the journal")
    void forget() {
        everything().forEach(fakes::record);
        UUID other = UUID.randomUUID();
        fakes.record(new ProfileCreated(other, List.of("STUDENT"), at(TODAY, 9)));

        fakes.recorder.forget(user);

        assertThat(fakes.journal.entries.values())
                .allSatisfy(e -> assertThat(e.meta().userId()).isNotEqualTo(user));
        assertThat(fakes.dashboards.find(user)).isEmpty();
        assertThat(fakes.activity.days(user, DAY1, TODAY.plusDays(1))).isEmpty();
        assertThat(fakes.activity.studyDays(user)).isEmpty();
        assertThat(fakes.learners.find(user)).isEmpty();
        assertThat(fakes.metrics.milestones(user)).isEmpty();
        assertThat(fakes.metrics.milestones(other)).isPresent();
    }

    @Test
    @DisplayName(
            "AC-16.03.2, docs/05 §2.8: another learner's data is forbidden; a tutor waits for enrollments (phase 10)")
    void access() {
        UUID other = UUID.randomUUID();
        Caller tutor = new Caller(UUID.randomUUID(), Set.of(Caller.Role.TUTOR));

        assertThatThrownBy(() -> fakes.dashboardQuery.student(learner, other))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fakes.activityQuery.timeReport(tutor, user, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fakes.dashboardQuery.streak(tutor)).isInstanceOf(AccessDeniedException.class);
        assertThat(fakes.dashboardQuery.student(learner, user).dashboard().userId())
                .isEqualTo(user);
    }

    @Test
    @DisplayName("docs/05 §2.5: a period must be [from, to) and at most 400 days")
    void period() {
        assertThatThrownBy(() -> fakes.activityQuery.days(learner, null, TODAY, TODAY))
                .isInstanceOf(InvalidPeriodException.class);
        assertThatThrownBy(() -> fakes.activityQuery.days(learner, null, TODAY.minusDays(401), TODAY))
                .isInstanceOf(InvalidPeriodException.class);
        assertThatThrownBy(() -> fakes.adminMetrics.product(TODAY, TODAY.minusDays(1)))
                .isInstanceOf(InvalidPeriodException.class);
    }

    @Test
    @DisplayName("docs/01 §7.2: the funnel, retention and week completion come from profile and planning events")
    void productMetrics() {
        everything().forEach(fakes::record);
        UUID tutorOnly = UUID.randomUUID();
        fakes.record(new ProfileCreated(tutorOnly, List.of("TUTOR"), at(TODAY, 9)));

        ProductReport report = fakes.adminMetrics.product(null, null);

        assertThat(report.from()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(report.funnel()).isEqualTo(new ProductReport.Funnel(1, 1, 1, 1, 1));
        int submitted =
                report.daily().stream().mapToInt(c -> c.attemptsSubmitted()).sum();
        assertThat(submitted).isEqualTo(1);
        assertThat(report.weekCompletionRate()).isEqualTo(1.0);
        assertThat(report.medianStudyDaysRatio()).isNotNull();
    }

    @Test
    @DisplayName("docs/01 §7.2: a step that happened earlier moves its count to the earlier day")
    void earlierStepMovesCounter() {
        fakes.record(new ProfileCreated(user, List.of("STUDENT"), at(TODAY, 9)));
        fakes.record(new ProfileCreated(user, List.of("STUDENT"), at(TODAY.minusDays(2), 9)));
        fakes.record(new ProfileCreated(user, List.of("STUDENT"), at(TODAY, 10)));

        assertThat(fakes.metrics.counters(TODAY.minusDays(3), TODAY.plusDays(1)))
                .extracting(c -> c.day() + "=" + c.newUsers())
                .containsExactlyInAnyOrder(TODAY.minusDays(2) + "=1", TODAY + "=0");
    }

    @Test
    @DisplayName("docs/01 §7.2: AI spend per provider and the cost per active learner")
    void aiUsage() {
        everything().forEach(fakes::record);

        AiUsageReport report = fakes.adminMetrics.aiUsage(null, null);

        assertThat(report.totals().calls()).isEqualTo(2);
        assertThat(report.totals().fallbackCalls()).isEqualTo(1);
        assertThat(report.activeLearners()).isEqualTo(1);
        assertThat(report.costPerActiveLearnerMicroUsd()).isEqualTo(500);
        assertThat(report.byPrompt()).extracting(AiUsageReport.Breakdown::key).contains("—");
    }

    private List<AnalyticsEvent> plan() {
        List<AnalyticsEvent> events = new ArrayList<>();
        events.add(new OnboardingCompleted(
                user,
                "Europe/Minsk",
                20,
                LocalDate.of(2026, 12, 27),
                5.0,
                Map.of(Section.WRITING, 5.5),
                at(DAY1.minusDays(1), 10)));
        events.add(new StudyPlanCreated(
                user, UUID.randomUUID(), LocalDate.of(2026, 12, 27), 20, "Europe/Minsk", at(DAY1.minusDays(1), 11)));
        events.add(new WeekPublished(
                user,
                week,
                1,
                DAY1,
                List.of(
                        new WeekPublished.TaskDay(task1, DAY1),
                        new WeekPublished.TaskDay(task2, TODAY),
                        new WeekPublished.TaskDay(task3, TODAY)),
                at(DAY1, 0)));
        events.add(time(DAY1, TaskType.READ_DAILY_LIFE, 600, at(TODAY.minusDays(1), 9)));
        events.add(time(TODAY, TaskType.COMPLETE_THE_WORDS, 300, at(TODAY, 9)));
        return events;
    }

    private List<AnalyticsEvent> everything() {
        List<AnalyticsEvent> events = new ArrayList<>();
        events.add(new ProfileCreated(user, List.of("STUDENT"), at(DAY1.minusDays(1), 9)));
        events.addAll(plan());
        UUID attempt = UUID.randomUUID();
        events.add(new AttemptStarted(user, attempt, TaskType.READ_DAILY_LIFE, 120, at(TODAY, 10)));
        events.add(new AttemptSubmitted(user, attempt, at(TODAY, 10).plusSeconds(100)));
        events.add(new TimeTracked(
                user,
                attempt,
                TaskType.READ_DAILY_LIFE,
                WeekSection.READING,
                TODAY,
                1500,
                false,
                at(TODAY, 10).plusSeconds(101)));
        events.add(new AttemptScored(
                user,
                attempt,
                TaskType.READ_DAILY_LIFE,
                Section.READING,
                2,
                3,
                at(TODAY, 10).plusSeconds(102)));
        events.add(new WeekTaskCompleted(user, week, task1, 1, DAY1, 33.3, at(DAY1, 15)));
        events.add(new WeekTaskCompleted(user, week, task2, 1, TODAY, 66.7, at(TODAY, 11)));
        events.add(new BandEstimateUpdated(user, null, null, null, List.of(), at(TODAY, 11)));
        events.add(new AbilityUpdated(user, Section.READING, 0.3, 0.4, at(TODAY, 11)));
        events.add(new WeekCompleted(user, week, 1, DAY1, TODAY, 100, 3, 3, "PROGRESS_70", at(TODAY, 12)));
        events.add(new UsageRecorded(
                user, "GROQ", "openai/gpt-oss-20b", "writing.grade", 900, 120, 400, 0, false, at(TODAY, 11)));
        events.add(new UsageRecorded(null, "OLLAMA", "nomic-embed-text", null, 30, 0, 100, 1, true, at(TODAY, 11)));
        return events;
    }

    private TimeTracked time(LocalDate day, TaskType type, int seconds, Instant at) {
        return new TimeTracked(user, UUID.randomUUID(), type, null, day, seconds, false, at);
    }

    private static Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(MINSK).toInstant();
    }
}
