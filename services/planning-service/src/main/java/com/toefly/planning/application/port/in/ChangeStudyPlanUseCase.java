package com.toefly.planning.application.port.in;

import com.toefly.planning.domain.plan.StudyPlan;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Changes of the time zone, the daily minutes or the exam date; the current week keeps its minutes. */
public interface ChangeStudyPlanUseCase {

    void preferencesUpdated(UUID userId, ZoneId timezone, int minutesPerDay);

    StudyPlan patch(UUID userId, int ifMatch, @Nullable LocalDate examDate, @Nullable Integer minutesPerDay);
}
