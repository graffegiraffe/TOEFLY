package com.toefly.content.adapter.out.persistence;

import com.toefly.content.application.port.out.SourceStorePort;
import com.toefly.content.domain.catalog.ExamFormat;
import com.toefly.content.domain.catalog.Source;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSourceStoreAdapter implements SourceStorePort {

    private static final String COLUMNS =
            "id, code, title, publisher, year, url, exam_format, attribution_text, extra::text AS extra";

    private final JdbcClient jdbc;

    public JdbcSourceStoreAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Source upsert(Source s) {
        jdbc.sql("""
                        INSERT INTO source (id, code, title, publisher, year, url, exam_format, legacy,
                                            attribution_text, extra)
                        VALUES (:id, :code, :title, :publisher, :year, :url, :format, :legacy, :attribution,
                                CAST(:extra AS jsonb))
                        ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title, publisher = EXCLUDED.publisher,
                            year = EXCLUDED.year, url = EXCLUDED.url, exam_format = EXCLUDED.exam_format,
                            legacy = EXCLUDED.legacy, attribution_text = EXCLUDED.attribution_text,
                            extra = EXCLUDED.extra
                        """)
                .param("id", s.id())
                .param("code", s.code())
                .param("title", s.title())
                .param("publisher", s.publisher())
                .param("year", s.year())
                .param("url", s.url())
                .param("format", s.examFormat().name())
                .param("legacy", s.legacy())
                .param("attribution", s.attributionText())
                .param("extra", s.extraJson())
                .update();
        return findByCode(s.code()).orElseThrow();
    }

    @Override
    public Optional<Source> findByCode(String code) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM source WHERE code = :code")
                .param("code", code)
                .query(JdbcSourceStoreAdapter::map)
                .optional();
    }

    @Override
    public Optional<Source> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM source WHERE id = :id")
                .param("id", id)
                .query(JdbcSourceStoreAdapter::map)
                .optional();
    }

    @Override
    public List<Source> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM source ORDER BY code")
                .query(JdbcSourceStoreAdapter::map)
                .list();
    }

    private static Source map(ResultSet rs, int row) throws SQLException {
        int year = rs.getInt("year");
        return new Source(
                rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("title"),
                rs.getString("publisher"), rs.wasNull() ? null : year, rs.getString("url"),
                ExamFormat.valueOf(rs.getString("exam_format")), rs.getString("attribution_text"),
                rs.getString("extra"));
    }
}
