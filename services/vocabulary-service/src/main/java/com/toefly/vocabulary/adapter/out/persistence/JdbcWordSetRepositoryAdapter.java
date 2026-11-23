package com.toefly.vocabulary.adapter.out.persistence;

import com.toefly.vocabulary.application.port.out.WordSetRepository;
import com.toefly.vocabulary.domain.wordset.WordSet;
import com.toefly.vocabulary.domain.wordset.WordSetWord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** word_set and word_set_word: the Collins units copied from content-service. */
@Repository
public class JdbcWordSetRepositoryAdapter implements WordSetRepository {

    private final JdbcClient jdbc;

    public JdbcWordSetRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<WordSet> all() {
        return load(null);
    }

    @Override
    public Optional<WordSet> find(UUID wordSetId) {
        return load(wordSetId).stream().findFirst();
    }

    @Override
    public void upsert(WordSet set, Instant syncedAt) {
        // a unit number moved to another id (content re-imported) frees its number first
        jdbc.sql("DELETE FROM word_set WHERE unit_no = :no AND id <> :id")
                .param("no", set.unitNo())
                .param("id", set.id())
                .update();
        jdbc.sql("""
                        INSERT INTO word_set (id, unit_no, theme, title, synced_at) VALUES (:id, :no, :theme, :title, :at)
                        ON CONFLICT (id) DO UPDATE SET unit_no = EXCLUDED.unit_no, theme = EXCLUDED.theme,
                            title = EXCLUDED.title, synced_at = EXCLUDED.synced_at
                        """)
                .param("id", set.id())
                .param("no", set.unitNo())
                .param("theme", set.theme())
                .param("title", JdbcEntryRepositoryAdapter.clip(set.title(), 200))
                .param("at", Sql.ts(syncedAt))
                .update();
        jdbc.sql("DELETE FROM word_set_word WHERE word_set_id = :id")
                .param("id", set.id())
                .update();
        int ordinal = 0;
        for (WordSetWord word : set.words()) {
            jdbc.sql("""
                            INSERT INTO word_set_word (word_set_id, ordinal, lemma, part_of_speech, definition_en,
                                example_en, ipa)
                            VALUES (:id, :ordinal, :lemma, :pos, :definition, :example, :ipa)
                            """)
                    .param("id", set.id())
                    .param("ordinal", ordinal++)
                    .param("lemma", JdbcEntryRepositoryAdapter.clip(word.lemma(), 80))
                    .param("pos", JdbcEntryRepositoryAdapter.clip(word.partOfSpeech(), 16))
                    .param("definition", word.definitionEn())
                    .param("example", word.exampleEn())
                    .param("ipa", JdbcEntryRepositoryAdapter.clip(word.ipa(), 80))
                    .update();
        }
    }

    @Override
    public Map<UUID, Progress> progress(UUID userId) {
        Map<UUID, Progress> progress = new HashMap<>();
        jdbc.sql("""
                        SELECT w.word_set_id, count(e.id) AS in_dictionary,
                               count(e.id) FILTER (WHERE c.state = 'REVIEW') AS learned
                        FROM word_set_word w
                        JOIN vocabulary_entry e ON e.user_id = :user AND e.lemma = lower(btrim(w.lemma))
                        JOIN review_card c ON c.entry_id = e.id
                        GROUP BY w.word_set_id
                        """)
                .param("user", userId)
                .query((rs, row) -> progress.put(
                        Sql.requiredUuid(rs, "word_set_id"),
                        new Progress(rs.getInt("in_dictionary"), rs.getInt("learned"))))
                .list();
        return progress;
    }

    private List<WordSet> load(UUID onlyId) {
        Map<UUID, Object[]> heads = new LinkedHashMap<>();
        Map<UUID, List<WordSetWord>> words = new HashMap<>();
        String filter = onlyId == null ? "" : " WHERE s.id = :id";
        var spec =
                jdbc.sql("SELECT s.id, s.unit_no, s.theme, s.title FROM word_set s" + filter + " ORDER BY s.unit_no");
        if (onlyId != null) {
            spec = spec.param("id", onlyId);
        }
        spec.query((rs, row) -> heads.put(
                        Sql.requiredUuid(rs, "id"),
                        new Object[] {rs.getInt("unit_no"), rs.getString("theme"), rs.getString("title")}))
                .list();
        if (heads.isEmpty()) {
            return List.of();
        }
        jdbc.sql("""
                        SELECT word_set_id, lemma, part_of_speech, definition_en, example_en, ipa FROM word_set_word
                        WHERE word_set_id IN (:ids) ORDER BY word_set_id, ordinal
                        """)
                .param("ids", heads.keySet())
                .query((rs, row) -> words.computeIfAbsent(Sql.requiredUuid(rs, "word_set_id"), id -> new ArrayList<>())
                        .add(new WordSetWord(
                                rs.getString("lemma"),
                                rs.getString("part_of_speech"),
                                rs.getString("definition_en"),
                                rs.getString("example_en"),
                                rs.getString("ipa"))))
                .list();
        List<WordSet> sets = new ArrayList<>();
        heads.forEach((id, head) -> sets.add(new WordSet(
                id, (Integer) head[0], (String) head[1], (String) head[2], words.getOrDefault(id, List.of()))));
        return sets;
    }
}
