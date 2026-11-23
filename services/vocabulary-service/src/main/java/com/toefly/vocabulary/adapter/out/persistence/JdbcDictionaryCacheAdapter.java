package com.toefly.vocabulary.adapter.out.persistence;

import com.toefly.vocabulary.application.port.out.DictionaryCachePort;
import com.toefly.vocabulary.domain.entry.WordDetails;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** dictionary_cache: one row per lemma, no user data (INV-VOC-06). */
@Repository
public class JdbcDictionaryCacheAdapter implements DictionaryCachePort {

    private final JdbcClient jdbc;

    public JdbcDictionaryCacheAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WordDetails> find(String lemma) {
        return jdbc.sql("""
                        SELECT part_of_speech, definition_en, example_en, translation_ru, ipa
                        FROM dictionary_cache WHERE lemma = :lemma
                        """)
                .param("lemma", lemma)
                .query((rs, row) -> new WordDetails(
                        rs.getString("part_of_speech"),
                        rs.getString("definition_en"),
                        rs.getString("example_en"),
                        rs.getString("translation_ru"),
                        rs.getString("ipa")))
                .optional();
    }

    @Override
    public void put(String lemma, WordDetails details, Source source, String aiStamp) {
        jdbc.sql("""
                        INSERT INTO dictionary_cache (lemma, part_of_speech, definition_en, example_en, translation_ru,
                            ipa, source, ai_stamp, created_at)
                        VALUES (:lemma, :pos, :definition, :example, :translation, :ipa, :source, :stamp::jsonb, :at)
                        ON CONFLICT (lemma) DO NOTHING
                        """)
                .param("lemma", lemma)
                .param("pos", JdbcEntryRepositoryAdapter.clip(details.partOfSpeech(), 16))
                .param("definition", details.definitionEn())
                .param("example", details.exampleEn())
                .param("translation", JdbcEntryRepositoryAdapter.clip(details.translationRu(), 200))
                .param("ipa", JdbcEntryRepositoryAdapter.clip(details.ipa(), 80))
                .param("source", source.name())
                .param("stamp", aiStamp == null || aiStamp.isBlank() ? "{}" : aiStamp)
                .param("at", Sql.ts(Instant.now()))
                .update();
    }
}
