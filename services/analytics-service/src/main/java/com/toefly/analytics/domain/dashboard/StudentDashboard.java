package com.toefly.analytics.domain.dashboard;

import com.toefly.analytics.domain.common.Section;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code StudentDashboardView} (docs/03 §6.15): the current week, the band estimate, θ by section and the last results.
 * Every change is monotonic (INV-ANL-02): a later week wins over an earlier one, and within one value an older event
 * never overwrites a newer one, so incremental processing and a rebuild from the journal give the same view.
 */
public record StudentDashboard(
        UUID userId,
        @Nullable CurrentWeek currentWeek,
        @Nullable BandSnapshot band,
        Map<Section, Theta> theta,
        List<RecentResult> lastResults,
        @Nullable Instant asOf) {

    /** How many results the dashboard keeps. */
    public static final int LAST_RESULTS = 5;

    public StudentDashboard {
        theta = theta.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(theta));
        lastResults = List.copyOf(lastResults);
    }

    public static StudentDashboard empty(UUID userId) {
        return new StudentDashboard(userId, null, null, Map.of(), List.of(), null);
    }

    public StudentDashboard weekPublished(UUID weekId, int weekNo, LocalDate day1, int tasksTotal, Instant at) {
        CurrentWeek week = currentWeek;
        if (week == null || weekNo > week.weekNo()) {
            return withWeek(new CurrentWeek(weekId, weekNo, day1, tasksTotal, 0, false, at), at);
        }
        if (week.weekId().equals(weekId)) {
            return withWeek(
                    new CurrentWeek(
                            weekId, weekNo, day1, tasksTotal, week.progressPercent(), week.completed(), week.asOf()),
                    at);
        }
        return touch(at);
    }

    public StudentDashboard weekProgress(UUID weekId, int weekNo, double progressPercent, Instant at) {
        CurrentWeek week = currentWeek;
        if (week == null || weekNo > week.weekNo()) {
            return withWeek(new CurrentWeek(weekId, weekNo, null, 0, progressPercent, false, at), at);
        }
        if (week.weekId().equals(weekId) && !week.completed() && !at.isBefore(week.asOf())) {
            return withWeek(
                    new CurrentWeek(weekId, weekNo, week.day1(), week.tasksTotal(), progressPercent, false, at), at);
        }
        return touch(at);
    }

    public StudentDashboard weekCompleted(
            UUID weekId, int weekNo, LocalDate day1, int tasksTotal, double progressPercent, Instant at) {
        CurrentWeek week = currentWeek;
        if (week == null || weekNo > week.weekNo() || week.weekId().equals(weekId)) {
            return withWeek(new CurrentWeek(weekId, weekNo, day1, tasksTotal, progressPercent, true, at), at);
        }
        return touch(at);
    }

    public StudentDashboard band(BandSnapshot snapshot) {
        if (band != null && snapshot.asOf().isBefore(band.asOf())) {
            return touch(snapshot.asOf());
        }
        return new StudentDashboard(userId, currentWeek, snapshot, theta, lastResults, later(snapshot.asOf()));
    }

    public StudentDashboard ability(Section section, double value, double standardError, Instant at) {
        Theta known = theta.get(section);
        if (known != null && at.isBefore(known.asOf())) {
            return touch(at);
        }
        Map<Section, Theta> next = new EnumMap<>(Section.class);
        next.putAll(theta);
        next.put(section, new Theta(value, standardError, at));
        return new StudentDashboard(userId, currentWeek, band, next, lastResults, later(at));
    }

    /** Adds a result, keeps the newest version per id and the {@link #LAST_RESULTS} newest results. */
    public StudentDashboard result(RecentResult result) {
        List<RecentResult> next = new ArrayList<>();
        boolean newer = true;
        for (RecentResult known : lastResults) {
            if (known.id().equals(result.id())) {
                newer = !result.at().isBefore(known.at());
                if (!newer) {
                    next.add(known);
                }
            } else {
                next.add(known);
            }
        }
        if (newer) {
            next.add(result);
        }
        next.sort(Comparator.comparing(RecentResult::at).reversed().thenComparing(r -> r.id().toString()));
        List<RecentResult> kept = next.size() > LAST_RESULTS ? next.subList(0, LAST_RESULTS) : next;
        return new StudentDashboard(userId, currentWeek, band, theta, kept, later(result.at()));
    }

    private StudentDashboard withWeek(CurrentWeek week, Instant at) {
        return new StudentDashboard(userId, week, band, theta, lastResults, later(at));
    }

    private StudentDashboard touch(Instant at) {
        return new StudentDashboard(userId, currentWeek, band, theta, lastResults, later(at));
    }

    private Instant later(Instant at) {
        return asOf == null || at.isAfter(asOf) ? at : asOf;
    }
}
