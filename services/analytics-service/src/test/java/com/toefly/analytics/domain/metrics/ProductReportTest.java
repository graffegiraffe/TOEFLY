package com.toefly.analytics.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductReportTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    static final LocalDate FROM = TODAY.minusDays(60);
    static final LocalDate TO = TODAY.plusDays(1);

    @Test
    @DisplayName("docs/01 §7.2: the funnel and activation count each learner of the cohort once")
    void funnel() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<LearnerMilestones> learners = List.of(
                LearnerMilestones.none(a)
                        .registered(TODAY.minusDays(10))
                        .onboarded(TODAY.minusDays(10))
                        .planCreated(TODAY.minusDays(10))
                        .taskDone(TODAY.minusDays(9)),
                LearnerMilestones.none(b)
                        .registered(TODAY.minusDays(10))
                        .onboarded(TODAY.minusDays(8))
                        .taskDone(TODAY.minusDays(5)),
                LearnerMilestones.none(c).registered(TODAY.minusDays(100)));

        ProductReport report = ProductReport.of(FROM, TO, TODAY, List.of(), learners, Map.of());

        assertThat(report.funnel()).isEqualTo(new ProductReport.Funnel(2, 2, 1, 2, 1));
    }

    @Test
    @DisplayName("docs/01 §7.2: retention D1/D7/D28 counts learners with a study day in the window, if it has begun")
    void retention() {
        UUID kept = UUID.randomUUID();
        UUID lost = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        LocalDate registered = TODAY.minusDays(40);
        List<LearnerMilestones> learners = List.of(
                LearnerMilestones.none(kept).registered(registered),
                LearnerMilestones.none(lost).registered(registered),
                LearnerMilestones.none(fresh).registered(TODAY.minusDays(3)));
        Map<UUID, Set<LocalDate>> studied = Map.of(
                kept, Set.of(registered.plusDays(1), registered.plusDays(8), registered.plusDays(30)),
                lost, Set.of(registered.plusDays(1)),
                fresh, Set.of(TODAY.minusDays(2)));

        ProductReport report = ProductReport.of(FROM, TO, TODAY, List.of(), learners, studied);

        assertThat(report.retention())
                .extracting(
                        ProductReport.Retention::name,
                        ProductReport.Retention::eligible,
                        ProductReport.Retention::retained)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("D1", 3, 3),
                        org.assertj.core.groups.Tuple.tuple("D7", 2, 1),
                        org.assertj.core.groups.Tuple.tuple("D28", 2, 1));
        assertThat(report.retention().get(1).rate()).isEqualTo(0.5);
        assertThat(new ProductReport.Retention("D7", 7, 13, 0, 0).rate()).isNull();
    }

    @Test
    @DisplayName("docs/01 §7.1: North Star — median SDR over learners with a plan and a study day in the window")
    void studyDaysRatio() {
        UUID old = UUID.randomUUID();
        UUID young = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        UUID noPlan = UUID.randomUUID();
        List<LearnerMilestones> learners = List.of(
                LearnerMilestones.none(old).planCreated(TODAY.minusDays(100)),
                LearnerMilestones.none(young).planCreated(TODAY.minusDays(3)),
                LearnerMilestones.none(idle).planCreated(TODAY.minusDays(50)),
                LearnerMilestones.none(noPlan));
        Set<LocalDate> fourteen = new java.util.HashSet<>();
        for (int i = 0; i < 14; i++) {
            fourteen.add(TODAY.minusDays(i * 2L));
        }
        Map<UUID, Set<LocalDate>> studied = Map.of(
                old, fourteen,
                young, Set.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3)),
                idle, Set.of(TODAY.minusDays(40)),
                noPlan, Set.of(TODAY));

        ProductReport report = ProductReport.of(FROM, TO, TODAY, List.of(), learners, studied);

        assertThat(report.learnersInSdr()).isEqualTo(2);
        assertThat(report.medianStudyDaysRatio()).isEqualTo((0.5 + 1.0) / 2);
        assertThat(ProductReport.median(List.of(0.2, 0.9, 0.4))).isEqualTo(0.4);
        assertThat(ProductReport.median(List.of())).isNull();
    }

    @Test
    @DisplayName("docs/01 §7.2: week completion is the share of weeks closed by 70% progress")
    void weekCompletion() {
        ProductCounters day = ProductCounters.empty(TODAY)
                .plus(ProductCounters.Counter.WEEKS_COMPLETED, 4)
                .plus(ProductCounters.Counter.WEEKS_COMPLETED_BY_PROGRESS, 3)
                .plus(ProductCounters.Counter.NEW_USERS, 2)
                .plus(ProductCounters.Counter.ONBOARDED, 2)
                .plus(ProductCounters.Counter.PLANS_CREATED, 1)
                .plus(ProductCounters.Counter.FIRST_TASKS, 1)
                .plus(ProductCounters.Counter.ATTEMPTS_SUBMITTED, 7);
        ProductCounters outside =
                ProductCounters.empty(TODAY.minusDays(90)).plus(ProductCounters.Counter.WEEKS_COMPLETED, 9);

        ProductReport report = ProductReport.of(FROM, TO, TODAY, List.of(day, outside), List.of(), Map.of());

        assertThat(report.weekCompletionRate()).isEqualTo(0.75);
        assertThat(report.daily()).containsExactly(new ProductCounters(TODAY, 2, 2, 1, 1, 7, 4, 3));
        assertThat(ProductReport.of(FROM, TO, TODAY, List.of(), List.of(), Map.of())
                        .weekCompletionRate())
                .isNull();
    }

    @Test
    @DisplayName("INV-ANL-01: each funnel step keeps its earliest day; only an earlier day moves the counter")
    void milestonesKeepEarliest() {
        UUID user = UUID.randomUUID();
        LearnerMilestones m = LearnerMilestones.none(user).registered(TODAY).registered(TODAY.minusDays(1));

        assertThat(m.registeredOn()).isEqualTo(TODAY.minusDays(1));
        assertThat(LearnerMilestones.moved(TODAY, TODAY)).isFalse();
        assertThat(LearnerMilestones.moved(null, TODAY)).isTrue();
        assertThat(LearnerMilestones.moved(TODAY, null)).isFalse();
    }
}
