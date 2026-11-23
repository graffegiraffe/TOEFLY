package com.toefly.vocabulary.adapter.out.persistence;

import com.toefly.vocabulary.domain.card.ReviewCard;
import com.toefly.vocabulary.domain.entry.EntryContext;
import com.toefly.vocabulary.domain.entry.EntryOrigin;
import com.toefly.vocabulary.domain.entry.VocabularyEntry;
import com.toefly.vocabulary.domain.entry.WordDetails;
import com.toefly.vocabulary.domain.fsrs.CardMemory;
import com.toefly.vocabulary.domain.fsrs.CardState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Column lists and row mappers of vocabulary_entry and review_card; {@code e_} and {@code c_} prefixes in joins. */
final class Rows {

    static final JsonMapper JSON = JsonMapper.builder().build();

    static final String ENTRY_COLUMNS = """
            e.id AS e_id, e.user_id AS e_user_id, e.lemma AS e_lemma, e.part_of_speech AS e_part_of_speech,
            e.definition_en AS e_definition_en, e.example_en AS e_example_en, e.translation_ru AS e_translation_ru,
            e.ipa AS e_ipa, e.note AS e_note, e.contexts::text AS e_contexts, e.origin AS e_origin,
            e.word_set_id AS e_word_set_id, e.enriched AS e_enriched, e.created_at AS e_created_at,
            e.updated_at AS e_updated_at, e.version AS e_version""";

    static final String CARD_COLUMNS = """
            c.id AS c_id, c.entry_id AS c_entry_id, c.user_id AS c_user_id, c.state AS c_state, c.due AS c_due,
            c.stability AS c_stability, c.difficulty AS c_difficulty, c.reps AS c_reps, c.lapses AS c_lapses,
            c.last_review_at AS c_last_review_at, c.learning_step AS c_learning_step, c.suspended AS c_suspended,
            c.fsrs_params_version AS c_fsrs_params_version, c.created_at AS c_created_at, c.version AS c_version""";

    private Rows() {}

    static VocabularyEntry entry(ResultSet rs) throws SQLException {
        return new VocabularyEntry(
                Sql.requiredUuid(rs, "e_id"),
                Sql.requiredUuid(rs, "e_user_id"),
                rs.getString("e_lemma"),
                new WordDetails(
                        rs.getString("e_part_of_speech"),
                        rs.getString("e_definition_en"),
                        rs.getString("e_example_en"),
                        rs.getString("e_translation_ru"),
                        rs.getString("e_ipa")),
                rs.getString("e_note"),
                contexts(rs.getString("e_contexts")),
                EntryOrigin.valueOf(rs.getString("e_origin")),
                Sql.uuid(rs, "e_word_set_id"),
                rs.getBoolean("e_enriched"),
                Sql.requiredInstant(rs, "e_created_at"),
                Sql.requiredInstant(rs, "e_updated_at"),
                rs.getInt("e_version"));
    }

    static ReviewCard card(ResultSet rs) throws SQLException {
        return new ReviewCard(
                Sql.requiredUuid(rs, "c_id"),
                Sql.requiredUuid(rs, "c_entry_id"),
                Sql.requiredUuid(rs, "c_user_id"),
                new CardMemory(
                        CardState.valueOf(rs.getString("c_state")),
                        rs.getInt("c_learning_step"),
                        Sql.number(rs, "c_stability"),
                        Sql.number(rs, "c_difficulty"),
                        Sql.requiredInstant(rs, "c_due"),
                        Sql.instant(rs, "c_last_review_at"),
                        rs.getInt("c_reps"),
                        rs.getInt("c_lapses")),
                rs.getBoolean("c_suspended"),
                rs.getString("c_fsrs_params_version"),
                Sql.requiredInstant(rs, "c_created_at"),
                rs.getInt("c_version"));
    }

    static String contextsJson(List<EntryContext> contexts) {
        ArrayNode array = JSON.createArrayNode();
        for (EntryContext context : contexts) {
            ObjectNode node = array.addObject();
            node.put("sentence", context.sentence());
            node.put(
                    "itemSetId",
                    context.itemSetId() == null ? null : context.itemSetId().toString());
            node.put("sourceLabel", context.sourceLabel());
        }
        return JSON.writeValueAsString(array);
    }

    private static List<EntryContext> contexts(String json) {
        List<EntryContext> contexts = new ArrayList<>();
        for (JsonNode node : JSON.readTree(json)) {
            String itemSet = node.path("itemSetId").isNull()
                    ? null
                    : node.path("itemSetId").asString(null);
            contexts.add(new EntryContext(
                    node.path("sentence").asString(""),
                    itemSet == null || itemSet.isBlank() ? null : UUID.fromString(itemSet),
                    node.path("sourceLabel").asString("")));
        }
        return contexts;
    }
}
