package com.toefly.analytics.adapter.out.persistence;

import com.toefly.analytics.application.port.out.ActivityRepository;
import com.toefly.analytics.domain.activity.ActivityDay;
import com.toefly.analytics.domain.activity.PaceTally;
import com.toefly.analytics.domain.activity.PlannedTask;
import com.toefly.analytics.domain.common.TaskType;
import com.toefly.analytics.domain.common.WeekSection;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcActivityRepositoryAdapter implements ActivityRepository {

    private static final TypeReference<Map<WeekSection, Integer>> BY_SECTION = new TypeReference<>() {};
    private static final TypeReference<Map<TaskType, Integer>> BY_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<TaskType, PaceTally>> PACE = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcActivityRepositoryAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<ActivityDay> findDay(UUID userId, LocalDate date) {
        return jdbc.sql(SELECT_DAY + " WHERE user_id = :user AND local_date = :day")
                .param("user", userId)
                .param("day", Date.valueOf(date))
                .query(this::day)
                .optional();
    }

    @Override
    public void saveDay(ActivityDay d) {
        jdbc.sql("""
                        INSERT INTO activity_day (user_id, local_date, seconds_by_section, seconds_by_task_type,
                                                  pace_by_task_type, tasks_done, goal_minutes, day_plan_done, goal_met,
                                                  active_seconds, last_active_at)
                        VALUES (:user, :day, CAST(:bySection AS jsonb), CAST(:byType AS jsonb), CAST(:pace AS jsonb),
                                :tasks, :goal, :planDone, :goalMet, :active, :lastActive)
                        ON CONFLICT (user_id, local_date) DO UPDATE SET
                            seconds_by_section = EXCLUDED.seconds_by_section,
                            seconds_by_task_type = EXCLUDED.seconds_by_task_type,
                            pace_by_task_type = EXCLUDED.pace_by_task_type, tasks_done = EXCLUDED.tasks_done,
                            goal_minutes = EXCLUDED.goal_minutes, day_plan_done = EXCLUDED.day_plan_done,
                            goal_met = EXCLUDED.goal_met, active_seconds = EXCLUDED.active_seconds,
                            last_active_at = EXCLUDED.last_active_at
                        """)
                .param("user", d.userId())
                .param("day", Date.valueOf(d.date()))
                .param("bySection", json.writeValueAsString(d.secondsBySection()))
                .param("byType", json.writeValueAsString(d.secondsByTaskType()))
                .param("pace", json.writeValueAsString(d.pace()))
                .param("tasks", d.tasksDone())
                .param("goal", d.goalMinutes())
                .param("planDone", d.dayPlanDone())
                .param("goalMet", d.goalMet())
                .param("active", d.activeSeconds())
                .param("lastActive", d.lastActiveAt() == null ? null : Timestamp.from(d.lastActiveAt()))
                .update();
    }

    @Override
    public List<ActivityDay> days(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.sql(SELECT_DAY + " WHERE user_id = :user AND local_date >= :from AND local_date < :to"
                        + " ORDER BY local_date")
                .param("user", userId)
                .param("from", Date.valueOf(from))
                .param("to", Date.valueOf(to))
                .query(this::day)
                .list();
    }

    @Override
    public void saveAttemptNorm(UUID attemptId, UUID userId, int normSeconds) {
        jdbc.sql("""
                        INSERT INTO attempt_norm (attempt_id, user_id, norm_seconds) VALUES (:attempt, :user, :norm)
                        ON CONFLICT (attempt_id) DO UPDATE SET norm_seconds = EXCLUDED.norm_seconds
                        """)
                .param("attempt", attemptId)
                .param("user", userId)
                .param("norm", normSeconds)
                .update();
    }

    @Override
    public Optional<Integer> attemptNorm(UUID attemptId) {
        return jdbc.sql("SELECT norm_seconds FROM attempt_norm WHERE attempt_id = :attempt")
                .param("attempt", attemptId)
                .query(Integer.class)
                .optional();
    }

    @Override
    public Optional<PlannedTask> plannedTask(UUID weekTaskId) {
        return jdbc.sql("SELECT * FROM planned_task WHERE week_task_id = :task")
                .param("task", weekTaskId)
                .query(JdbcActivityRepositoryAdapter::task)
                .optional();
    }

    @Override
    public List<PlannedTask> plannedTasks(UUID userId, LocalDate plannedDate) {
        return jdbc.sql("SELECT * FROM planned_task WHERE user_id = :user AND planned_date = :day")
                .param("user", userId)
                .param("day", Date.valueOf(plannedDate))
                .query(JdbcActivityRepositoryAdapter::task)
                .list();
    }

    @Override
    public void savePlannedTask(PlannedTask t) {
        jdbc.sql("""
                        INSERT INTO planned_task (week_task_id, user_id, planned_date, done_date)
                        VALUES (:task, :user, :planned, :done)
                        ON CONFLICT (week_task_id) DO UPDATE SET
                            planned_date = EXCLUDED.planned_date, done_date = EXCLUDED.done_date
                        """)
                .param("task", t.weekTaskId())
                .param("user", t.userId())
                .param("planned", Date.valueOf(t.plannedDate()))
                .param("done", t.doneDate() == null ? null : Date.valueOf(t.doneDate()))
                .update();
    }

    @Override
    public void addStudyDay(UUID userId, LocalDate day, Instant at) {
        jdbc.sql("""
                        INSERT INTO streak_view (user_id, done_days, as_of) VALUES (:user, ARRAY[CAST(:day AS date)], :at)
                        ON CONFLICT (user_id) DO UPDATE SET
                            done_days = CASE WHEN CAST(:day AS date) = ANY (streak_view.done_days)
                                             THEN streak_view.done_days
                                             ELSE array_append(streak_view.done_days, CAST(:day AS date)) END,
                            as_of = GREATEST(streak_view.as_of, EXCLUDED.as_of)
                        """)
                .param("user", userId)
                .param("day", Date.valueOf(day))
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public Set<LocalDate> studyDays(UUID userId) {
        return jdbc.sql("SELECT done_days FROM streak_view WHERE user_id = :user")
                .param("user", userId)
                .query((rs, n) -> dates(rs.getArray("done_days")))
                .optional()
                .orElseGet(Set::of);
    }

    @Override
    public Map<UUID, Set<LocalDate>> studyDaysSince(LocalDate since) {
        Map<UUID, Set<LocalDate>> days = new HashMap<>();
        jdbc.sql("SELECT user_id, local_date FROM activity_day WHERE goal_met AND local_date >= :since")
                .param("since", Date.valueOf(since))
                .query((rs, n) -> {
                    days.computeIfAbsent(rs.getObject("user_id", UUID.class), id -> new HashSet<>())
                            .add(rs.getDate("local_date").toLocalDate());
                    return 1;
                })
                .list();
        return days;
    }

    @Override
    public Optional<Instant> streakAsOf(UUID userId) {
        return jdbc.sql("SELECT as_of FROM streak_view WHERE user_id = :user")
                .param("user", userId)
                .query((rs, n) -> rs.getTimestamp("as_of").toInstant())
                .optional();
    }

    @Override
    public int activeLearners(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT count(DISTINCT user_id) FROM activity_day
                        WHERE active_seconds > 0 AND local_date >= :from AND local_date < :to
                        """)
                .param("from", Date.valueOf(from))
                .param("to", Date.valueOf(to))
                .query(Integer.class)
                .single();
    }

    @Override
    public void reset() {
        for (String table : TABLES) {
            jdbc.sql("DELETE FROM " + table).update();
        }
    }

    @Override
    public void deleteUser(UUID userId) {
        for (String table : TABLES) {
            jdbc.sql("DELETE FROM " + table + " WHERE user_id = :user")
                    .param("user", userId)
                    .update();
        }
    }

    private static final List<String> TABLES = List.of("activity_day", "streak_view", "attempt_norm", "planned_task");

    private static final String SELECT_DAY = """
            SELECT user_id, local_date, seconds_by_section::text AS by_section, seconds_by_task_type::text AS by_type,
                   pace_by_task_type::text AS pace, tasks_done, goal_minutes, day_plan_done, goal_met, last_active_at
            FROM activity_day""";

    private ActivityDay day(ResultSet rs, int n) throws SQLException {
        Timestamp lastActive = rs.getTimestamp("last_active_at");
        return new ActivityDay(
                rs.getObject("user_id", UUID.class),
                rs.getDate("local_date").toLocalDate(),
                json.readValue(rs.getString("by_section"), BY_SECTION),
                json.readValue(rs.getString("by_type"), BY_TYPE),
                json.readValue(rs.getString("pace"), PACE),
                rs.getInt("tasks_done"),
                rs.getInt("goal_minutes"),
                rs.getBoolean("day_plan_done"),
                rs.getBoolean("goal_met"),
                lastActive == null ? null : lastActive.toInstant());
    }

    private static PlannedTask task(ResultSet rs, int n) throws SQLException {
        Date done = rs.getDate("done_date");
        return new PlannedTask(
                rs.getObject("week_task_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getDate("planned_date").toLocalDate(),
                done == null ? null : done.toLocalDate());
    }

    private static Set<LocalDate> dates(Array array) throws SQLException {
        Set<LocalDate> days = new TreeSet<>();
        for (Object value : (Object[]) array.getArray()) {
            days.add(((Date) value).toLocalDate());
        }
        return days;
    }
}
