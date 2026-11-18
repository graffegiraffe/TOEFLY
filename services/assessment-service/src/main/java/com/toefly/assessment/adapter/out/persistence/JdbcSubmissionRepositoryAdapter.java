package com.toefly.assessment.adapter.out.persistence;

import com.toefly.assessment.application.port.in.SubmissionFilter;
import com.toefly.assessment.application.port.out.ConcurrencyConflictException;
import com.toefly.assessment.application.port.out.SubmissionRepository;
import com.toefly.assessment.domain.evaluation.AnnotationStatus;
import com.toefly.assessment.domain.evaluation.CriterionScore;
import com.toefly.assessment.domain.evaluation.Evaluation;
import com.toefly.assessment.domain.evaluation.EvaluationAgreement;
import com.toefly.assessment.domain.evaluation.EvaluatorType;
import com.toefly.assessment.domain.evaluation.InlineAnnotation;
import com.toefly.assessment.domain.evaluation.MistakeCategory;
import com.toefly.assessment.domain.evaluation.PassRecord;
import com.toefly.assessment.domain.evaluation.Severity;
import com.toefly.assessment.domain.submission.AttemptMode;
import com.toefly.assessment.domain.submission.Submission;
import com.toefly.assessment.domain.submission.SubmissionStatus;
import com.toefly.assessment.domain.submission.SubmissionVersion;
import com.toefly.assessment.domain.submission.TaskType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** {@code submission}, {@code submission_version}, {@code evaluation} and {@code inline_annotation}. */
@Repository
public class JdbcSubmissionRepositoryAdapter implements SubmissionRepository {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<List<CriterionScore>> CRITERIA = new TypeReference<>() {};
    private static final TypeReference<List<PassRecord>> PASSES = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcSubmissionRepositoryAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(Submission submission) {
        jdbc.sql("""
                        INSERT INTO submission (id, user_id, item_set_id, item_set_version, task_type, mode,
                            week_task_id, exam_session_id, status, current_version_no, version, created_at)
                        VALUES (:id, :user, :set, :setVersion, :type, :mode, :weekTask, :exam, :status, :current,
                            :version, :created)
                        """)
                .param("id", submission.id())
                .param("user", submission.userId())
                .param("set", submission.itemSetId())
                .param("setVersion", submission.itemSetVersion())
                .param("type", submission.taskType().name())
                .param("mode", submission.mode().name())
                .param("weekTask", submission.weekTaskId())
                .param("exam", submission.examSessionId())
                .param("status", submission.status().name())
                .param("current", submission.current().versionNo())
                .param("version", submission.version())
                .param("created", Timestamp.from(submission.createdAt()))
                .update();
        submission.versions().forEach(v -> upsertVersion(submission.id(), v));
    }

    @Override
    public void update(Submission submission, int expectedVersion) {
        int updated = jdbc.sql("""
                        UPDATE submission SET status = :status, current_version_no = :current, version = :version
                        WHERE id = :id AND version = :expected
                        """)
                .param("status", submission.status().name())
                .param("current", submission.current().versionNo())
                .param("version", submission.version())
                .param("id", submission.id())
                .param("expected", expectedVersion)
                .update();
        if (updated != 1) {
            throw new ConcurrencyConflictException("submission " + submission.id() + " changed");
        }
        submission.versions().forEach(v -> upsertVersion(submission.id(), v));
    }

    private void upsertVersion(UUID submissionId, SubmissionVersion v) {
        jdbc.sql("""
                        INSERT INTO submission_version (id, submission_id, version_no, status, body, word_count,
                            active_seconds, overtime_seconds, counts_for_estimate, submitted_at)
                        VALUES (:id, :submission, :no, :status, :body, :words, :active, :overtime, :counts, :submitted)
                        ON CONFLICT (submission_id, version_no) DO UPDATE SET status = EXCLUDED.status,
                            body = EXCLUDED.body, word_count = EXCLUDED.word_count,
                            active_seconds = EXCLUDED.active_seconds, overtime_seconds = EXCLUDED.overtime_seconds,
                            submitted_at = EXCLUDED.submitted_at
                        """)
                .param("id", v.id())
                .param("submission", submissionId)
                .param("no", v.versionNo())
                .param("status", v.status().name())
                .param("body", v.text())
                .param("words", v.wordCount())
                .param("active", v.activeSeconds())
                .param("overtime", v.overtimeSeconds())
                .param("counts", v.countsForEstimate())
                .param("submitted", v.submittedAt() == null ? null : Timestamp.from(v.submittedAt()))
                .update();
    }

    @Override
    public Optional<Submission> find(UUID submissionId) {
        return load("s.id = :id", Map.of("id", submissionId), 0, 1).stream().findFirst();
    }

    @Override
    public List<Submission> list(UUID userId, SubmissionFilter filter, int offset, int limit) {
        StringBuilder where = new StringBuilder("s.user_id = :user");
        Map<String, Object> params = new HashMap<>();
        params.put("user", userId);
        if (filter.taskType() != null) {
            where.append(" AND s.task_type = :type");
            params.put("type", filter.taskType().name());
        }
        if (filter.status() != null) {
            where.append(" AND s.status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.weekTaskId() != null) {
            where.append(" AND s.week_task_id = :weekTask");
            params.put("weekTask", filter.weekTaskId());
        }
        return load(where.toString(), params, offset, limit);
    }

    private List<Submission> load(String where, Map<String, Object> params, int offset, int limit) {
        List<Row> rows = jdbc.sql("SELECT * FROM submission s WHERE " + where
                        + " ORDER BY s.created_at DESC, s.id LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, n) -> row(rs))
                .list();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<SubmissionVersion>> versions = new HashMap<>();
        jdbc.sql("SELECT * FROM submission_version WHERE submission_id IN (:ids) ORDER BY version_no")
                .param("ids", rows.stream().map(Row::id).toList())
                .query((rs, n) -> {
                    versions.computeIfAbsent(rs.getObject("submission_id", UUID.class), k -> new ArrayList<>())
                            .add(version(rs));
                    return null;
                })
                .list();
        return rows.stream()
                .map(r -> new Submission(
                        r.id,
                        r.user,
                        r.set,
                        r.setVersion,
                        TaskType.valueOf(r.type),
                        AttemptMode.valueOf(r.mode),
                        r.weekTask,
                        r.exam,
                        r.created,
                        versions.getOrDefault(r.id, List.of()),
                        r.version))
                .toList();
    }

    private record Row(
            UUID id,
            UUID user,
            UUID set,
            int setVersion,
            String type,
            String mode,
            @Nullable UUID weekTask,
            @Nullable UUID exam,
            Instant created,
            int version) {}

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("item_set_id", UUID.class),
                rs.getInt("item_set_version"),
                rs.getString("task_type"),
                rs.getString("mode"),
                rs.getObject("week_task_id", UUID.class),
                rs.getObject("exam_session_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getInt("version"));
    }

    private static SubmissionVersion version(ResultSet rs) throws SQLException {
        Timestamp submitted = rs.getTimestamp("submitted_at");
        return new SubmissionVersion(
                rs.getObject("id", UUID.class),
                rs.getInt("version_no"),
                SubmissionStatus.valueOf(rs.getString("status")),
                rs.getString("body"),
                rs.getInt("word_count"),
                rs.getInt("active_seconds"),
                rs.getInt("overtime_seconds"),
                submitted == null ? null : submitted.toInstant(),
                rs.getBoolean("counts_for_estimate"));
    }

    /** One evaluation per evaluator and version: a newer one replaces the stored one. */
    @Override
    public void saveEvaluation(Evaluation e) {
        jdbc.sql("DELETE FROM evaluation WHERE submission_id = :s AND version_no = :v AND evaluator = :who")
                .param("s", e.submissionId())
                .param("v", e.versionNo())
                .param("who", e.evaluator().name())
                .update();
        jdbc.sql("""
                        INSERT INTO evaluation (id, submission_id, version_no, evaluator, tutor_id, rubric_id,
                            final_score, overall_score, criteria, strengths, priorities, passes, agreement,
                            needs_human_review, caps_applied, integrity_flags, prompt_key, prompt_version, model_id,
                            degraded, created_at)
                        VALUES (:id, :s, :v, :who, :tutor, :rubric, :final, :overall, :criteria::jsonb,
                            :strengths::jsonb, :priorities::jsonb, :passes::jsonb, :agreement, :review,
                            :caps::jsonb, :flags::jsonb, :promptKey, :promptVersion, :model, :degraded, :created)
                        """)
                .param("id", e.id())
                .param("s", e.submissionId())
                .param("v", e.versionNo())
                .param("who", e.evaluator().name())
                .param("tutor", e.tutorId())
                .param("rubric", e.rubricId())
                .param("final", e.finalScore())
                .param("overall", e.overallScore())
                .param("criteria", json.writeValueAsString(e.criteria()))
                .param("strengths", json.writeValueAsString(e.strengths()))
                .param("priorities", json.writeValueAsString(e.priorities()))
                .param("passes", json.writeValueAsString(e.passes()))
                .param("agreement", e.agreement() == null ? null : e.agreement().name())
                .param("review", e.needsHumanReview())
                .param("caps", json.writeValueAsString(e.capsApplied()))
                .param("flags", json.writeValueAsString(e.integrityFlags()))
                .param("promptKey", e.promptKey())
                .param("promptVersion", e.promptVersion())
                .param("model", e.modelId())
                .param("degraded", e.degraded())
                .param("created", Timestamp.from(e.createdAt()))
                .update();
        insertAnnotations(e.id(), e.annotations());
    }

    @Override
    public void deleteAiEvaluation(UUID submissionId, int versionNo) {
        jdbc.sql("DELETE FROM evaluation WHERE submission_id = :s AND version_no = :v AND evaluator = 'AI'")
                .param("s", submissionId)
                .param("v", versionNo)
                .update();
    }

    @Override
    public List<Evaluation> evaluations(UUID submissionId) {
        Map<UUID, List<InlineAnnotation>> annotations = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT a.* FROM inline_annotation a JOIN evaluation e ON e.id = a.evaluation_id
                        WHERE e.submission_id = :s ORDER BY a.ordinal
                        """)
                .param("s", submissionId)
                .query((rs, n) -> {
                    annotations
                            .computeIfAbsent(rs.getObject("evaluation_id", UUID.class), k -> new ArrayList<>())
                            .add(annotation(rs));
                    return null;
                })
                .list();
        return jdbc.sql("SELECT * FROM evaluation WHERE submission_id = :s ORDER BY version_no, evaluator")
                .param("s", submissionId)
                .query((rs, n) -> evaluation(rs, annotations))
                .list();
    }

    @Override
    public void updateAnnotations(UUID evaluationId, List<InlineAnnotation> annotations) {
        jdbc.sql("DELETE FROM inline_annotation WHERE evaluation_id = :e")
                .param("e", evaluationId)
                .update();
        insertAnnotations(evaluationId, annotations);
    }

    @Override
    public List<Double> firstVersionScores(UUID userId, TaskType type, int limit) {
        return jdbc.sql("""
                        SELECT COALESCE(t.final_score, a.final_score) AS score
                        FROM submission s
                        JOIN submission_version v ON v.submission_id = s.id AND v.version_no = 1
                        LEFT JOIN evaluation t ON t.submission_id = s.id AND t.version_no = 1 AND t.evaluator = 'TUTOR'
                        LEFT JOIN evaluation a ON a.submission_id = s.id AND a.version_no = 1 AND a.evaluator = 'AI'
                        WHERE s.user_id = :user AND s.task_type = :type AND (t.id IS NOT NULL OR a.id IS NOT NULL)
                        ORDER BY v.submitted_at DESC NULLS LAST, s.created_at DESC
                        LIMIT :limit
                        """)
                .param("user", userId)
                .param("type", type.name())
                .param("limit", limit)
                .query((rs, n) -> rs.getDouble("score"))
                .list();
    }

    private void insertAnnotations(UUID evaluationId, List<InlineAnnotation> annotations) {
        int ordinal = 0;
        for (InlineAnnotation a : annotations) {
            jdbc.sql("""
                            INSERT INTO inline_annotation (id, evaluation_id, ordinal, range_start, range_end,
                                anchor_quote, category, subcategory, severity, message, suggestion, grammar_topic,
                                unit_title, status)
                            VALUES (:id, :e, :ordinal, :start, :end, :quote, :category, :sub, :severity, :message,
                                :suggestion, :topic, :unit, :status)
                            """)
                    .param("id", a.id())
                    .param("e", evaluationId)
                    .param("ordinal", ordinal++)
                    .param("start", a.start())
                    .param("end", a.end())
                    .param("quote", a.anchorQuote())
                    .param("category", a.category().name())
                    .param("sub", a.subcategory())
                    .param("severity", a.severity().name())
                    .param("message", a.message())
                    .param("suggestion", a.suggestion())
                    .param("topic", a.grammarTopic())
                    .param("unit", a.unitTitle())
                    .param("status", a.status().name())
                    .update();
        }
    }

    private Evaluation evaluation(ResultSet rs, Map<UUID, List<InlineAnnotation>> annotations) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String agreement = rs.getString("agreement");
        return new Evaluation(
                id,
                rs.getObject("submission_id", UUID.class),
                rs.getInt("version_no"),
                EvaluatorType.valueOf(rs.getString("evaluator")),
                rs.getObject("tutor_id", UUID.class),
                rs.getString("rubric_id"),
                rs.getDouble("final_score"),
                json.readValue(rs.getString("criteria"), CRITERIA),
                json.readValue(rs.getString("strengths"), STRINGS),
                json.readValue(rs.getString("priorities"), STRINGS),
                annotations.getOrDefault(id, List.of()),
                json.readValue(rs.getString("passes"), PASSES),
                agreement == null ? null : EvaluationAgreement.valueOf(agreement),
                rs.getBoolean("needs_human_review"),
                json.readValue(rs.getString("caps_applied"), STRINGS),
                json.readValue(rs.getString("integrity_flags"), STRINGS),
                rs.getString("prompt_key"),
                rs.getInt("prompt_version"),
                rs.getString("model_id"),
                rs.getBoolean("degraded"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static InlineAnnotation annotation(ResultSet rs) throws SQLException {
        Integer start = (Integer) rs.getObject("range_start");
        Integer end = (Integer) rs.getObject("range_end");
        return new InlineAnnotation(
                rs.getObject("id", UUID.class),
                start,
                end,
                rs.getString("anchor_quote"),
                MistakeCategory.valueOf(rs.getString("category")),
                rs.getString("subcategory"),
                Severity.valueOf(rs.getString("severity")),
                rs.getString("message"),
                rs.getString("suggestion"),
                rs.getString("grammar_topic"),
                rs.getString("unit_title"),
                AnnotationStatus.valueOf(rs.getString("status")));
    }
}
