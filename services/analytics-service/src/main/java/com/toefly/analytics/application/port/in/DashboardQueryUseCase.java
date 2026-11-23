package com.toefly.analytics.application.port.in;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The student dashboard and the streak of Home and Progress. */
public interface DashboardQueryUseCase {

    StudentDashboardResult student(Caller caller, @Nullable UUID studentId);

    StreakResult streak(Caller caller);

    /** The dashboard of a user for an internal call; who may ask is checked by the security chain. */
    StudentDashboardResult studentOf(UUID userId);
}
