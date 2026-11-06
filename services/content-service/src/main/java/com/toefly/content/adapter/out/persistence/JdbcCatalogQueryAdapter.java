package com.toefly.content.adapter.out.persistence;

import com.toefly.content.application.port.in.BrowseCatalogUseCase.Filter;
import com.toefly.content.application.port.in.BrowseCatalogUseCase.Hit;
import com.toefly.content.application.port.in.BrowseCatalogUseCase.Row;
import com.toefly.content.application.port.out.CatalogQueryPort;
import com.toefly.content.domain.catalog.ExamFormat;
import com.toefly.content.domain.catalog.Source;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCatalogQueryAdapter implements CatalogQueryPort {

    private static final String SELECT = "SELECT "
            + Arrays.stream(JdbcItemSetStoreAdapter.COLUMNS.split(","))
                    .map(c -> "i." + c.strip())
                    .collect(Collectors.joining(", "))
            + ", s.code AS s_code, s.title AS s_title, s.publisher AS s_publisher, s.year AS s_year, s.url AS s_url,"
            + " s.exam_format AS s_format, s.attribution_text AS s_attr, s.extra::text AS s_extra,"
            + " (SELECT count(*) FROM item_report r WHERE r.item_set_id = i.id AND r.status = 'OPEN') AS open_reports"
            + " FROM item_set i JOIN source s ON s.id = i.source_id";

    private final JdbcClient jdbc;

    public JdbcCatalogQueryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Row> list(Filter f, @Nullable Instant afterCreatedAt, @Nullable UUID afterId, int limit) {
        Map<String, Object> params = new HashMap<>();
        List<String> where = where(f, params);
        if (afterCreatedAt != null && afterId != null) {
            where.add("(i.created_at, i.id) > (:afterAt, :afterId)");
            params.put("afterAt", Timestamp.from(afterCreatedAt));
            params.put("afterId", afterId);
        }
        params.put("limit", limit);
        return jdbc.sql(SELECT + clause(where) + " ORDER BY i.created_at, i.id LIMIT :limit")
                .params(params)
                .query(JdbcCatalogQueryAdapter::row)
                .list();
    }

    @Override
    public List<Hit> fullText(String query, Filter f, int limit) {
        Map<String, Object> params = new HashMap<>();
        List<String> where = where(f, params);
        where.add("i.search_tsv @@ websearch_to_tsquery('english', :q)");
        params.put("q", query);
        params.put("limit", limit);
        return jdbc.sql(SELECT.replace(
                                " FROM item_set i",
                                ", ts_rank(i.search_tsv, websearch_to_tsquery('english', :q)) AS score FROM item_set i")
                        + clause(where) + " ORDER BY score DESC, i.id LIMIT :limit")
                .params(params)
                .query((rs, n) -> new Hit(row(rs, n), rs.getDouble("score")))
                .list();
    }

    @Override
    public List<Hit> nearest(float[] embedding, Filter f, int limit) {
        Map<String, Object> params = new HashMap<>();
        List<String> where = where(f, params);
        params.put("v", vector(embedding));
        params.put("limit", limit);
        String sql = SELECT.replace(
                        " FROM item_set i",
                        ", 1 - (SELECT min(e.embedding <=> CAST(:v AS vector)) FROM item_set_embedding e"
                                + " WHERE e.item_set_id = i.id) AS score FROM item_set i")
                + clause(where) + (where.isEmpty() ? " WHERE" : " AND")
                + " EXISTS (SELECT 1 FROM item_set_embedding e WHERE e.item_set_id = i.id)"
                + " ORDER BY score DESC, i.id LIMIT :limit";
        return jdbc.sql(sql)
                .params(params)
                .query((rs, n) -> new Hit(row(rs, n), rs.getDouble("score")))
                .list();
    }

    static String vector(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            sb.append(i == 0 ? "" : ",").append(v[i]);
        }
        return sb.append(']').toString();
    }

    private static List<String> where(Filter f, Map<String, Object> p) {
        List<String> w = new ArrayList<>();
        eq(
                w,
                p,
                "i.section",
                "section",
                f.section() == null ? null : f.section().name());
        eq(
                w,
                p,
                "i.task_type",
                "taskType",
                f.taskType() == null ? null : f.taskType().name());
        eq(
                w,
                p,
                "i.exam_format",
                "examFormat",
                f.examFormat() == null ? null : f.examFormat().name());
        eq(
                w,
                p,
                "i.provenance",
                "provenance",
                f.provenance() == null ? null : f.provenance().name());
        eq(w, p, "i.cefr", "cefr", f.cefr());
        eq(w, p, "s.code", "sourceCode", f.sourceCode());
        eq(w, p, "i.status", "status", f.status() == null ? null : f.status().name());
        if (f.legacy() != null) {
            w.add(f.legacy() ? "i.exam_format = 'LEGACY'" : "i.exam_format <> 'LEGACY'");
        }
        if (Boolean.TRUE.equals(f.reported())) {
            w.add("EXISTS (SELECT 1 FROM item_report r WHERE r.item_set_id = i.id AND r.status = 'OPEN')");
        }
        return w;
    }

    private static void eq(List<String> w, Map<String, Object> p, String column, String name, @Nullable String value) {
        if (value != null) {
            w.add(column + " = :" + name);
            p.put(name, value);
        }
    }

    private static String clause(List<String> where) {
        return where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        int year = rs.getInt("s_year");
        boolean noYear = rs.wasNull();
        Source source = new Source(
                rs.getObject("source_id", UUID.class),
                rs.getString("s_code"),
                rs.getString("s_title"),
                rs.getString("s_publisher"),
                noYear ? null : year,
                rs.getString("s_url"),
                ExamFormat.valueOf(rs.getString("s_format")),
                rs.getString("s_attr"),
                rs.getString("s_extra"));
        return new Row(JdbcItemSetStoreAdapter.map(rs, n), source, rs.getInt("open_reports"));
    }
}
