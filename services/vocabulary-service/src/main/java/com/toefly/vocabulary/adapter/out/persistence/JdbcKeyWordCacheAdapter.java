package com.toefly.vocabulary.adapter.out.persistence;

import com.toefly.vocabulary.application.port.out.KeyWord;
import com.toefly.vocabulary.application.port.out.KeyWordCachePort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** key_word_cache: key words of an ItemSet for all learners (AC-15.02.1). */
@Repository
public class JdbcKeyWordCacheAdapter implements KeyWordCachePort {

    private final JdbcClient jdbc;

    public JdbcKeyWordCacheAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<List<KeyWord>> find(UUID itemSetId) {
        return jdbc.sql("SELECT words::text FROM key_word_cache WHERE item_set_id = :id")
                .param("id", itemSetId)
                .query(String.class)
                .optional()
                .map(JdbcKeyWordCacheAdapter::words);
    }

    @Override
    public void put(UUID itemSetId, List<KeyWord> words, String aiStamp) {
        ArrayNode array = Rows.JSON.createArrayNode();
        for (KeyWord word : words) {
            ObjectNode node = array.addObject();
            node.put("lemma", word.lemma());
            node.put("partOfSpeech", word.partOfSpeech());
            node.put("definitionEn", word.definitionEn());
            node.put("sentence", word.sentence());
        }
        jdbc.sql("""
                        INSERT INTO key_word_cache (item_set_id, words, ai_stamp, created_at)
                        VALUES (:id, :words::jsonb, :stamp::jsonb, :at)
                        ON CONFLICT (item_set_id) DO NOTHING
                        """)
                .param("id", itemSetId)
                .param("words", Rows.JSON.writeValueAsString(array))
                .param("stamp", aiStamp == null || aiStamp.isBlank() ? "{}" : aiStamp)
                .param("at", Sql.ts(Instant.now()))
                .update();
    }

    private static List<KeyWord> words(String json) {
        List<KeyWord> words = new ArrayList<>();
        for (JsonNode node : Rows.JSON.readTree(json)) {
            words.add(new KeyWord(
                    node.path("lemma").asString(""),
                    text(node, "partOfSpeech"),
                    text(node, "definitionEn"),
                    text(node, "sentence")));
        }
        return words;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }
}
