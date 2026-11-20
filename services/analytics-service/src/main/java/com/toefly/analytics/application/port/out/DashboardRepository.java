package com.toefly.analytics.application.port.out;

import com.toefly.analytics.domain.dashboard.BandPoint;
import com.toefly.analytics.domain.dashboard.StudentDashboard;
import com.toefly.analytics.domain.dashboard.WeekSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code student_dashboard_view} with the weeks and band points of the Progress charts. */
public interface DashboardRepository {

    Optional<StudentDashboard> find(UUID userId);

    void save(StudentDashboard dashboard);

    Optional<WeekSummary> findWeek(UUID weekId);

    void saveWeek(WeekSummary week);

    List<WeekSummary> weeks(UUID userId);

    void addBandPoint(UUID userId, BandPoint point);

    List<BandPoint> bandPoints(UUID userId);

    void reset();

    void deleteUser(UUID userId);
}
