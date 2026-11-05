package com.toefly.profile.adapter.out.persistence;

import com.toefly.profile.application.port.out.ProfileStorePort;
import com.toefly.profile.domain.profile.Band;
import com.toefly.profile.domain.profile.ExamPlan;
import com.toefly.profile.domain.profile.ExplanationLanguage;
import com.toefly.profile.domain.profile.OnboardingStatus;
import com.toefly.profile.domain.profile.OnboardingStep;
import com.toefly.profile.domain.profile.Preferences;
import com.toefly.profile.domain.profile.Role;
import com.toefly.profile.domain.profile.StudyGoal;
import com.toefly.profile.domain.profile.UserProfile;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProfileStoreAdapter implements ProfileStorePort {

    private static final String COLUMNS = """
            user_id, email, display_name, roles, timezone, explanation_language, minutes_per_day,
            preferred_study_time, overall_target, reading_target, listening_target, writing_target, speaking_target,
            exam_booked, exam_date, onboarding_status, onboarding_steps, goal_revision, version, created_at""";

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcProfileStoreAdapter(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<UserProfile> findById(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM user_profile WHERE user_id = :userId")
                .param("userId", userId)
                .query((rs, row) -> map(rs))
                .optional();
    }

    @Override
    public boolean insertIfAbsent(UserProfile profile) {
        return bind(jdbc.sql("""
                        INSERT INTO user_profile (%s, updated_at)
                        VALUES (:userId, :email, :displayName, :roles, :timezone, :language, :minutes, :studyTime,
                                :overall, :reading, :listening, :writing, :speaking, :examBooked, :examDate,
                                :onboarding, :steps, :revision, :version, :createdAt, :now)
                        ON CONFLICT (user_id) DO NOTHING
                        """.formatted(COLUMNS)), profile).update() == 1;
    }

    @Override
    public void update(UserProfile profile, long previousVersion) {
        int updated = bind(jdbc.sql("""
                        UPDATE user_profile SET email = :email, display_name = :displayName, roles = :roles,
                            timezone = :timezone, explanation_language = :language, minutes_per_day = :minutes,
                            preferred_study_time = :studyTime, overall_target = :overall, reading_target = :reading,
                            listening_target = :listening, writing_target = :writing, speaking_target = :speaking,
                            exam_booked = :examBooked, exam_date = :examDate, onboarding_status = :onboarding,
                            onboarding_steps = :steps, goal_revision = :revision, version = :version,
                            updated_at = :now
                        WHERE user_id = :userId AND version = :previousVersion
                        """), profile)
                .param("previousVersion", previousVersion)
                .update();
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Profile " + profile.userId() + " changed concurrently");
        }
    }

    @Override
    public void appendGoalRevision(UserProfile profile) {
        StudyGoal goal = profile.goal();
        ExamPlan exam = profile.exam() == null ? ExamPlan.notBooked() : profile.exam();
        if (goal == null) {
            throw new IllegalStateException("No goal to record");
        }
        jdbc.sql("""
                        INSERT INTO study_goal (user_id, revision, overall_target, reading_target, listening_target,
                            writing_target, speaking_target, exam_booked, exam_date, minutes_per_day, effective_from)
                        VALUES (:userId, :revision, :overall, :reading, :listening, :writing, :speaking, :examBooked,
                            :examDate, :minutes, :now)
                        """)
                .param("userId", profile.userId())
                .param("revision", profile.goalRevision())
                .param("overall", decimal(goal.overallTarget()))
                .param("reading", decimal(goal.readingTarget()))
                .param("listening", decimal(goal.listeningTarget()))
                .param("writing", decimal(goal.writingTarget()))
                .param("speaking", decimal(goal.speakingTarget()))
                .param("examBooked", exam.booked())
                .param("examDate", exam.examDate() == null ? null : Date.valueOf(exam.examDate()))
                .param("minutes", profile.preferences().minutesPerDay())
                .param("now", Timestamp.from(clock.instant()))
                .update();
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, UserProfile profile) {
        StudyGoal goal = profile.goal();
        ExamPlan exam = profile.exam();
        Preferences preferences = profile.preferences();
        return statement
                .param("userId", profile.userId())
                .param("email", profile.email())
                .param("displayName", profile.displayName())
                .param("roles", join(profile.roles()))
                .param("timezone", preferences.timezone().getId())
                .param("language", preferences.explanationLanguage().code())
                .param("minutes", preferences.minutesPerDay())
                .param("studyTime", Time.valueOf(preferences.preferredStudyTime()))
                .param("overall", goal == null ? null : decimal(goal.overallTarget()))
                .param("reading", goal == null ? null : decimal(goal.readingTarget()))
                .param("listening", goal == null ? null : decimal(goal.listeningTarget()))
                .param("writing", goal == null ? null : decimal(goal.writingTarget()))
                .param("speaking", goal == null ? null : decimal(goal.speakingTarget()))
                .param("examBooked", exam == null ? null : exam.booked())
                .param("examDate", exam == null || exam.examDate() == null ? null : Date.valueOf(exam.examDate()))
                .param("onboarding", profile.onboardingStatus().name())
                .param("steps", join(profile.filledSteps()))
                .param("revision", profile.goalRevision())
                .param("version", profile.version())
                .param("createdAt", Timestamp.from(profile.createdAt()))
                .param("now", Timestamp.from(clock.instant()));
    }

    private static UserProfile map(ResultSet rs) throws SQLException {
        BigDecimal overall = rs.getBigDecimal("overall_target");
        StudyGoal goal = overall == null
                ? null
                : new StudyGoal(
                        Band.of(overall.doubleValue()),
                        band(rs, "reading_target"),
                        band(rs, "listening_target"),
                        band(rs, "writing_target"),
                        band(rs, "speaking_target"));
        Object booked = rs.getObject("exam_booked");
        Date examDate = rs.getDate("exam_date");
        ExamPlan exam = booked == null
                ? null
                : new ExamPlan((Boolean) booked, examDate == null ? null : examDate.toLocalDate());
        return new UserProfile(
                rs.getObject("user_id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                split(rs.getString("roles"), Role.class),
                new Preferences(
                        ZoneId.of(rs.getString("timezone")),
                        ExplanationLanguage.ofCode(rs.getString("explanation_language")),
                        rs.getInt("minutes_per_day"),
                        rs.getTime("preferred_study_time").toLocalTime()),
                goal,
                exam,
                OnboardingStatus.valueOf(rs.getString("onboarding_status")),
                split(rs.getString("onboarding_steps"), OnboardingStep.class),
                rs.getInt("goal_revision"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static @Nullable Band band(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : Band.of(value.doubleValue());
    }

    private static @Nullable BigDecimal decimal(@Nullable Band band) {
        return band == null ? null : BigDecimal.valueOf(band.value());
    }

    private static String join(Collection<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private static <E extends Enum<E>> Set<E> split(String value, Class<E> type) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(name -> Enum.valueOf(type, name))
                .collect(Collectors.toUnmodifiableSet());
    }
}
