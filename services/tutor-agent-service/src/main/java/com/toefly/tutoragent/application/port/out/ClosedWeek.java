package com.toefly.tutoragent.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A closed week as planning-service reported it (WeekCompletedV1). */
public record ClosedWeek(
        UUID userId,
        UUID weekId,
        int weekNo,
        LocalDate startDate,
        LocalDate endDate,
        Instant closedAt,
        int tasksTotal,
        int tasksDone,
        double progressPercent,
        List<Section> sections,
        @Nullable UUID correlationId,
        @Nullable UUID causationId) {

    public ClosedWeek {
        sections = List.copyOf(sections);
    }

    public record Section(String section, int tasksTotal, int tasksDone, int minutesPlanned, int minutesDone) {}
}
