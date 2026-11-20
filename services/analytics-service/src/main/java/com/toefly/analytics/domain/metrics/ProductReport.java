package com.toefly.analytics.domain.metrics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Product metrics of a period (docs/01 §7): the funnel of the learners registered in it, retention D1/D7/D28 by "day
 * with study" (the daily goal met), the share of weeks closed by progress and the North Star median SDR.
 */
public record ProductReport(
        LocalDate from,
        LocalDate to,
        Funnel funnel,
        List<Retention> retention,
        @Nullable Double weekCompletionRate,
        @Nullable Double medianStudyDaysRatio,
        int learnersInSdr,
        List<ProductCounters> daily) {

    /** Window of the Study Days Ratio (docs/01 §7.1). */
    public static final int SDR_WINDOW_DAYS = 28;

    /** Activation window: the first task within 48 hours of the registration, counted in days. */
    public static final int ACTIVATION_DAYS = 2;

    public ProductReport {
        retention = List.copyOf(retention);
        daily = List.copyOf(daily);
    }

    public record Funnel(int registered, int onboarded, int planCreated, int firstTaskDone, int activated) {}

    /** Share of the eligible cohort with a study day in {@code [startDay, endDay]} after the registration. */
    public record Retention(String name, int startDay, int endDay, int eligible, int retained) {

        public @Nullable Double rate() {
            return eligible == 0 ? null : (double) retained / eligible;
        }
    }

    /**
     * @param milestones funnel steps of the learners known to the service
     * @param studyDays days with the goal met by learner
     */
    public static ProductReport of(
            LocalDate from,
            LocalDate to,
            LocalDate today,
            Collection<ProductCounters> counters,
            Collection<LearnerMilestones> milestones,
            Map<UUID, Set<LocalDate>> studyDays) {
        List<LearnerMilestones> cohort = milestones.stream()
                .filter(m -> m.registeredOn() != null
                        && !m.registeredOn().isBefore(from)
                        && m.registeredOn().isBefore(to))
                .toList();
        Funnel funnel = new Funnel(
                cohort.size(),
                (int) cohort.stream().filter(m -> m.onboardedOn() != null).count(),
                (int) cohort.stream().filter(m -> m.planCreatedOn() != null).count(),
                (int) cohort.stream().filter(m -> m.firstTaskOn() != null).count(),
                (int) cohort.stream().filter(ProductReport::activated).count());
        List<Retention> retention = List.of(
                retention("D1", 1, 1, cohort, studyDays, today),
                retention("D7", 7, 13, cohort, studyDays, today),
                retention("D28", 28, 34, cohort, studyDays, today));
        List<ProductCounters> daily = counters.stream()
                .filter(c -> !c.day().isBefore(from) && c.day().isBefore(to))
                .sorted(Comparator.comparing(ProductCounters::day))
                .toList();
        int closed = daily.stream().mapToInt(ProductCounters::weeksCompleted).sum();
        int byProgress = daily.stream()
                .mapToInt(ProductCounters::weeksCompletedByProgress)
                .sum();
        List<Double> ratios = studyDaysRatios(milestones, studyDays, today);
        return new ProductReport(
                from,
                to,
                funnel,
                retention,
                closed == 0 ? null : (double) byProgress / closed,
                median(ratios),
                ratios.size(),
                daily);
    }

    private static boolean activated(LearnerMilestones m) {
        LocalDate registered = m.registeredOn();
        return registered != null
                && m.onboardedOn() != null
                && m.firstTaskOn() != null
                && !m.firstTaskOn().isAfter(registered.plusDays(ACTIVATION_DAYS));
    }

    private static Retention retention(
            String name,
            int start,
            int end,
            List<LearnerMilestones> cohort,
            Map<UUID, Set<LocalDate>> studyDays,
            LocalDate today) {
        int eligible = 0;
        int retained = 0;
        for (LearnerMilestones learner : cohort) {
            LocalDate registered = learner.registeredOn();
            if (registered == null || registered.plusDays(start).isAfter(today)) {
                continue;
            }
            eligible++;
            LocalDate first = registered.plusDays(start);
            LocalDate last = registered.plusDays(end);
            boolean studied = studyDays.getOrDefault(learner.userId(), Set.of()).stream()
                    .anyMatch(day -> !day.isBefore(first) && !day.isAfter(last));
            if (studied) {
                retained++;
            }
        }
        return new Retention(name, start, end, eligible, retained);
    }

    /**
     * SDR of each learner with a plan and at least one study day in the window: days with study divided by the days of
     * the window, the last 28 days or since the plan started if it is younger (docs/01 §7.1).
     */
    static List<Double> studyDaysRatios(
            Collection<LearnerMilestones> milestones, Map<UUID, Set<LocalDate>> studyDays, LocalDate today) {
        List<Double> ratios = new ArrayList<>();
        for (LearnerMilestones learner : milestones) {
            LocalDate planned = learner.planCreatedOn();
            if (planned == null || planned.isAfter(today)) {
                continue;
            }
            LocalDate windowStart = today.minusDays(SDR_WINDOW_DAYS - 1L);
            if (planned.isAfter(windowStart)) {
                windowStart = planned;
            }
            LocalDate start = windowStart;
            long days = ChronoUnit.DAYS.between(start, today) + 1;
            long studied = studyDays.getOrDefault(learner.userId(), Set.of()).stream()
                    .filter(day -> !day.isBefore(start) && !day.isAfter(today))
                    .count();
            if (studied > 0) {
                ratios.add((double) studied / days);
            }
        }
        return ratios;
    }

    static @Nullable Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }
}
