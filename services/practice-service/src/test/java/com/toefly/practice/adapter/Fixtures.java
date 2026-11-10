package com.toefly.practice.adapter;

import com.toefly.practice.application.port.out.ItemPoolPort.PoolItem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Item sets of every closed type with our own texts, in the format of the internal content API. */
final class Fixtures {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Path DIR = Path.of("src/test/resources/fixtures/item-sets");
    static final List<String> TYPES = List.of(
            "COMPLETE_THE_WORDS",
            "READ_DAILY_LIFE",
            "READ_ACADEMIC_PASSAGE",
            "LISTEN_CHOOSE_RESPONSE",
            "LISTEN_CONVERSATION",
            "LISTEN_ANNOUNCEMENT",
            "LISTEN_ACADEMIC_TALK",
            "BUILD_A_SENTENCE",
            "GRAMMAR_EXERCISE",
            "VOCAB_EXERCISE",
            "LEGACY_READING",
            "LEGACY_LISTENING");

    private static final Map<UUID, String> BY_ID = new HashMap<>();
    private static final Map<String, JsonNode> BY_TYPE = new HashMap<>();

    static {
        for (String type : TYPES) {
            try {
                String text = Files.readString(DIR.resolve(type + ".json"), StandardCharsets.UTF_8);
                JsonNode node = JSON.readTree(text);
                BY_ID.put(UUID.fromString(node.path("id").asString()), text);
                BY_TYPE.put(type, node);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private Fixtures() {}

    static String json(UUID itemSetId) {
        String json = BY_ID.get(itemSetId);
        if (json == null) {
            throw new com.toefly.practice.application.port.out.ItemSetUnavailableException(itemSetId);
        }
        return json;
    }

    static UUID id(String type) {
        return UUID.fromString(BY_TYPE.get(type).path("id").asString());
    }

    static String examFormat(String type) {
        return BY_TYPE.get(type).path("examFormat").asString();
    }

    /** The right answers in the request form, by ordinal. */
    static List<String> answers(String type) {
        List<String> answers = new ArrayList<>();
        BY_TYPE.get(type).path("_answers").forEach(answer -> answers.add(JSON.writeValueAsString(answer)));
        return answers;
    }

    static List<PoolItem> poolItems(String type) {
        UUID set = id(type);
        List<PoolItem> items = new ArrayList<>();
        int count = BY_TYPE.get(type).path("payload").path("items").size();
        for (int ordinal = 1; ordinal <= count; ordinal++) {
            items.add(new PoolItem(itemId(set, ordinal), ordinal, -0.5 + 0.1 * ordinal));
        }
        return items;
    }

    static UUID itemId(UUID set, int ordinal) {
        return UUID.nameUUIDFromBytes((set + "/" + ordinal).getBytes(StandardCharsets.UTF_8));
    }
}
