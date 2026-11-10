package com.toefly.planning.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.planning.application.port.in.OnboardingGoal;
import com.toefly.planning.application.port.in.PlanAlreadyExistsException;
import com.toefly.planning.application.port.in.PlanQueryUseCase.Today;
import com.toefly.planning.application.port.in.PlanQueryUseCase.WeekSlot;
import com.toefly.planning.application.port.in.VersionMismatchException;
import com.toefly.planning.application.port.out.Cause;
import com.toefly.planning.domain.plan.StudyPlan;
import com.toefly.planning.domain.week.Rebalance;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekCloseReason;
import com.toefly.planning.domain.week.WeekRuleException;
import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekStatus;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import com.toefly.planning.domain.week.WeekTaskStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** planning-service from the onboarding to the next week, with in-memory ports and a clock moved by hand. */
class PlanningScenarioTest {

    static final Instant START = Instant.parse("2026-11-10T09:00:00Z");
    static final ZoneId MINSK = ZoneId.of("Europe/Minsk");

    private final Fakes fakes = new Fakes(START);

    private UUID onboard(ZoneId zone, LocalDate exam, int minutes) {
        UUID user = UUID.randomUUID();
        fakes.planService.onboardingCompleted(
                new OnboardingGoal(user, 1, exam, zone, minutes, Map.of("OVERALL", 4.5)), Cause.none());
        return user;
    }

    private StudyPlan plan(UUID user) {
        return fakes.plans.findByUser(user).orElseThrow();
    }

    private Week week(UUID user, int weekNo) {
        return fakes.weeks.findByPlan(plan(user).id()).stream()
                .filter(week -> week.weekNo() == weekNo)
                .findFirst()
                .orElseThrow();
    }

    private void moveTo(LocalDate date, ZoneId zone) {
        fakes.clock.set(date.atStartOfDay(zone).toInstant().plus(Duration.ofHours(10)));
    }

    private void scoreAll(UUID user, Week week, int lastDay) {
        for (WeekTask task : week.tasks()) {
            if (task.dayNo() <= lastDay && task.kind() == WeekTaskKind.ITEM_SET) {
                fakes.attempts.attemptScored(
                        user, task.id(), UUID.randomUUID(), task.taskType(), 3, 3, true, Instant.now(), Cause.none());
            } else if (task.dayNo() <= lastDay && task.kind().manuallyCompletable()) {
                fakes.tasks.complete(user, task.id());
            }
        }
    }

    @Nested
    @DisplayName("the plan")
    class Plan {

        @Test
        @DisplayName("AC-04.01.1, AC-04.01.2: onboarding builds the plan and Week 1 from today, both published")
        void onboarding() {
            UUID user = onboard(MINSK, LocalDate.of(2027, 1, 5), 60);
            StudyPlan plan = plan(user);
            assertThat(plan.weeksTotal()).isEqualTo(8);
            Week first = week(user, 1);
            assertThat(first.status()).isEqualTo(WeekStatus.PUBLISHED);
            assertThat(first.day1()).isEqualTo(LocalDate.of(2026, 11, 10));
            assertThat(fakes.events.of("study-plan-created")).hasSize(1);
            assertThat(fakes.events.of("week-published")).hasSize(1);
        }

        @Test
        @DisplayName("phase 4 DoD: a plan of 1 week (exam in 10 days) is built and extended once to the exam")
        void oneWeek() {
            UUID user = onboard(MINSK, LocalDate.of(2026, 11, 20), 60);
            assertThat(plan(user).weeksTotal()).isEqualTo(1);
            assertThat(plan(user).mockExamWeeks()).containsExactly(1);
            Week first = week(user, 1);
            assertThat(first.tasks()).isNotEmpty();
            assertThat(first.tasks().stream().mapToInt(WeekTask::dayNo).max().orElseThrow())
                    .isEqualTo(7);
            scoreAll(user, first, 7);
            moveTo(LocalDate.of(2026, 11, 17), MINSK); // day 8: closes, the exam is still ahead
            fakes.rollover.rolloverAll();
            assertThat(week(user, 1).closedBy()).isEqualTo(WeekCloseReason.PROGRESS_70);
            assertThat(plan(user).weeksTotal()).isEqualTo(2);
            assertThat(week(user, 2).day1()).isEqualTo(LocalDate.of(2026, 11, 17));
        }

        @Test
        @DisplayName("phase 4 DoD: a plan of 16 weeks (exam in 200 days) has mock weeks 4, 8 and 16")
        void sixteenWeeks() {
            UUID user = onboard(MINSK, LocalDate.of(2026, 11, 10).plusDays(200), 45);
            assertThat(plan(user).weeksTotal()).isEqualTo(16);
            assertThat(plan(user).mockExamWeeks()).containsExactly(4, 8, 16);
            List<WeekSlot> slots = fakes.queries.weeks(user);
            assertThat(slots).hasSize(16);
            assertThat(slots.getFirst().week()).isNotNull();
            assertThat(slots.subList(1, 16))
                    .allSatisfy(slot -> assertThat(slot.week()).isNull());
            assertThat(slots.get(15).startDate())
                    .isEqualTo(LocalDate.of(2026, 11, 10).plusWeeks(15));
            assertThat(slots.get(3).mockExam()).isTrue();
        }

        @Test
        @DisplayName("AC-02.05.1: a repeated onboarding does not build a second plan; a new revision applies next week")
        void goalChange() {
            UUID user = onboard(MINSK, null, 60);
            fakes.planService.onboardingCompleted(new OnboardingGoal(user, 1, null, MINSK, 60, Map.of()), Cause.none());
            assertThat(fakes.plans.byId).hasSize(1);
            fakes.planService.onboardingCompleted(new OnboardingGoal(user, 2, null, MINSK, 30, Map.of()), Cause.none());
            assertThat(plan(user).minutesPerDay()).isEqualTo(30);
            assertThat(week(user, 1).minutesPerDay()).isEqualTo(60);
            assertThatThrownBy(() -> fakes.planService.create(user, LocalDate.of(2027, 3, 1), null, 60))
                    .isInstanceOf(PlanAlreadyExistsException.class);
        }

        @Test
        @DisplayName("PATCH /study-plans/current checks the version and keeps the current week")
        void patch() {
            UUID user = onboard(MINSK, null, 60);
            int version = plan(user).version();
            assertThatThrownBy(() -> fakes.planService.patch(user, version + 5, null, 30))
                    .isInstanceOf(VersionMismatchException.class);
            StudyPlan patched = fakes.planService.patch(user, version, LocalDate.of(2026, 12, 22), 45);
            assertThat(patched.minutesPerDay()).isEqualTo(45);
            assertThat(patched.weeksTotal()).isEqualTo(6);
        }

        @Test
        @DisplayName("POST /study-plans without the onboarding builds Week 1")
        void create() {
            UUID user = UUID.randomUUID();
            StudyPlan plan = fakes.planService.create(user, LocalDate.of(2027, 2, 1), 4, 30);
            assertThat(plan.weeksTotal()).isEqualTo(4);
            assertThat(week(user, 1).status()).isEqualTo(WeekStatus.PUBLISHED);
        }

        @Test
        @DisplayName("INV-WK-04: no item set or unit appears twice in the plan")
        void noRepeats() {
            UUID user = onboard(MINSK, null, 60);
            scoreAll(user, week(user, 1), 7);
            moveTo(LocalDate.of(2026, 11, 17), MINSK);
            fakes.rollover.rolloverAll();
            Set<String> refs = new HashSet<>();
            for (int weekNo = 1; weekNo <= 2; weekNo++) {
                for (WeekTask task : week(user, weekNo).tasks()) {
                    if (task.kind() != WeekTaskKind.EXTERNAL_RESOURCE) {
                        assertThat(refs.add(task.refId())).as(task.title()).isTrue();
                    }
                }
            }
            assertThat(week(user, 2).tasks())
                    .filteredOn(task -> task.kind() == WeekTaskKind.GRAMMAR_UNIT)
                    .extracting(WeekTask::title)
                    .contains("Round-Up Unit 2", "Collins Grammar Unit 2");
        }
    }

    @Nested
    @DisplayName("attempts")
    class Attempts {

        @Test
        @DisplayName("AC-04.03.3: attempt-scored closes the task once and publishes week-task-completed once")
        void scoredOnce() {
            UUID user = onboard(MINSK, null, 60);
            WeekTask task = week(user, 1).tasks().stream()
                    .filter(candidate -> candidate.kind() == WeekTaskKind.ITEM_SET)
                    .findFirst()
                    .orElseThrow();
            UUID attempt = UUID.randomUUID();
            fakes.attempts.attemptStarted(user, task.id(), attempt, false, START);
            assertThat(week(user, 1).status()).isEqualTo(WeekStatus.IN_PROGRESS);
            assertThat(week(user, 1).task(task.id()).status()).isEqualTo(WeekTaskStatus.IN_PROGRESS);
            for (int i = 0; i < 2; i++) {
                fakes.attempts.attemptScored(
                        user, task.id(), attempt, task.taskType(), 2, 3, true, START, Cause.none());
            }
            assertThat(week(user, 1).task(task.id()).status()).isEqualTo(WeekTaskStatus.DONE);
            assertThat(fakes.events.of("week-task-completed")).hasSize(1);
            assertThat(week(user, 1).progress()).isGreaterThan(0);
        }

        @Test
        @DisplayName("NFR-SEC-02: an attempt of another user and an attempt without a task change no week")
        void foreign() {
            UUID user = onboard(MINSK, null, 60);
            WeekTask task = week(user, 1).tasks().getFirst();
            fakes.attempts.attemptScored(
                    UUID.randomUUID(),
                    task.id(),
                    UUID.randomUUID(),
                    TaskType.READ_DAILY_LIFE,
                    1,
                    1,
                    true,
                    START,
                    Cause.none());
            fakes.attempts.attemptScored(
                    user, null, UUID.randomUUID(), TaskType.READ_DAILY_LIFE, 1, 1, true, START, Cause.none());
            fakes.attempts.attemptStarted(user, null, UUID.randomUUID(), false, START);
            assertThat(week(user, 1).task(task.id()).status()).isEqualTo(WeekTaskStatus.PENDING);
            assertThat(fakes.events.of("week-task-completed")).isEmpty();
        }

        @Test
        @DisplayName("docs/02 §4.0: a type under 60% in the last two weeks gets more sets next week")
        void weakTypeNextWeek() {
            UUID user = onboard(MINSK, null, 60);
            Week first = week(user, 1);
            for (WeekTask task : first.tasks()) {
                if (task.kind() == WeekTaskKind.ITEM_SET) {
                    int correct = task.taskType() == TaskType.LISTEN_CONVERSATION ? 0 : 3;
                    fakes.attempts.attemptScored(
                            user,
                            task.id(),
                            UUID.randomUUID(),
                            task.taskType(),
                            correct,
                            3,
                            true,
                            fakes.clock.instant(),
                            Cause.none());
                } else if (task.kind().manuallyCompletable()) {
                    fakes.tasks.complete(user, task.id());
                }
            }
            fakes.sync.abilityUpdated(user, WeekSection.LISTENING, -1.0, fakes.clock.instant());
            moveTo(LocalDate.of(2026, 11, 17), MINSK);
            fakes.rollover.rolloverAll();
            Week second = week(user, 2);
            assertThat(second.weakTypes()).contains(TaskType.LISTEN_CONVERSATION);
            long conversations = second.tasks().stream()
                    .filter(task -> task.taskType() == TaskType.LISTEN_CONVERSATION)
                    .count();
            long firstConversations = first.tasks().stream()
                    .filter(task -> task.taskType() == TaskType.LISTEN_CONVERSATION)
                    .count();
            assertThat(conversations).isGreaterThan(firstConversations);
            assertThat(second.rationale()).contains("conversation");
        }
    }

    @Nested
    @DisplayName("the day boundary in the learner's time zone")
    class TimeZones {

        @Test
        @DisplayName("phase 4 DoD: UTC+14, UTC−12 and Europe/Minsk start the next day at their own midnight")
        void boundaries() {
            ZoneId plus14 = ZoneId.of("Pacific/Kiritimati");
            ZoneId minus12 = ZoneId.of("Etc/GMT+12");
            UUID east = onboard(plus14, null, 60);
            UUID west = onboard(minus12, null, 60);
            UUID minsk = onboard(MINSK, null, 60);
            // 2026-11-10T09:00Z is Nov 10 23:00 in UTC+14, Nov 9 21:00 in UTC−12, Nov 10 12:00 in Minsk
            assertThat(week(east, 1).day1()).isEqualTo(LocalDate.of(2026, 11, 10));
            assertThat(week(west, 1).day1()).isEqualTo(LocalDate.of(2026, 11, 9));
            assertThat(week(minsk, 1).day1()).isEqualTo(LocalDate.of(2026, 11, 10));

            fakes.clock.set(Instant.parse("2026-11-10T09:59:59Z"));
            fakes.rollover.rolloverAll();
            assertThat(week(east, 1).lastRolloverDate()).isNull();
            fakes.clock.set(Instant.parse("2026-11-10T10:00:00Z"));
            fakes.rollover.rolloverAll();
            assertThat(week(east, 1).lastRolloverDate()).isEqualTo(LocalDate.of(2026, 11, 11));
            assertThat(fakes.queries.today(east, null).date()).isEqualTo(LocalDate.of(2026, 11, 11));

            fakes.clock.set(Instant.parse("2026-11-10T11:59:59Z"));
            fakes.rollover.rolloverAll();
            assertThat(week(west, 1).lastRolloverDate()).isNull();
            fakes.clock.set(Instant.parse("2026-11-10T12:00:00Z"));
            fakes.rollover.rolloverAll();
            assertThat(week(west, 1).lastRolloverDate()).isEqualTo(LocalDate.of(2026, 11, 10));

            fakes.clock.set(Instant.parse("2026-11-10T20:59:59Z"));
            fakes.rollover.rolloverAll();
            assertThat(week(minsk, 1).lastRolloverDate()).isNull();
            fakes.clock.set(Instant.parse("2026-11-10T21:00:00Z"));
            fakes.rollover.rolloverAll();
            assertThat(week(minsk, 1).lastRolloverDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        }

        @Test
        @DisplayName("AC-04.04.1: after local midnight the tasks of yesterday are overdue in Today")
        void overdueAfterMidnight() {
            ZoneId plus14 = ZoneId.of("Pacific/Kiritimati");
            UUID user = onboard(plus14, null, 60);
            Today before = fakes.queries.today(user, null);
            assertThat(before.overdue()).isEmpty();
            assertThat(before.today()).isNotEmpty();
            fakes.clock.set(Instant.parse("2026-11-10T10:00:00Z"));
            Today after = fakes.queries.today(user, null);
            assertThat(after.overdue())
                    .extracting(WeekTask::id)
                    .containsExactlyInAnyOrderElementsOf(
                            before.today().stream().map(WeekTask::id).toList());
        }
    }

    @Nested
    @DisplayName("the nightly check")
    class Nightly {

        @Test
        @DisplayName("AC-04.05.1: a week lagging more than 3 days is rebalanced at midnight and published")
        void rebalance() {
            UUID user = onboard(MINSK, null, 60);
            Week first = week(user, 1);
            fakes.attempts.attemptStarted(user, first.tasks().getFirst().id(), UUID.randomUUID(), false, START);
            moveTo(LocalDate.of(2026, 11, 15), MINSK); // day 6: days 1–5 overdue
            fakes.rollover.rolloverAll();
            assertThat(fakes.events.of("week-rebalanced")).hasSize(1);
            Rebalance rebalance =
                    (Rebalance) fakes.events.of("week-rebalanced").getFirst().detail();
            assertThat(rebalance.manual()).isFalse();
            assertThat(week(user, 1).rebalanceCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-04.06.2, AC-04.07.4: day 10 closes the week; Week 2 starts the day after, built and published")
        void dayTen() {
            UUID user = onboard(MINSK, null, 60);
            moveTo(LocalDate.of(2026, 11, 17), MINSK); // day 8
            fakes.rollover.rolloverAll();
            assertThat(week(user, 1).status()).isEqualTo(WeekStatus.PUBLISHED);
            assertThat(week(user, 1).extended(LocalDate.of(2026, 11, 17))).isTrue();
            moveTo(LocalDate.of(2026, 11, 20), MINSK); // day 11
            fakes.rollover.rolloverAll();
            assertThat(week(user, 1).closedBy()).isEqualTo(WeekCloseReason.DAY_10);
            assertThat(week(user, 1).closedOn()).isEqualTo(LocalDate.of(2026, 11, 19));
            assertThat(week(user, 2).day1()).isEqualTo(LocalDate.of(2026, 11, 20));
            assertThat(fakes.events.of("week-completed")).hasSize(1);
            assertThat(fakes.events.of("week-published")).hasSize(2);
            assertThat(plan(user).currentWeekNo()).isEqualTo(2);
        }

        @Test
        @DisplayName("a long pause runs every missed day: two weeks close in one run")
        void longPause() {
            UUID user = onboard(MINSK, null, 60);
            moveTo(LocalDate.of(2026, 11, 30), MINSK); // 20 days later
            fakes.rollover.rolloverAll();
            assertThat(week(user, 1).closedBy()).isEqualTo(WeekCloseReason.DAY_10);
            assertThat(week(user, 2).closedBy()).isEqualTo(WeekCloseReason.DAY_10);
            assertThat(week(user, 3).day1()).isEqualTo(LocalDate.of(2026, 11, 30));
        }

        @Test
        @DisplayName("AC-04.06.3: Finish week early closes at once; the next week starts tomorrow")
        void finishEarly() {
            UUID user = onboard(MINSK, null, 60);
            Week first = week(user, 1);
            assertThatThrownBy(() -> fakes.tasks.finishEarly(user, first.id())).isInstanceOf(WeekRuleException.class);
            scoreAll(user, first, 7);
            fakes.tasks.finishEarly(user, first.id());
            assertThat(week(user, 1).closedBy()).isEqualTo(WeekCloseReason.EARLY);
            assertThat(week(user, 2).day1()).isEqualTo(LocalDate.of(2026, 11, 11));
            Today today = fakes.queries.today(user, null);
            assertThat(today.week()).isNull();
        }
    }

    @Nested
    @DisplayName("commands and queries")
    class Commands {

        @Test
        @DisplayName("AC-04.03.3: a resource is marked done by hand; an item set is not")
        void manual() {
            UUID user = onboard(MINSK, null, 60);
            Week first = week(user, 1);
            WeekTask resource = first.tasks().stream()
                    .filter(task -> task.kind() == WeekTaskKind.EXTERNAL_RESOURCE)
                    .findFirst()
                    .orElseThrow();
            WeekTask set = first.tasks().stream()
                    .filter(task -> task.kind() == WeekTaskKind.ITEM_SET)
                    .findFirst()
                    .orElseThrow();
            assertThat(fakes.tasks.complete(user, resource.id()).status()).isEqualTo(WeekTaskStatus.DONE);
            assertThat(fakes.tasks.uncomplete(user, resource.id()).status()).isEqualTo(WeekTaskStatus.PENDING);
            assertThatThrownBy(() -> fakes.tasks.complete(user, set.id())).isInstanceOf(WeekRuleException.class);
            assertThat(fakes.tasks.skip(user, set.id()).status()).isEqualTo(WeekTaskStatus.SKIPPED);
            assertThatThrownBy(() -> fakes.tasks.complete(UUID.randomUUID(), resource.id()))
                    .isInstanceOfSatisfying(
                            WeekRuleException.class, e -> assertThat(e.code()).isEqualTo("not-found"));
        }

        @Test
        @DisplayName("AC-04.03.5: moving a task needs the version of the week")
        void move() {
            UUID user = onboard(MINSK, null, 60);
            Week first = week(user, 1);
            WeekTask task = first.tasks().getFirst();
            assertThatThrownBy(() -> fakes.tasks.move(user, first.id(), task.id(), first.version() + 1, 3))
                    .isInstanceOf(VersionMismatchException.class);
            assertThat(fakes.tasks
                            .move(user, first.id(), task.id(), first.version(), 3)
                            .dayNo())
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("POST /weeks/{id}/rebalance by the learner; a second one within 48 hours is refused")
        void manualRebalance() {
            UUID user = onboard(MINSK, null, 60);
            Week first = week(user, 1);
            moveTo(LocalDate.of(2026, 11, 15), MINSK);
            fakes.tasks.rebalance(user, first.id());
            assertThat(fakes.events.of("week-rebalanced")).hasSize(1);
            assertThatThrownBy(() -> fakes.tasks.rebalance(user, first.id())).isInstanceOf(WeekRuleException.class);
        }

        @Test
        @DisplayName("AC-05.01.2: when the day is done Today offers two tasks of tomorrow")
        void getAhead() {
            UUID user = onboard(MINSK, null, 60);
            scoreAll(user, week(user, 1), 1);
            Today today = fakes.queries.today(user, null);
            assertThat(today.today())
                    .allSatisfy(task -> assertThat(task.status()).isEqualTo(WeekTaskStatus.DONE));
            assertThat(today.getAhead())
                    .hasSizeBetween(1, 2)
                    .allSatisfy(task -> assertThat(task.dayNo()).isEqualTo(2));
        }

        @Test
        @DisplayName("queries of a learner without a plan and the erasure of a learner")
        void emptyAndErasure() {
            UUID nobody = UUID.randomUUID();
            assertThat(fakes.queries.today(nobody, null).week()).isNull();
            assertThat(fakes.queries.weeks(nobody)).isEmpty();
            assertThat(fakes.queries.currentWeek(nobody)).isEmpty();
            UUID user = onboard(MINSK, null, 60);
            assertThat(fakes.queries.currentWeek(user)).isPresent();
            assertThat(fakes.queries.export(user)).isPresent();
            assertThat(fakes.queries.week(UUID.randomUUID(), week(user, 1).id()))
                    .isEmpty();
            assertThat(fakes.queries.weekById(week(user, 1).id())).isPresent();
            fakes.sync.deleteUser(user);
            assertThat(fakes.plans.findByUser(user)).isEmpty();
            assertThat(fakes.weeks.byId).isEmpty();
        }

        @Test
        @DisplayName("the catalog follows published and hidden sets")
        void catalog() {
            UUID set = UUID.randomUUID();
            fakes.sync.itemSetPublished(set, 1, TaskType.READ_DAILY_LIFE, "TOEFL_2026", 0, 2.5, "campus", null, START);
            assertThat(fakes.catalog.visible(TaskType.READ_DAILY_LIFE))
                    .anyMatch(entry -> entry.itemSetId().equals(set));
            fakes.sync.itemSetHidden(set, START);
            assertThat(fakes.catalog.visible(TaskType.READ_DAILY_LIFE))
                    .noneMatch(entry -> entry.itemSetId().equals(set));
        }
    }
}
