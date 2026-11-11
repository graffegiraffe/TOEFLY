package com.toefly.planning.adapter.out.persistence;

import com.toefly.planning.application.port.out.ConcurrencyConflictException;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.domain.week.TaskOrigin;
import com.toefly.planning.domain.week.TaskType;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekCloseReason;
import com.toefly.planning.domain.week.WeekSection;
import com.toefly.planning.domain.week.WeekStatus;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import com.toefly.planning.domain.week.WeekTaskSkipReason;
import com.toefly.planning.domain.week.WeekTaskStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** {@code week} with its {@code week_task} rows and the derived {@code day_plan} rows, versioned by the week. */
@Repository
public class JdbcWeekRepositoryAdapter implements WeekRepository {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcWeekRepositoryAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<Week> findById(UUID weekId) {
        return load("w.id = :id", Map.of("id", weekId)).stream().findFirst();
    }

    @Override
    public Optional<Week> findByTask(UUID weekTaskId) {
        return load("w.id = (SELECT week_id FROM week_task WHERE id = :task)", Map.of("task", weekTaskId)).stream()
                .findFirst();
    }

    @Override
    public List<Week> findByPlan(UUID planId) {
        return load("w.plan_id = :plan", Map.of("plan", planId));
    }

    @Override
    public List<UUID> plansWithOpenWeeks() {
        return jdbc.sql("SELECT DISTINCT plan_id FROM week WHERE status IN ('PUBLISHED', 'IN_PROGRESS')")
                .query(UUID.class)
                .list();
    }

    @Override
    public void insert(Week week) {
        jdbc.sql("""
                        INSERT INTO week (id, plan_id, user_id, week_no, status, day1, minutes_per_day, mock_exam,
                            weak_types, rationale, generated_at, published_at, completed_at, closed_on, closed_by,
                            rebalanced_at, rebalance_count, rebalance_skipped, last_rollover_date, progress_percent,
                            version)
                        VALUES (:id, :plan, :user, :no, :status, :day1, :minutes, :mock, CAST(:weak AS jsonb),
                            :rationale, :generated, :published, :completed, :closedOn, :closedBy, :rebalancedAt,
                            :rebalanceCount, CAST(:rebalanceSkipped AS jsonb), :rollover, :progress, :version)
                        """).params(weekParams(week)).update();
        writeTasks(week);
    }

    @Override
    public int update(Week week) {
        int changed = jdbc.sql("""
                        UPDATE week SET status = :status, day1 = :day1, published_at = :published,
                            completed_at = :completed, closed_on = :closedOn, closed_by = :closedBy,
                            rebalanced_at = :rebalancedAt, rebalance_count = :rebalanceCount,
                            rebalance_skipped = CAST(:rebalanceSkipped AS jsonb), last_rollover_date = :rollover,
                            progress_percent = :progress, version = version + 1
                        WHERE id = :id AND version = :version
                        """).params(weekParams(week)).update();
        if (changed != 1) {
            throw new ConcurrencyConflictException("week " + week.id());
        }
        writeTasks(week);
        week.saved(week.version() + 1);
        return week.version();
    }

    @Override
    public Set<String> assignedRefs(UUID userId) {
        return new HashSet<>(
                jdbc.sql("SELECT DISTINCT ref_id FROM week_task WHERE user_id = :user AND ref_id IS NOT NULL")
                        .param("user", userId)
                        .query(String.class)
                        .list());
    }

    @Override
    public Map<TaskType, List<UUID>> doneItemSets(UUID userId) {
        Map<TaskType, List<UUID>> done = new EnumMap<>(TaskType.class);
        jdbc.sql("""
                        SELECT task_type, ref_id FROM week_task
                        WHERE user_id = :user AND kind = 'ITEM_SET' AND status = 'DONE'
                        ORDER BY completed_at
                        """)
                .param("user", userId)
                .query((rs, row) -> {
                    done.computeIfAbsent(TaskType.valueOf(rs.getString("task_type")), type -> new ArrayList<>())
                            .add(UUID.fromString(rs.getString("ref_id")));
                    return null;
                })
                .list();
        return done;
    }

    @Override
    public Map<TaskType, Integer> typeCounts(UUID planId, int fromWeekNo) {
        Map<TaskType, Integer> counts = new EnumMap<>(TaskType.class);
        jdbc.sql("""
                        SELECT t.task_type, count(*) AS n FROM week_task t JOIN week w ON w.id = t.week_id
                        WHERE w.plan_id = :plan AND w.week_no >= :from AND t.task_type IS NOT NULL
                        GROUP BY t.task_type
                        """)
                .param("plan", planId)
                .param("from", fromWeekNo)
                .query((rs, row) -> counts.put(TaskType.valueOf(rs.getString("task_type")), rs.getInt("n")))
                .list();
        return counts;
    }

    @Override
    public void deleteByUser(UUID userId) {
        jdbc.sql("DELETE FROM week WHERE user_id = :user").param("user", userId).update();
    }

    // ---- reading ----

    private List<Week> load(String where, Map<String, Object> params) {
        List<WeekRow> rows = jdbc.sql("SELECT w.* FROM week w WHERE " + where + " ORDER BY w.week_no")
                .params(params)
                .query(this::weekRow)
                .list();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<WeekTask>> tasks = new HashMap<>();
        for (WeekRow row : rows) {
            tasks.put(row.id(), new ArrayList<>());
        }
        jdbc.sql("SELECT * FROM week_task WHERE week_id IN (:weeks) ORDER BY sort_order")
                .param("weeks", new ArrayList<>(tasks.keySet()))
                .query((rs, row) -> {
                    tasks.get(rs.getObject("week_id", UUID.class)).add(task(rs));
                    return null;
                })
                .list();
        return rows.stream().map(row -> row.toWeek(tasks.get(row.id()))).toList();
    }

    private record WeekRow(
            UUID id,
            UUID planId,
            UUID userId,
            int weekNo,
            WeekStatus status,
            LocalDate day1,
            int minutesPerDay,
            boolean mockExam,
            List<TaskType> weakTypes,
            @Nullable String rationale,
            Instant generatedAt,
            @Nullable Instant publishedAt,
            @Nullable Instant completedAt,
            @Nullable LocalDate closedOn,
            @Nullable WeekCloseReason closedBy,
            @Nullable Instant rebalancedAt,
            int rebalanceCount,
            List<UUID> rebalanceSkipped,
            @Nullable LocalDate lastRollover,
            int version) {

        Week toWeek(List<WeekTask> tasks) {
            return new Week(
                    id,
                    planId,
                    userId,
                    weekNo,
                    status,
                    day1,
                    minutesPerDay,
                    mockExam,
                    weakTypes,
                    rationale,
                    generatedAt,
                    publishedAt,
                    completedAt,
                    closedOn,
                    closedBy,
                    rebalancedAt,
                    rebalanceCount,
                    rebalanceSkipped,
                    lastRollover,
                    version,
                    tasks);
        }
    }

    private WeekRow weekRow(ResultSet rs, int row) throws SQLException {
        String closedBy = rs.getString("closed_by");
        return new WeekRow(
                rs.getObject("id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getInt("week_no"),
                WeekStatus.valueOf(rs.getString("status")),
                rs.getDate("day1").toLocalDate(),
                rs.getInt("minutes_per_day"),
                rs.getBoolean("mock_exam"),
                json.readValue(rs.getString("weak_types"), STRINGS).stream()
                        .map(TaskType::valueOf)
                        .toList(),
                rs.getString("rationale"),
                rs.getTimestamp("generated_at").toInstant(),
                instant(rs, "published_at"),
                instant(rs, "completed_at"),
                date(rs, "closed_on"),
                closedBy == null ? null : WeekCloseReason.valueOf(closedBy),
                instant(rs, "rebalanced_at"),
                rs.getInt("rebalance_count"),
                json.readValue(rs.getString("rebalance_skipped"), STRINGS).stream()
                        .map(UUID::fromString)
                        .toList(),
                date(rs, "last_rollover_date"),
                rs.getInt("version"));
    }

    private static WeekTask task(ResultSet rs) throws SQLException {
        String type = rs.getString("task_type");
        String reason = rs.getString("skipped_reason");
        return new WeekTask(
                rs.getObject("id", UUID.class),
                WeekTaskKind.valueOf(rs.getString("kind")),
                WeekSection.valueOf(rs.getString("week_section")),
                type == null ? null : TaskType.valueOf(type),
                rs.getString("ref_id"),
                rs.getString("title"),
                rs.getBigDecimal("estimated_minutes").doubleValue(),
                TaskOrigin.valueOf(rs.getString("origin")),
                rs.getString("rationale"),
                rs.getBoolean("is_repeat"),
                WeekTaskStatus.valueOf(rs.getString("status")),
                reason == null ? null : WeekTaskSkipReason.valueOf(reason),
                rs.getInt("day_no"),
                rs.getInt("sort_order"),
                rs.getObject("attempt_id", UUID.class),
                instant(rs, "started_at"),
                instant(rs, "completed_at"),
                date(rs, "completed_on"));
    }

    // ---- writing ----

    private Map<String, Object> weekParams(Week week) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", week.id());
        params.put("plan", week.planId());
        params.put("user", week.userId());
        params.put("no", week.weekNo());
        params.put("status", week.status().name());
        params.put("day1", Date.valueOf(week.day1()));
        params.put("minutes", week.minutesPerDay());
        params.put("mock", week.mockExam());
        params.put(
                "weak",
                json.writeValueAsString(
                        week.weakTypes().stream().map(Enum::name).toList()));
        params.put("rationale", week.rationale());
        params.put("generated", Timestamp.from(week.generatedAt()));
        params.put("published", timestamp(week.publishedAt()));
        params.put("completed", timestamp(week.completedAt()));
        params.put("closedOn", week.closedOn() == null ? null : Date.valueOf(week.closedOn()));
        params.put("closedBy", week.closedBy() == null ? null : week.closedBy().name());
        params.put("rebalancedAt", timestamp(week.rebalancedAt()));
        params.put("rebalanceCount", week.rebalanceCount());
        params.put(
                "rebalanceSkipped",
                json.writeValueAsString(
                        week.rebalanceSkipped().stream().map(UUID::toString).toList()));
        params.put("rollover", week.lastRolloverDate() == null ? null : Date.valueOf(week.lastRolloverDate()));
        params.put("progress", week.progressPercent());
        params.put("version", week.version());
        return params;
    }

    private void writeTasks(Week week) {
        for (WeekTask task : week.tasks()) {
            Map<String, Object> params = new HashMap<>();
            params.put("id", task.id());
            params.put("week", week.id());
            params.put("user", week.userId());
            params.put("day", task.dayNo());
            params.put("kind", task.kind().name());
            params.put("section", task.section().name());
            params.put("type", task.taskType() == null ? null : task.taskType().name());
            params.put("ref", task.refId());
            params.put("title", task.title());
            params.put("minutes", BigDecimal.valueOf(task.estimatedMinutes()).setScale(1, RoundingMode.HALF_UP));
            params.put("status", task.status().name());
            params.put("origin", task.origin().name());
            params.put("rationale", task.rationale());
            params.put("repeat", task.repeat());
            params.put(
                    "reason",
                    task.skippedReason() == null ? null : task.skippedReason().name());
            params.put("order", task.sortOrder());
            params.put("attempt", task.attemptId());
            params.put("started", timestamp(task.startedAt()));
            params.put("done", timestamp(task.completedAt()));
            params.put("doneOn", task.completedOn() == null ? null : Date.valueOf(task.completedOn()));
            jdbc.sql("""
                            INSERT INTO week_task (id, week_id, user_id, day_no, kind, week_section, task_type, ref_id,
                                title, estimated_minutes, status, origin, rationale, is_repeat, skipped_reason,
                                sort_order, attempt_id, started_at, completed_at, completed_on)
                            VALUES (:id, :week, :user, :day, :kind, :section, :type, :ref, :title, :minutes, :status,
                                :origin, :rationale, :repeat, :reason, :order, :attempt, :started, :done, :doneOn)
                            ON CONFLICT (id) DO UPDATE SET day_no = EXCLUDED.day_no, status = EXCLUDED.status,
                                skipped_reason = EXCLUDED.skipped_reason, sort_order = EXCLUDED.sort_order,
                                attempt_id = EXCLUDED.attempt_id, started_at = EXCLUDED.started_at,
                                completed_at = EXCLUDED.completed_at, completed_on = EXCLUDED.completed_on
                            """).params(params).update();
        }
        jdbc.sql("DELETE FROM day_plan WHERE week_id = :week")
                .param("week", week.id())
                .update();
        Map<Integer, Double> minutes = new TreeMap<>();
        for (int day = 1; day <= Week.DAYS; day++) {
            minutes.put(day, 0.0);
        }
        for (WeekTask task : week.tasks()) {
            minutes.merge(task.dayNo(), task.estimatedMinutes(), Double::sum);
        }
        minutes.forEach((day, total) -> jdbc.sql("""
                        INSERT INTO day_plan (week_id, day_no, local_date, planned_minutes)
                        VALUES (:week, :day, :date, :minutes)
                        """)
                .param("week", week.id())
                .param("day", day)
                .param("date", Date.valueOf(week.date(day)))
                .param("minutes", (int) Math.round(total))
                .update());
    }

    private static @Nullable Timestamp timestamp(@Nullable Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static @Nullable LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
