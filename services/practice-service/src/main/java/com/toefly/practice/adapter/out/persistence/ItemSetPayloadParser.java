package com.toefly.practice.adapter.out.persistence;

import com.toefly.practice.application.port.in.ItemSetContent;
import com.toefly.practice.domain.attempt.ExamFormat;
import com.toefly.practice.domain.attempt.Section;
import com.toefly.practice.domain.attempt.TaskType;
import com.toefly.practice.domain.scoring.ItemExplanation;
import com.toefly.practice.domain.scoring.ItemKey;
import com.toefly.practice.domain.scoring.ItemSetKeys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns the internal item set of content-service into keys, explanations and the content a learner may see
 * (payload schemas: content/schemas/payload, docs/03-domain-model.md §4).
 */
final class ItemSetPayloadParser {

    /** Fields a learner must not see before answering. */
    private static final Set<String> SECRET = Set.of(
            "answerKey",
            "answerKeys",
            "acceptedAnswers",
            "acceptedKeys",
            "explanation",
            "rationale",
            "transcript",
            "fullWord",
            "suffix",
            "modelAnswer",
            "modelAnswers",
            "distractorChunkId",
            "errorKey",
            "correction",
            "keyExplanation");

    private static final String PLACEHOLDER = "explanation not provided";

    private ItemSetPayloadParser() {}

    static ItemSetContent parse(String json) {
        JsonNode set = EventJson.MAPPER.readTree(json);
        TaskType taskType = TaskType.valueOf(set.path("taskType").asString());
        JsonNode payload = set.path("payload");
        JsonNode stimulus = payload.path("stimulus");
        Map<Integer, Map<String, Object>> items = new LinkedHashMap<>();
        Map<Integer, ItemKey> keys = new HashMap<>();
        Map<Integer, ItemExplanation> explanations = new HashMap<>();
        Map<Integer, Double> maxScores = new HashMap<>();
        for (JsonNode item : payload.path("items")) {
            int ordinal = item.path("ordinal").asInt();
            items.put(ordinal, publicItem(taskType, item));
            ItemKey key = key(taskType, ordinal, item);
            if (key != null) {
                keys.put(ordinal, key);
                maxScores.put(ordinal, key.maxScore());
            }
            explanations.put(ordinal, explanation(item));
        }
        return new ItemSetContent(
                UUID.fromString(set.path("id").asString()),
                set.path("version").asInt(1),
                taskType,
                set.hasNonNull("section") ? Section.valueOf(set.path("section").asString()) : null,
                ExamFormat.valueOf(set.path("examFormat").asString("TOEFL_2026")),
                set.path("status").asString(""),
                estimatedSeconds(set),
                stimulus.isObject() ? toMap(redact(stimulus)) : null,
                stimulus.isObject() ? toMap(stimulus) : null,
                items,
                maxScores,
                new ItemSetKeys(keys, explanations));
    }

    private static int estimatedSeconds(JsonNode set) {
        if (set.hasNonNull("etsNormSeconds")) {
            return set.path("etsNormSeconds").asInt();
        }
        return (int) Math.round(set.path("estimatedMinutes").asDouble(0) * 60);
    }

    private static Map<String, Object> publicItem(TaskType taskType, JsonNode item) {
        ObjectNode copy = (ObjectNode) redact(item);
        if (taskType == TaskType.BUILD_A_SENTENCE) {
            copy.remove("sentence"); // the assembled sentence is the answer
        }
        if (taskType == TaskType.COMPLETE_THE_WORDS) {
            // the exam shows one cell per missing letter (AC-07.01.1); the letters stay secret
            copy.put("missingLetters", item.path("suffix").asString().length());
        }
        return toMap(copy);
    }

    static @Nullable ItemKey key(TaskType taskType, int ordinal, JsonNode item) {
        String exercise = item.path("exerciseType").asString("");
        if (taskType == TaskType.COMPLETE_THE_WORDS) {
            List<String> accepted = new ArrayList<>();
            accepted.add(item.path("suffix").asString());
            item.path("acceptedAnswers").forEach(value -> accepted.add(value.asString()));
            return new ItemKey.Text(ordinal, accepted, true);
        }
        if (taskType == TaskType.BUILD_A_SENTENCE) {
            List<List<String>> accepted = new ArrayList<>();
            accepted.add(strings(item.path("answerKey")));
            item.path("acceptedKeys").forEach(sequence -> accepted.add(strings(sequence)));
            int slots = 0;
            for (JsonNode part : item.path("template")) {
                slots += "SLOT".equals(part.path("kind").asString()) ? 1 : 0;
            }
            String distractor = item.hasNonNull("distractorChunkId")
                    ? item.path("distractorChunkId").asString()
                    : null;
            return new ItemKey.Sequence(ordinal, accepted, distractor, slots);
        }
        if ("PROSE_SUMMARY".equals(item.path("subskill").asString()) || item.has("answerKeys")) {
            return new ItemKey.ProseSummary(ordinal, Set.copyOf(strings(item.path("answerKeys"))));
        }
        switch (exercise) {
            case "GAP_FILL", "WORD_FORMATION" -> {
                return new ItemKey.Text(ordinal, textAnswers(item), false);
            }
            case "TRANSFORMATION" -> {
                return new ItemKey.Transformation(ordinal, textAnswers(item));
            }
            case "ERROR_CORRECTION" -> {
                return new ItemKey.ErrorCorrection(
                        ordinal,
                        item.path("errorKey").asString(),
                        List.of(item.path("correction").asString()));
            }
            default -> {
                // multiple choice of every other closed type
            }
        }
        if (item.path("answerKey").isString()) {
            return new ItemKey.SingleChoice(ordinal, item.path("answerKey").asString());
        }
        return null;
    }

    private static List<String> textAnswers(JsonNode item) {
        List<String> accepted = new ArrayList<>();
        accepted.add(item.path("answerKey").asString());
        item.path("acceptedAnswers").forEach(value -> accepted.add(value.asString()));
        return accepted;
    }

    static ItemExplanation explanation(JsonNode item) {
        Map<String, String> rationales = new LinkedHashMap<>();
        for (JsonNode option : item.path("options")) {
            String text = meaningful(option.path("rationale").asString(""));
            if (text != null && option.path("key").isString()) {
                rationales.put(option.path("key").asString(), text);
            }
        }
        return new ItemExplanation(
                meaningful(item.path("explanation").path("summary").asString("")), rationales);
    }

    /** The seed marks missing explanations with a placeholder text; it counts as missing. */
    private static @Nullable String meaningful(String text) {
        String value = text.strip();
        return value.isEmpty() || value.toLowerCase(java.util.Locale.ROOT).startsWith(PLACEHOLDER) ? null : value;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }

    static JsonNode redact(JsonNode node) {
        JsonNode copy = node.deepCopy();
        strip(copy);
        return copy;
    }

    private static void strip(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Set<String> names = new LinkedHashSet<>(object.propertyNames());
            for (String name : names) {
                if (SECRET.contains(name)) {
                    object.remove(name);
                } else if ("markup".equals(name) && object.path(name).isString()) {
                    object.put(name, object.path(name).asString().replaceAll("(\\w*)\\{[^}]*}", "$1{_}"));
                } else {
                    strip(object.path(name));
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(ItemSetPayloadParser::strip);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonNode node) {
        return EventJson.MAPPER.convertValue(node, Map.class);
    }
}
