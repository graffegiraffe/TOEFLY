package com.toefly.content.adapter.in.web;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Removes everything a learner must not see before answering: keys, explanations, rationales, transcripts and the
 * hidden part of Complete the Words gaps. Practice-service reads the full payload from the internal endpoint.
 */
final class PayloadRedactor {

    private static final Set<String> SECRET_FIELDS = Set.of(
            "answerKey", "acceptedAnswers", "explanation", "rationale", "transcript", "modelAnswer",
            "modelAnswers", "sampleAnswer", "fullWord", "suffix", "answerOrder", "keyExplanation");
    private static final Pattern GAP = Pattern.compile("(\\w*)\\{[^}]*}");

    private PayloadRedactor() {}

    static JsonNode redact(JsonNode payload) {
        JsonNode copy = payload.deepCopy();
        strip(copy);
        return copy;
    }

    private static void strip(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (Iterator<Map.Entry<String, JsonNode>> it = object.properties().iterator(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                if (SECRET_FIELDS.contains(field.getKey())) {
                    it.remove();
                } else if ("markup".equals(field.getKey()) && field.getValue().isString()) {
                    object.put("markup", GAP.matcher(field.getValue().asString()).replaceAll("$1{_}"));
                } else {
                    strip(field.getValue());
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(PayloadRedactor::strip);
        }
    }
}
