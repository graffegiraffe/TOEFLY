package com.toefly.practice.adapter.out.persistence;

import com.toefly.practice.application.port.in.TimeBucket;
import com.toefly.practice.application.port.in.TimeGrouping;
import com.toefly.practice.application.port.out.ConcurrencyConflictException;
import com.toefly.practice.application.port.out.TimeEntryPort;
import com.toefly.practice.domain.attempt.TaskType;
import com.toefly.practice.domain.time.ContextType;
import com.toefly.practice.domain.time.TimeEntry;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** time_entry with optimistic updates by version. */
@Repository
public class JdbcTimeEntryAdapter implements TimeEntryPort {

    private final JdbcClient jdbc;

    public JdbcTimeEntryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TimeEntry> find(ContextType contextType, UUID contextId, UUID userId) {
        return jdbc.sql("""
                        SELECT * FROM time_entry
                        WHERE context_type = :type AND context_id = :context AND user_id = :user
                        """)
                .param("type", contextType.name())
                .param("context", contextId)
                .param("user", userId)
                .query(JdbcTimeEntryAdapter::map)
                .optional();
    }

    @Override
    public void save(TimeEntry entry) {
        int changed;
        if (entry.version() == 0) {
            changed = jdbc.sql("""
                            INSERT INTO time_entry (id, user_id, context_type, context_id, task_type, week_section,
                                local_date, started_at, estimated_seconds, active_seconds, last_sequence,
                                last_heartbeat_at, paused, audio_replays, suspicious, closed_at, version)
                            VALUES (:id, :user, :type, :context, :taskType, :weekSection, :day, :startedAt,
                                :estimated, :active, :sequence, :heartbeat, :paused, :replays, :suspicious, :closed, 1)
                            ON CONFLICT (context_type, context_id, user_id) DO NOTHING
                            """)
                    .param("id", entry.id())
                    .param("user", entry.userId())
                    .param("type", entry.contextType().name())
                    .param("context", entry.contextId())
                    .param(
                            "taskType",
                            entry.taskType() == null ? null : entry.taskType().name())
                    .param("weekSection", entry.weekSection())
                    .param("day", Date.valueOf(entry.localDate()))
                    .param("startedAt", Timestamp.from(entry.startedAt()))
                    .param("estimated", entry.estimatedSeconds())
                    .param("active", entry.activeSeconds())
                    .param("sequence", entry.lastSequence())
                    .param("heartbeat", timestamp(entry.lastHeartbeatAt()))
                    .param("paused", entry.paused())
                    .param("replays", entry.audioReplays())
                    .param("suspicious", entry.suspicious())
                    .param("closed", timestamp(entry.closedAt()))
                    .update();
        } else {
            changed = jdbc.sql("""
                            UPDATE time_entry SET active_seconds = :active, last_sequence = :sequence,
                                last_heartbeat_at = :heartbeat, paused = :paused, audio_replays = :replays,
                                suspicious = :suspicious, closed_at = :closed, version = version + 1
                            WHERE id = :id AND version = :version
                            """)
                    .param("id", entry.id())
                    .param("active", entry.activeSeconds())
                    .param("sequence", entry.lastSequence())
                    .param("heartbeat", timestamp(entry.lastHeartbeatAt()))
                    .param("paused", entry.paused())
                    .param("replays", entry.audioReplays())
                    .param("suspicious", entry.suspicious())
                    .param("closed", timestamp(entry.closedAt()))
                    .param("version", entry.version())
                    .update();
        }
        if (changed != 1) {
            throw new ConcurrencyConflictException(entry.contextId(), entry.version());
        }
    }

    @Override
    public List<TimeBucket> summary(UUID userId, LocalDate from, LocalDate to, TimeGrouping grouping) {
        String key = switch (grouping) {
            case DAY -> "to_char(local_date, 'YYYY-MM-DD')";
            case WEEK -> "to_char(local_date, 'IYYY-\"W\"IW')";
            case SECTION -> "coalesce(week_section, 'OTHER')";
            case TASK_TYPE -> "coalesce(task_type, context_type)";
        };
        return jdbc.sql("""
                        SELECT %s AS bucket, sum(active_seconds)::int AS active,
                            sum(CASE WHEN closed_at IS NOT NULL THEN estimated_seconds ELSE 0 END)::int AS estimated,
                            count(*) FILTER (WHERE context_type = 'ATTEMPT')::int AS attempts
                        FROM time_entry
                        WHERE user_id = :user AND local_date >= :from AND local_date < :to
                        GROUP BY bucket ORDER BY bucket
                        """.formatted(key))
                .param("user", userId)
                .param("from", Date.valueOf(from))
                .param("to", Date.valueOf(to))
                .query((rs, row) -> new TimeBucket(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)))
                .list();
    }

    @Override
    public List<TimeEntry> findAll(UUID userId) {
        return jdbc.sql("SELECT * FROM time_entry WHERE user_id = :user ORDER BY local_date")
                .param("user", userId)
                .query(JdbcTimeEntryAdapter::map)
                .list();
    }

    @Override
    public int eraseUser(UUID userId) {
        return jdbc.sql("DELETE FROM time_entry WHERE user_id = :user")
                .param("user", userId)
                .update();
    }

    private static TimeEntry map(ResultSet rs, int row) throws SQLException {
        String taskType = rs.getString("task_type");
        return new TimeEntry(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                ContextType.valueOf(rs.getString("context_type")),
                rs.getObject("context_id", UUID.class),
                taskType == null ? null : TaskType.valueOf(taskType),
                rs.getString("week_section"),
                rs.getDate("local_date").toLocalDate(),
                rs.getTimestamp("started_at").toInstant(),
                rs.getInt("estimated_seconds"),
                rs.getInt("active_seconds"),
                rs.getLong("last_sequence"),
                instant(rs.getTimestamp("last_heartbeat_at")),
                rs.getBoolean("paused"),
                rs.getInt("audio_replays"),
                rs.getBoolean("suspicious"),
                instant(rs.getTimestamp("closed_at")),
                rs.getInt("version"));
    }

    private static @Nullable Timestamp timestamp(@Nullable Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static @Nullable Instant instant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
