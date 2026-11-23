package com.toefly.vocabulary.adapter.out.persistence;

import com.toefly.vocabulary.application.port.in.EntryPage;
import com.toefly.vocabulary.application.port.in.EntryQuery;
import com.toefly.vocabulary.application.port.in.EntryRow;
import com.toefly.vocabulary.application.port.out.EntryRepository;
import com.toefly.vocabulary.domain.entry.VocabularyEntry;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** vocabulary_entry (docs/03 §6.11). */
@Repository
public class JdbcEntryRepositoryAdapter implements EntryRepository {

    private static final String SELECT = "SELECT " + Rows.ENTRY_COLUMNS + " FROM vocabulary_entry e ";
    private static final String SELECT_ROW = "SELECT " + Rows.ENTRY_COLUMNS + ", " + Rows.CARD_COLUMNS
            + " FROM vocabulary_entry e JOIN review_card c ON c.entry_id = e.id ";

    private final JdbcClient jdbc;

    public JdbcEntryRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<VocabularyEntry> find(UUID userId, UUID entryId) {
        return jdbc.sql(SELECT + "WHERE e.user_id = :user AND e.id = :id")
                .param("user", userId)
                .param("id", entryId)
                .query((rs, row) -> Rows.entry(rs))
                .optional();
    }

    @Override
    public List<VocabularyEntry> findAll(UUID userId, Collection<UUID> entryIds) {
        if (entryIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + "WHERE e.user_id = :user AND e.id IN (:ids)")
                .param("user", userId)
                .param("ids", entryIds)
                .query((rs, row) -> Rows.entry(rs))
                .list();
    }

    @Override
    public Optional<VocabularyEntry> findByLemma(UUID userId, String lemma) {
        return jdbc.sql(SELECT + "WHERE e.user_id = :user AND e.lemma = :lemma")
                .param("user", userId)
                .param("lemma", lemma)
                .query((rs, row) -> Rows.entry(rs))
                .optional();
    }

    @Override
    public Optional<VocabularyEntry> findByLemmaForUpdate(UUID userId, String lemma) {
        return jdbc.sql(SELECT + "WHERE e.user_id = :user AND e.lemma = :lemma FOR UPDATE")
                .param("user", userId)
                .param("lemma", lemma)
                .query((rs, row) -> Rows.entry(rs))
                .optional();
    }

    @Override
    public boolean insert(VocabularyEntry e) {
        return jdbc.sql("""
                        INSERT INTO vocabulary_entry (id, user_id, lemma, part_of_speech, definition_en, example_en,
                            translation_ru, ipa, note, contexts, origin, word_set_id, enriched, created_at,
                            updated_at, version)
                        VALUES (:id, :user, :lemma, :pos, :definition, :example, :translation, :ipa, :note,
                            :contexts::jsonb, :origin, :wordSet, :enriched, :created, :updated, 0)
                        ON CONFLICT (user_id, lemma) DO NOTHING
                        """).paramSource(params(e)).update() == 1;
    }

    @Override
    public VocabularyEntry update(VocabularyEntry e) {
        int updated = jdbc.sql("""
                        UPDATE vocabulary_entry SET part_of_speech = :pos, definition_en = :definition,
                            example_en = :example, translation_ru = :translation, ipa = :ipa, note = :note,
                            contexts = :contexts::jsonb, enriched = :enriched, updated_at = :updated,
                            version = version + 1
                        WHERE id = :id AND user_id = :user AND version = :version
                        """)
                .paramSource(params(e).addValue("version", e.version()))
                .update();
        if (updated != 1) {
            throw new OptimisticLockingFailureException("vocabulary_entry " + e.id() + " changed concurrently");
        }
        return new VocabularyEntry(
                e.id(),
                e.userId(),
                e.lemma(),
                e.details(),
                e.note(),
                e.contexts(),
                e.origin(),
                e.wordSetId(),
                e.enriched(),
                e.createdAt(),
                e.updatedAt(),
                e.version() + 1);
    }

    @Override
    public void delete(UUID userId, UUID entryId) {
        jdbc.sql("DELETE FROM vocabulary_entry WHERE user_id = :user AND id = :id")
                .param("user", userId)
                .param("id", entryId)
                .update();
    }

    @Override
    public Set<String> existingLemmas(UUID userId, Collection<String> lemmas) {
        if (lemmas.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT lemma FROM vocabulary_entry WHERE user_id = :user AND lemma IN (:lemmas)")
                .param("user", userId)
                .param("lemmas", lemmas)
                .query(String.class)
                .list());
    }

    @Override
    public List<VocabularyEntry> unenriched(int limit, int maxAttempts) {
        return jdbc.sql(SELECT + "WHERE NOT e.enriched AND e.enrich_attempts < :max ORDER BY e.created_at LIMIT :limit")
                .param("max", maxAttempts)
                .param("limit", limit)
                .query((rs, row) -> Rows.entry(rs))
                .list();
    }

    @Override
    public void countEnrichAttempt(UUID entryId) {
        jdbc.sql("UPDATE vocabulary_entry SET enrich_attempts = enrich_attempts + 1 WHERE id = :id")
                .param("id", entryId)
                .update();
    }

    @Override
    public EntryPage page(UUID userId, EntryQuery query, Instant endOfDay) {
        StringBuilder where = new StringBuilder("WHERE e.user_id = :user");
        MapSqlParameterSource params = new MapSqlParameterSource("user", userId);
        if (query.q() != null) {
            where.append(" AND (e.lemma LIKE :prefix OR similarity(e.lemma, :q) > 0.3)");
            params.addValue("q", query.q().toLowerCase(java.util.Locale.ROOT));
            params.addValue("prefix", escape(query.q().toLowerCase(java.util.Locale.ROOT)) + "%");
        }
        if (query.status() != null) {
            where.append(
                    switch (query.status()) {
                        case NEW -> " AND c.state = 'NEW'";
                        case LEARNING -> " AND c.state IN ('LEARNING', 'RELEARNING')";
                        case REVIEW -> " AND c.state = 'REVIEW' AND c.stability < 90";
                        case MASTERED -> " AND c.state = 'REVIEW' AND c.stability >= 90";
                    });
        }
        if (query.origin() != null) {
            where.append(" AND e.origin = :origin");
            params.addValue("origin", query.origin().name());
        }
        if (query.wordSetId() != null) {
            where.append(" AND e.word_set_id = :wordSet");
            params.addValue("wordSet", query.wordSetId());
        }
        if (query.dueToday()) {
            where.append(" AND NOT c.suspended AND c.state <> 'NEW' AND c.due < :endOfDay");
            params.addValue("endOfDay", Sql.ts(endOfDay));
        }
        String order = switch (query.sort()) {
            case CREATED_DESC -> " ORDER BY e.created_at DESC, e.id DESC";
            case CREATED_ASC -> " ORDER BY e.created_at, e.id";
            case DIFFICULTY_DESC -> " ORDER BY c.difficulty DESC, e.created_at DESC, e.id DESC";
            case LEMMA -> " ORDER BY e.lemma, e.id";
        };
        long total = jdbc.sql(
                        "SELECT count(*) FROM vocabulary_entry e JOIN review_card c ON c.entry_id = e.id " + where)
                .paramSource(params)
                .query(Long.class)
                .single();
        params.addValue("limit", query.limit() + 1);
        params.addValue("offset", query.offset());
        List<EntryRow> rows = jdbc.sql(SELECT_ROW + where + order + " LIMIT :limit OFFSET :offset")
                .paramSource(params)
                .query((rs, row) -> new EntryRow(Rows.entry(rs), Rows.card(rs)))
                .list();
        boolean more = rows.size() > query.limit();
        return new EntryPage(more ? rows.subList(0, query.limit()) : rows, total, more);
    }

    @Override
    public long count(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM vocabulary_entry WHERE user_id = :user")
                .param("user", userId)
                .query(Long.class)
                .single();
    }

    @Override
    public List<VocabularyEntry> recent(UUID userId, int limit) {
        return jdbc.sql(SELECT + "WHERE e.user_id = :user ORDER BY e.created_at DESC, e.id DESC LIMIT :limit")
                .param("user", userId)
                .param("limit", limit)
                .query((rs, row) -> Rows.entry(rs))
                .list();
    }

    @Override
    public List<EntryRow> all(UUID userId) {
        return jdbc.sql(SELECT_ROW + "WHERE e.user_id = :user ORDER BY e.created_at, e.id")
                .param("user", userId)
                .query((rs, row) -> new EntryRow(Rows.entry(rs), Rows.card(rs)))
                .list();
    }

    @Override
    public void deleteAllOf(UUID userId) {
        jdbc.sql("DELETE FROM vocabulary_entry WHERE user_id = :user")
                .param("user", userId)
                .update();
    }

    private static MapSqlParameterSource params(VocabularyEntry e) {
        return new MapSqlParameterSource()
                .addValue("id", e.id())
                .addValue("user", e.userId())
                .addValue("lemma", e.lemma())
                .addValue("pos", clip(e.details().partOfSpeech(), 16))
                .addValue("definition", e.details().definitionEn())
                .addValue("example", e.details().exampleEn())
                .addValue("translation", clip(e.details().translationRu(), 200))
                .addValue("ipa", clip(e.details().ipa(), 80))
                .addValue("note", e.note())
                .addValue("contexts", Rows.contextsJson(e.contexts()))
                .addValue("origin", e.origin().name())
                .addValue("wordSet", e.wordSetId())
                .addValue("enriched", e.enriched())
                .addValue("created", Sql.ts(e.createdAt()))
                .addValue("updated", Sql.ts(e.updatedAt()));
    }

    static @org.jspecify.annotations.Nullable String clip(@org.jspecify.annotations.Nullable String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
