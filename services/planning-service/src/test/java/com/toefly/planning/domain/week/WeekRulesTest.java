package com.toefly.planning.domain.week;

import static com.toefly.planning.domain.week.Weeks.day;
import static com.toefly.planning.domain.week.Weeks.midnightUtc;
import static com.toefly.planning.domain.week.Weeks.week;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The table of the rules of a week (phase 4 DoD): carry-over, lag over 3 days, 70%, day 10. */
class WeekRulesTest {

    private static Week sevenFullDays() {
        Weeks builder = week();
        for (int day = 1; day <= 7; day++) {
            builder.fullDay(day);
        }
        return builder.build();
    }

    @Nested
    @DisplayName("carry-over of missed days")
    class CarryOver {

        @Test
        @DisplayName("AC-04.04.1: open tasks of past days are overdue and keep their day")
        void overdueKeepsDay() {
            Week week = sevenFullDays();
            List<WeekTask> overdue = week.overdue(day(3));
            assertThat(overdue)
                    .hasSize(20)
                    .allSatisfy(task -> assertThat(task.dayNo()).isLessThan(3));
            assertThat(week.planned(day(3)))
                    .hasSize(10)
                    .allSatisfy(task -> assertThat(task.dayNo()).isEqualTo(3));
        }

        @Test
        @DisplayName("AC-04.04.2: lagDays is the overdue time divided by the daily minutes")
        void lagDays() {
            Week week = sevenFullDays();
            assertThat(week.lagDays(day(1))).isZero();
            assertThat(week.lagDays(day(4))).isEqualTo(3.0);
            Weeks.doDays(week, 1);
            assertThat(week.lagDays(day(4))).isEqualTo(2.0);
        }

        @Test
        @DisplayName("AC-04.03.2: a task of day 5 done on day 2 counts and leaves day 5")
        void anyOrder() {
            Week week = sevenFullDays();
            WeekTask later = week.planned(day(5)).getFirst();
            week.complete(later.id(), UUID.randomUUID(), day(2), midnightUtc(2));
            assertThat(week.task(later.id()).status()).isEqualTo(WeekTaskStatus.DONE);
            assertThat(week.planned(day(5)).stream()
                            .filter(task -> task.status().open()))
                    .hasSize(9);
            assertThat(week.progress()).isGreaterThan(0);
        }

        @Test
        @DisplayName("AC-04.03.5: moving a task changes its day only")
        void move() {
            Week week = sevenFullDays();
            WeekTask task = week.planned(day(1)).getFirst();
            week.move(task.id(), 4);
            assertThat(week.task(task.id()).dayNo()).isEqualTo(4);
            assertThatThrownBy(() -> week.move(task.id(), 11)).isInstanceOf(WeekRuleException.class);
        }
    }

    @Nested
    @DisplayName("rebalance when the lag is over 3 days")
    class RebalanceRule {

        @Test
        @DisplayName("AC-04.05.1: lag over 3 days skips tasks for REBALANCE and spreads the rest up to day 7")
        void rebalances() {
            Week week = sevenFullDays();
            Weeks.doDays(week, 0);
            week.start(week.planned(day(1)).getFirst().id(), UUID.randomUUID(), midnightUtc(1));
            // day 5 starts: days 1–4 are overdue, 4 days of lag
            Rebalance rebalance = week.rebalance(day(5), midnightUtc(5), false).orElseThrow();

            assertThat(rebalance.lagDays()).isEqualTo(4.0);
            // 7 days of work, 3 days left: 4 days of tasks leave, the running one stays
            assertThat(rebalance.skipped()).isNotEmpty();
            double pending = week.tasks().stream()
                    .filter(task -> task.status().open())
                    .mapToDouble(WeekTask::estimatedMinutes)
                    .sum();
            assertThat(pending).isLessThanOrEqualTo(3 * 60.0 + 1e-9);
            assertThat(week.tasks())
                    .filteredOn(task -> task.status() == WeekTaskStatus.PENDING)
                    .allSatisfy(task -> assertThat(task.dayNo()).isBetween(5, 7));
            assertThat(week.tasks())
                    .filteredOn(task -> task.status() == WeekTaskStatus.IN_PROGRESS)
                    .hasSize(1);
            assertThat(week.rebalanceCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-04.05.1: never the mock exam, a task of the tutor or a vocabulary review")
        void protectedTasks() {
            Week week = week().fullDay(1)
                    .fullDay(2)
                    .fullDay(3)
                    .fullDay(4)
                    .fullDay(5)
                    .task(1, WeekTaskKind.MOCK_EXAM, null, 90, TaskOrigin.SYSTEM, false)
                    .task(1, WeekTaskKind.TUTOR_MATERIAL, null, 30, TaskOrigin.TUTOR, false)
                    .task(1, WeekTaskKind.VOCABULARY_REVIEW, null, 5, TaskOrigin.SYSTEM, false)
                    .build();
            week.rebalance(day(6), midnightUtc(6), false).orElseThrow();
            assertThat(week.tasks())
                    .filteredOn(task -> task.kind() != WeekTaskKind.ITEM_SET)
                    .allSatisfy(task -> assertThat(task.status()).isEqualTo(WeekTaskStatus.PENDING));
        }

        @Test
        @DisplayName("AC-04.05.1: the order of exclusion starts with repeats and strong types")
        void exclusionOrder() {
            Week week = week().weak(TaskType.READ_ACADEMIC_PASSAGE)
                    .fullDay(1)
                    .fullDay(2)
                    .fullDay(3)
                    .fullDay(4)
                    .task(5, WeekTaskKind.ITEM_SET, TaskType.LISTEN_CONVERSATION, 2, TaskOrigin.SYSTEM, true)
                    .build();
            Rebalance rebalance = week.rebalance(day(5), midnightUtc(5), false).orElseThrow();
            UUID repeat = week.tasks().stream()
                    .filter(WeekTask::repeat)
                    .findFirst()
                    .orElseThrow()
                    .id();
            assertThat(rebalance.skipped().getFirst()).isEqualTo(repeat);
            assertThat(rebalance.skipped().subList(1, 4))
                    .allSatisfy(id -> assertThat(week.task(id).taskType()).isEqualTo(TaskType.BUILD_A_SENTENCE));
        }

        @Test
        @DisplayName("AC-04.05.1: a lag of exactly 3 days does not rebalance")
        void threeDaysIsNotEnough() {
            Week week = sevenFullDays();
            assertThat(week.rebalance(day(4), midnightUtc(4), false)).isEmpty();
        }

        @Test
        @DisplayName("AC-04.05.4: not again within 48 hours; a manual request then fails with the next moment")
        void interval() {
            Week week = sevenFullDays();
            week.rebalance(day(5), midnightUtc(5), false).orElseThrow();
            assertThat(week.rebalance(day(6), midnightUtc(6), false)).isEmpty();
            assertThatThrownBy(() -> week.rebalance(day(6), midnightUtc(6), true))
                    .isInstanceOfSatisfying(
                            RebalanceTooSoonException.class,
                            tooSoon -> assertThat(tooSoon.nextAllowedAt())
                                    .isEqualTo(midnightUtc(5).plus(Duration.ofHours(48))));
        }

        @Test
        @DisplayName("AC-04.05.2: excluded tasks can still be done; AC-04.02.3: they leave the denominator")
        void excludedStillOpen() {
            Week week = sevenFullDays();
            Rebalance rebalance = week.rebalance(day(5), midnightUtc(5), false).orElseThrow();
            double before = week.progress();
            UUID excluded = rebalance.skipped().getFirst();
            assertThat(week.task(excluded).skippedReason()).isEqualTo(WeekTaskSkipReason.REBALANCE);
            week.complete(excluded, UUID.randomUUID(), day(5), midnightUtc(5));
            assertThat(week.task(excluded).status()).isEqualTo(WeekTaskStatus.DONE);
            assertThat(week.progress()).isGreaterThan(before);
        }

        @Test
        @DisplayName("in the extension the rest of the week spreads up to day 10")
        void extensionUpToDay10() {
            Week week = sevenFullDays();
            week.rebalance(day(8), midnightUtc(8), false).orElseThrow();
            assertThat(week.tasks())
                    .filteredOn(task -> task.status() == WeekTaskStatus.PENDING)
                    .allSatisfy(task -> assertThat(task.dayNo()).isBetween(8, 10));
        }
    }

    @Nested
    @DisplayName("close at 70% and after day 10")
    class Close {

        @Test
        @DisplayName("AC-04.06.1: 70% done after day 7 closes at midnight; open tasks get WEEK_CLOSE")
        void closesAt70() {
            Week week = sevenFullDays();
            Weeks.doDays(week, 5); // 5 of 7 days: 71%
            assertThat(week.closeIfDue(day(7), midnightUtc(7))).isEmpty();
            assertThat(week.closeIfDue(day(8), midnightUtc(8))).contains(WeekCloseReason.PROGRESS_70);
            assertThat(week.status()).isEqualTo(WeekStatus.COMPLETED);
            assertThat(week.closedOn()).isEqualTo(day(7));
            assertThat(week.tasks())
                    .filteredOn(task -> task.status() != WeekTaskStatus.DONE)
                    .allSatisfy(task -> assertThat(task.skippedReason()).isEqualTo(WeekTaskSkipReason.WEEK_CLOSE));
        }

        @Test
        @DisplayName("AC-04.06.2: under 70% the week runs on to day 10 and closes at the first midnight with 70%")
        void extends_() {
            Week week = sevenFullDays();
            Weeks.doDays(week, 4); // 57%
            assertThat(week.closeIfDue(day(8), midnightUtc(8))).isEmpty();
            assertThat(week.extended(day(8))).isTrue();
            assertThat(week.dayCount(day(8))).isEqualTo(8);
            Weeks.doDays(week, 5);
            assertThat(week.closeIfDue(day(9), midnightUtc(9))).contains(WeekCloseReason.PROGRESS_70);
            assertThat(week.closedOn()).isEqualTo(day(8));
            assertThat(week.dayCount(day(12))).isEqualTo(8);
        }

        @Test
        @DisplayName("AC-04.06.2: day 10 ends the week below 70%")
        void day10() {
            Week week = sevenFullDays();
            Weeks.doDays(week, 2);
            assertThat(week.closeIfDue(day(10), midnightUtc(10))).isEmpty();
            assertThat(week.closeIfDue(day(11), midnightUtc(11))).contains(WeekCloseReason.DAY_10);
            assertThat(week.closedOn()).isEqualTo(day(10));
            assertThat(week.progressPercent()).isEqualTo(28);
        }

        @Test
        @DisplayName(
                "AC-04.06.4: the first started task moves PUBLISHED to IN_PROGRESS; untouched weeks close at day 10")
        void publishedWeek() {
            Week week = week().status(WeekStatus.PUBLISHED).fullDay(1).build();
            assertThat(week.closeIfDue(day(8), midnightUtc(8))).isEmpty();
            assertThat(week.closeIfDue(day(11), midnightUtc(11))).contains(WeekCloseReason.DAY_10);
            assertThat(week.progressPercent()).isZero();

            Week started = week().status(WeekStatus.PUBLISHED).fullDay(1).build();
            started.start(started.tasks().getFirst().id(), UUID.randomUUID(), Weeks.NOW);
            assertThat(started.status()).isEqualTo(WeekStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("AC-04.06.3: Finish week early needs 100% before the end of day 7")
        void finishEarly() {
            Week week = week().fullDay(1).fullDay(2).build();
            assertThatThrownBy(() -> week.finishEarly(day(2), Weeks.NOW))
                    .isInstanceOfSatisfying(
                            WeekRuleException.class, e -> assertThat(e.code()).isEqualTo("week-not-completable"));
            Weeks.doDays(week, 2);
            week.finishEarly(day(3), Weeks.NOW);
            assertThat(week.closedBy()).isEqualTo(WeekCloseReason.EARLY);
            assertThat(week.closedOn()).isEqualTo(day(3));
        }

        @Test
        @DisplayName("AC-04.06.5, INV-WK-06: a closed week does not change")
        void readOnly() {
            Week week = sevenFullDays();
            week.closeIfDue(day(11), midnightUtc(11));
            UUID task = week.tasks().getFirst().id();
            assertThat(week.complete(task, UUID.randomUUID(), day(11), Instant.now()))
                    .isEmpty();
            assertThat(week.start(task, UUID.randomUUID(), Instant.now())).isFalse();
            assertThatThrownBy(() -> week.skip(task)).isInstanceOf(WeekRuleException.class);
            assertThat(week.rebalance(day(11), Instant.now(), false)).isEmpty();
            assertThatThrownBy(() -> week.rebalance(day(11), Instant.now(), true))
                    .isInstanceOf(WeekRuleException.class);
        }
    }

    @Nested
    @DisplayName("tasks")
    class Tasks {

        @Test
        @DisplayName("AC-04.02.3: USER_SKIPPED and WEEK_CLOSE stay in the denominator")
        void userSkipCounts() {
            Week week = week().set(1, TaskType.READ_DAILY_LIFE, 10)
                    .set(1, TaskType.COMPLETE_THE_WORDS, 10)
                    .build();
            List<WeekTask> tasks = week.tasks();
            week.skip(tasks.get(0).id());
            week.complete(tasks.get(1).id(), UUID.randomUUID(), day(1), Weeks.NOW);
            assertThat(week.progressPercent()).isEqualTo(50);
        }

        @Test
        @DisplayName("AC-04.03.3: an item set is not marked done by hand; a resource is")
        void manualCompletion() {
            Week week = week().set(1, TaskType.READ_DAILY_LIFE, 3)
                    .task(1, WeekTaskKind.EXTERNAL_RESOURCE, null, 15, TaskOrigin.SYSTEM, false)
                    .build();
            UUID set = week.tasks().stream()
                    .filter(task -> task.kind() == WeekTaskKind.ITEM_SET)
                    .findFirst()
                    .orElseThrow()
                    .id();
            UUID resource = week.tasks().stream()
                    .filter(task -> task.kind() == WeekTaskKind.EXTERNAL_RESOURCE)
                    .findFirst()
                    .orElseThrow()
                    .id();
            assertThatThrownBy(() -> week.completeManually(set, day(1), Weeks.NOW))
                    .isInstanceOfSatisfying(
                            WeekRuleException.class, e -> assertThat(e.code()).isEqualTo("week-task-not-completable"));
            assertThat(week.completeManually(resource, day(1), Weeks.NOW)).isPresent();
            assertThat(week.completeManually(resource, day(1), Weeks.NOW)).isEmpty();
        }

        @Test
        @DisplayName("INV-WK-05: only an external resource goes back from DONE")
        void uncomplete() {
            Week week = week().set(1, TaskType.READ_DAILY_LIFE, 3)
                    .task(1, WeekTaskKind.EXTERNAL_RESOURCE, null, 15, TaskOrigin.SYSTEM, false)
                    .build();
            for (WeekTask task : week.tasks()) {
                week.complete(task.id(), null, day(1), Weeks.NOW);
            }
            WeekTask resource = week.tasks().stream()
                    .filter(task -> task.kind() == WeekTaskKind.EXTERNAL_RESOURCE)
                    .findFirst()
                    .orElseThrow();
            WeekTask set = week.tasks().stream()
                    .filter(task -> task.kind() == WeekTaskKind.ITEM_SET)
                    .findFirst()
                    .orElseThrow();
            assertThat(week.uncomplete(resource.id()).status()).isEqualTo(WeekTaskStatus.PENDING);
            assertThatThrownBy(() -> week.uncomplete(set.id())).isInstanceOf(WeekRuleException.class);
        }

        @Test
        @DisplayName("an attempt without a score for 7 days returns the task to PENDING")
        void abandoned() {
            Week week = week().set(1, TaskType.READ_DAILY_LIFE, 3).build();
            UUID task = week.tasks().getFirst().id();
            week.start(task, UUID.randomUUID(), Weeks.NOW);
            assertThat(week.releaseAbandoned(Weeks.NOW.plus(Duration.ofDays(6))))
                    .isEmpty();
            assertThat(week.releaseAbandoned(Weeks.NOW.plus(Duration.ofDays(7))))
                    .hasSize(1);
            assertThat(week.task(task).status()).isEqualTo(WeekTaskStatus.PENDING);
        }

        @Test
        @DisplayName("AC-06.01.7: Try again on a done task changes nothing")
        void retry() {
            Week week = week().set(1, TaskType.READ_DAILY_LIFE, 3).build();
            UUID task = week.tasks().getFirst().id();
            week.complete(task, UUID.randomUUID(), day(1), Weeks.NOW);
            assertThat(week.start(task, UUID.randomUUID(), Weeks.NOW)).isFalse();
            assertThat(week.task(task).status()).isEqualTo(WeekTaskStatus.DONE);
        }
    }
}
