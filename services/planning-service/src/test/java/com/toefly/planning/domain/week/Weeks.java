package com.toefly.planning.domain.week;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Weeks with tasks on fixed days for the rule tests. */
final class Weeks {

    static final LocalDate DAY1 = LocalDate.of(2026, 11, 2); // a Monday
    static final Instant NOW = Instant.parse("2026-11-02T06:00:00Z");

    private final List<WeekTask> tasks = new ArrayList<>();
    private int minutesPerDay = 60;
    private WeekStatus status = WeekStatus.IN_PROGRESS;
    private List<TaskType> weak = List.of();

    static Weeks week() {
        return new Weeks();
    }

    Weeks minutes(int value) {
        minutesPerDay = value;
        return this;
    }

    Weeks status(WeekStatus value) {
        status = value;
        return this;
    }

    Weeks weak(TaskType... types) {
        weak = List.of(types);
        return this;
    }

    /** An item set of {@code type} with {@code minutes} on {@code day}. */
    Weeks set(int day, TaskType type, double minutes) {
        return task(day, WeekTaskKind.ITEM_SET, type, minutes, TaskOrigin.SYSTEM, false);
    }

    Weeks task(int day, WeekTaskKind kind, TaskType type, double minutes, TaskOrigin origin, boolean repeat) {
        WeekSection section = type != null ? type.section() : sectionOf(kind);
        tasks.add(new WeekTask(
                UUID.randomUUID(),
                kind,
                section,
                type,
                UUID.randomUUID().toString(),
                kind + " " + (type == null ? "" : type.label()) + " " + tasks.size(),
                minutes,
                origin,
                null,
                repeat,
                WeekTaskStatus.PENDING,
                null,
                day,
                tasks.size() + 1,
                null,
                null,
                null,
                null));
        return this;
    }

    /** A day of 60 minutes of filler sets, so the week has a known size. */
    Weeks fullDay(int day) {
        for (int i = 0; i < 10; i++) {
            set(day, i % 2 == 0 ? TaskType.READ_ACADEMIC_PASSAGE : TaskType.BUILD_A_SENTENCE, 6);
        }
        return this;
    }

    Week build() {
        return new Week(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                status,
                DAY1,
                minutesPerDay,
                false,
                weak,
                null,
                NOW,
                NOW,
                null,
                null,
                null,
                null,
                0,
                List.of(),
                null,
                0,
                tasks);
    }

    private static WeekSection sectionOf(WeekTaskKind kind) {
        return switch (kind) {
            case GRAMMAR_UNIT -> WeekSection.GRAMMAR;
            case VOCABULARY_UNIT, VOCABULARY_REVIEW -> WeekSection.VOCABULARY;
            case EXTERNAL_RESOURCE -> WeekSection.RESOURCES;
            default -> WeekSection.READING;
        };
    }

    static LocalDate day(int dayNo) {
        return DAY1.plusDays(dayNo - 1L);
    }

    static Instant midnightUtc(int dayNo) {
        return day(dayNo).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    }

    /** Marks every task of the days {@code 1..lastDay} done. */
    static void doDays(Week week, int lastDay) {
        for (WeekTask task : week.tasks()) {
            if (task.dayNo() <= lastDay && task.kind() == WeekTaskKind.ITEM_SET) {
                week.complete(task.id(), UUID.randomUUID(), day(task.dayNo()), midnightUtc(task.dayNo()));
            }
        }
    }
}
