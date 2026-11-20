package com.toefly.analytics.adapter.out.persistence;

import com.toefly.analytics.application.port.out.DashboardRepository;
import com.toefly.analytics.domain.common.Section;
import com.toefly.analytics.domain.dashboard.BandPoint;
import com.toefly.analytics.domain.dashboard.BandSnapshot;
import com.toefly.analytics.domain.dashboard.CurrentWeek;
import com.toefly.analytics.domain.dashboard.RecentResult;
import com.toefly.analytics.domain.dashboard.StudentDashboard;
import com.toefly.analytics.domain.dashboard.Theta;
import com.toefly.analytics.domain.dashboard.WeekSummary;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcDashboardRepositoryAdapter implements DashboardRepository {

    private static final TypeReference<Map<Section, Theta>> THETA = new TypeReference<>() {};
    private static final TypeReference<List<RecentResult>> RESULTS = new TypeReference<>() {};
    private static final TypeReference<Map<Section, Double>> SECTIONS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcDashboardRepositoryAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<StudentDashboard> find(UUID userId) {
        return jdbc.sql("""
                        SELECT current_week::text AS current_week, band_estimate::text AS band_estimate,
                               theta::text AS theta, last_results::text AS last_results, as_of
                        FROM student_dashboard_view WHERE user_id = :user
                        """)
                .param("user", userId)
                .query((rs, n) -> {
                    String week = rs.getString("current_week");
                    String band = rs.getString("band_estimate");
                    Timestamp asOf = rs.getTimestamp("as_of");
                    return new StudentDashboard(
                            userId,
                            week == null ? null : json.readValue(week, CurrentWeek.class),
                            band == null ? null : json.readValue(band, BandSnapshot.class),
                            json.readValue(rs.getString("theta"), THETA),
                            json.readValue(rs.getString("last_results"), RESULTS),
                            asOf == null ? null : asOf.toInstant());
                })
                .optional();
    }

    @Override
    public void save(StudentDashboard d) {
        jdbc.sql("""
                        INSERT INTO student_dashboard_view (user_id, current_week, band_estimate, theta, last_results, as_of)
                        VALUES (:user, CAST(:week AS jsonb), CAST(:band AS jsonb), CAST(:theta AS jsonb),
                                CAST(:results AS jsonb), :asOf)
                        ON CONFLICT (user_id) DO UPDATE SET
                            current_week = EXCLUDED.current_week, band_estimate = EXCLUDED.band_estimate,
                            theta = EXCLUDED.theta, last_results = EXCLUDED.last_results, as_of = EXCLUDED.as_of
                        """)
                .param("user", d.userId())
                .param("week", d.currentWeek() == null ? null : json.writeValueAsString(d.currentWeek()))
                .param("band", d.band() == null ? null : json.writeValueAsString(d.band()))
                .param("theta", json.writeValueAsString(d.theta()))
                .param("results", json.writeValueAsString(d.lastResults()))
                .param("asOf", d.asOf() == null ? null : Timestamp.from(d.asOf()))
                .update();
    }

    @Override
    public Optional<WeekSummary> findWeek(UUID weekId) {
        return jdbc.sql("SELECT * FROM week_summary WHERE week_id = :week")
                .param("week", weekId)
                .query(JdbcDashboardRepositoryAdapter::week)
                .optional();
    }

    @Override
    public void saveWeek(WeekSummary w) {
        jdbc.sql("""
                        INSERT INTO week_summary (week_id, user_id, week_no, day1, end_date, progress_percent,
                                                  tasks_total, tasks_done, completed, closed_by, as_of)
                        VALUES (:week, :user, :no, :day1, :end, :progress, :total, :done, :completed, :closedBy, :asOf)
                        ON CONFLICT (week_id) DO UPDATE SET
                            day1 = EXCLUDED.day1, end_date = EXCLUDED.end_date,
                            progress_percent = EXCLUDED.progress_percent, tasks_total = EXCLUDED.tasks_total,
                            tasks_done = EXCLUDED.tasks_done, completed = EXCLUDED.completed,
                            closed_by = EXCLUDED.closed_by, as_of = EXCLUDED.as_of
                        """)
                .param("week", w.weekId())
                .param("user", w.userId())
                .param("no", w.weekNo())
                .param("day1", date(w.day1()))
                .param("end", date(w.endDate()))
                .param("progress", w.progressPercent())
                .param("total", w.tasksTotal())
                .param("done", w.tasksDone())
                .param("completed", w.completed())
                .param("closedBy", w.closedBy())
                .param("asOf", Timestamp.from(w.asOf()))
                .update();
    }

    @Override
    public List<WeekSummary> weeks(UUID userId) {
        return jdbc.sql("SELECT * FROM week_summary WHERE user_id = :user ORDER BY week_no")
                .param("user", userId)
                .query(JdbcDashboardRepositoryAdapter::week)
                .list();
    }

    @Override
    public void addBandPoint(UUID userId, BandPoint p) {
        jdbc.sql("""
                        INSERT INTO band_point (point_id, user_id, occurred_at, overall_point, overall_low, overall_high,
                                                sections)
                        VALUES (:id, :user, :at, :point, :low, :high, CAST(:sections AS jsonb))
                        ON CONFLICT (point_id) DO NOTHING
                        """)
                .param("id", p.eventId())
                .param("user", userId)
                .param("at", Timestamp.from(p.at()))
                .param("point", p.overallPoint())
                .param("low", p.overallLow())
                .param("high", p.overallHigh())
                .param("sections", json.writeValueAsString(p.sections()))
                .update();
    }

    @Override
    public List<BandPoint> bandPoints(UUID userId) {
        return jdbc.sql("""
                        SELECT point_id, occurred_at, overall_point, overall_low, overall_high, sections::text AS sections
                        FROM band_point WHERE user_id = :user ORDER BY occurred_at
                        """)
                .param("user", userId)
                .query((rs, n) -> new BandPoint(
                        rs.getObject("point_id", UUID.class),
                        rs.getTimestamp("occurred_at").toInstant(),
                        number(rs, "overall_point"),
                        number(rs, "overall_low"),
                        number(rs, "overall_high"),
                        json.readValue(rs.getString("sections"), SECTIONS)))
                .list();
    }

    @Override
    public void reset() {
        jdbc.sql("DELETE FROM student_dashboard_view").update();
        jdbc.sql("DELETE FROM week_summary").update();
        jdbc.sql("DELETE FROM band_point").update();
    }

    @Override
    public void deleteUser(UUID userId) {
        for (String table : List.of("student_dashboard_view", "week_summary", "band_point")) {
            jdbc.sql("DELETE FROM " + table + " WHERE user_id = :user")
                    .param("user", userId)
                    .update();
        }
    }

    private static WeekSummary week(ResultSet rs, int n) throws SQLException {
        Date day1 = rs.getDate("day1");
        Date end = rs.getDate("end_date");
        return new WeekSummary(
                rs.getObject("week_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getInt("week_no"),
                day1 == null ? null : day1.toLocalDate(),
                end == null ? null : end.toLocalDate(),
                rs.getBigDecimal("progress_percent").doubleValue(),
                rs.getInt("tasks_total"),
                rs.getInt("tasks_done"),
                rs.getBoolean("completed"),
                rs.getString("closed_by"),
                rs.getTimestamp("as_of").toInstant());
    }

    private static @Nullable Double number(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    private static @Nullable Date date(@Nullable LocalDate day) {
        return day == null ? null : Date.valueOf(day);
    }
}
