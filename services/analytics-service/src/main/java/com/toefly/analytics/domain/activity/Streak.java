package com.toefly.analytics.domain.activity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * {@code StreakView} (docs/03 §6.15): the run of days with the daily goal met. Today does not break the streak until the
 * learner's midnight; once today's goal is met it counts at once ("Today done", AC-19.01.1, AC-19.01.2).
 */
public record Streak(int current, int longest, boolean todayDone, int goalMinutes, List<CalendarDay> lastDays) {

    /** Days of the calendar on Home (AC-19.01.3). */
    public static final int CALENDAR_DAYS = 30;

    public Streak {
        lastDays = List.copyOf(lastDays);
    }

    public record CalendarDay(LocalDate date, boolean done) {}

    public static Streak of(Collection<LocalDate> doneDays, LocalDate today, int goalMinutes) {
        NavigableSet<LocalDate> done = new TreeSet<>(doneDays);
        boolean todayDone = done.contains(today);
        LocalDate end = todayDone ? today : today.minusDays(1);
        int current = 0;
        for (LocalDate day = end; done.contains(day); day = day.minusDays(1)) {
            current++;
        }
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : done) {
            if (day.isAfter(today)) {
                break;
            }
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }
        List<CalendarDay> calendar = new ArrayList<>(CALENDAR_DAYS);
        for (int i = CALENDAR_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            calendar.add(new CalendarDay(day, done.contains(day)));
        }
        return new Streak(current, Math.max(longest, current), todayDone, goalMinutes, calendar);
    }
}
