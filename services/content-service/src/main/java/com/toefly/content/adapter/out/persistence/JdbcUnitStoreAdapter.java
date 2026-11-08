package com.toefly.content.adapter.out.persistence;

import com.toefly.content.application.port.in.BrowseUnitsUseCase.UnitView;
import com.toefly.content.application.port.out.UnitStorePort;
import com.toefly.content.application.seed.SeedUnit;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUnitStoreAdapter implements UnitStorePort {

    private final JdbcClient jdbc;

    public JdbcUnitStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String table(SeedUnit.Kind kind) {
        return kind == SeedUnit.Kind.GRAMMAR_UNIT ? "grammar_unit" : "vocabulary_unit";
    }

    @Override
    public Optional<String> contentHash(SeedUnit.Kind kind, UUID id) {
        return jdbc.sql("SELECT content_hash FROM " + table(kind) + " WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .optional()
                .map(String::strip);
    }

    @Override
    public void insert(SeedUnit u, UUID sourceId, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        if (u.kind() == SeedUnit.Kind.GRAMMAR_UNIT) {
            jdbc.sql("""
                            INSERT INTO grammar_unit (id, seed_id, source_id, framework, unit_no, title, topic_codes,
                                rule_card, progress_check_no, exercises, status, content_hash, created_at, updated_at)
                            VALUES (:id, :seed, :source, :framework, :no, :title, :topics, CAST(:rule AS jsonb),
                                :check, CAST(:exercises AS jsonb), :status, :hash, :now, :now)
                            """)
                    .param("id", u.id())
                    .param("seed", u.seedId())
                    .param("source", sourceId)
                    .param("framework", u.framework())
                    .param("no", u.unitNo())
                    .param("title", u.title())
                    .param("topics", u.topicCodes().toArray(String[]::new))
                    .param("rule", u.ruleCardJson())
                    .param("check", u.progressCheckNo())
                    .param("exercises", u.exercisesJson())
                    .param("status", status)
                    .param("hash", u.contentHash())
                    .param("now", now)
                    .update();
        } else {
            jdbc.sql("""
                            INSERT INTO vocabulary_unit (id, seed_id, source_id, unit_no, theme, title, words,
                                exercises, status, content_hash, created_at, updated_at)
                            VALUES (:id, :seed, :source, :no, :theme, :title, CAST(:words AS jsonb),
                                CAST(:exercises AS jsonb), :status, :hash, :now, :now)
                            """)
                    .param("id", u.id())
                    .param("seed", u.seedId())
                    .param("source", sourceId)
                    .param("no", u.unitNo())
                    .param("theme", u.theme())
                    .param("title", u.title())
                    .param("words", u.wordsJson())
                    .param("exercises", u.exercisesJson())
                    .param("status", status)
                    .param("hash", u.contentHash())
                    .param("now", now)
                    .update();
        }
    }

    @Override
    public void updateContent(SeedUnit u) {
        Timestamp now = Timestamp.from(Instant.now());
        if (u.kind() == SeedUnit.Kind.GRAMMAR_UNIT) {
            jdbc.sql("""
                            UPDATE grammar_unit SET title = :title, topic_codes = :topics,
                                rule_card = CAST(:rule AS jsonb), progress_check_no = :check,
                                exercises = CAST(:exercises AS jsonb), content_hash = :hash, updated_at = :now
                            WHERE id = :id
                            """)
                    .param("id", u.id())
                    .param("title", u.title())
                    .param("topics", u.topicCodes().toArray(String[]::new))
                    .param("rule", u.ruleCardJson())
                    .param("check", u.progressCheckNo())
                    .param("exercises", u.exercisesJson())
                    .param("hash", u.contentHash())
                    .param("now", now)
                    .update();
        } else {
            jdbc.sql("""
                            UPDATE vocabulary_unit SET theme = :theme, title = :title,
                                words = CAST(:words AS jsonb), exercises = CAST(:exercises AS jsonb),
                                content_hash = :hash, updated_at = :now
                            WHERE id = :id
                            """)
                    .param("id", u.id())
                    .param("theme", u.theme())
                    .param("title", u.title())
                    .param("words", u.wordsJson())
                    .param("exercises", u.exercisesJson())
                    .param("hash", u.contentHash())
                    .param("now", now)
                    .update();
        }
    }

    private static final String GRAMMAR = """
            SELECT u.id, s.code, u.unit_no, u.title, u.status, u.framework, NULL AS theme, u.topic_codes,
                   u.progress_check_no, u.rule_card::text AS rule_card, '[]' AS words, u.exercises::text AS exercises
            FROM grammar_unit u JOIN source s ON s.id = u.source_id
            """;
    private static final String VOCAB = """
            SELECT u.id, s.code, u.unit_no, u.title, u.status, NULL AS framework, u.theme, '{}'::text[] AS topic_codes,
                   NULL::smallint AS progress_check_no, NULL AS rule_card, u.words::text AS words,
                   u.exercises::text AS exercises
            FROM vocabulary_unit u JOIN source s ON s.id = u.source_id
            """;

    @Override
    public List<UnitView> list(SeedUnit.Kind kind, boolean approvedOnly, @Nullable String framework) {
        String base = kind == SeedUnit.Kind.GRAMMAR_UNIT ? GRAMMAR : VOCAB;
        String where = " WHERE (:all OR u.status = 'APPROVED')"
                + (kind == SeedUnit.Kind.GRAMMAR_UNIT
                        ? " AND (CAST(:framework AS text) IS NULL OR u.framework = :framework)"
                        : "");
        var spec = jdbc.sql(base + where + " ORDER BY " + (kind == SeedUnit.Kind.GRAMMAR_UNIT ? "u.framework, " : "")
                        + "u.unit_no")
                .param("all", !approvedOnly);
        if (kind == SeedUnit.Kind.GRAMMAR_UNIT) {
            spec = spec.param("framework", framework);
        }
        return spec.query((rs, n) -> map(rs, kind)).list();
    }

    @Override
    public Optional<UnitView> find(SeedUnit.Kind kind, UUID id) {
        String base = kind == SeedUnit.Kind.GRAMMAR_UNIT ? GRAMMAR : VOCAB;
        return jdbc.sql(base + " WHERE u.id = :id")
                .param("id", id)
                .query((rs, n) -> map(rs, kind))
                .optional();
    }

    @Override
    public boolean approve(SeedUnit.Kind kind, UUID id) {
        return jdbc.sql("UPDATE " + table(kind) + " SET status = 'APPROVED', updated_at = now() WHERE id = :id")
                        .param("id", id)
                        .update()
                == 1;
    }

    private static UnitView map(ResultSet rs, SeedUnit.Kind kind) throws SQLException {
        int check = rs.getInt("progress_check_no");
        boolean noCheck = rs.wasNull();
        return new UnitView(
                rs.getObject("id", UUID.class),
                kind,
                rs.getString("code"),
                rs.getInt("unit_no"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getString("framework"),
                rs.getString("theme"),
                Arrays.asList((String[]) rs.getArray("topic_codes").getArray()),
                noCheck ? null : check,
                rs.getString("rule_card"),
                rs.getString("words"),
                rs.getString("exercises"));
    }
}
