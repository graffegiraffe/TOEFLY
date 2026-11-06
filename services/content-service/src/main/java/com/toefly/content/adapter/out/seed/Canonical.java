package com.toefly.content.adapter.out.seed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Canonical JSON (sorted keys, no whitespace) for stable content hashes (docs/08-content-pipeline.md §4.6). */
final class Canonical {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Canonical() {}

    static String of(JsonNode node) {
        StringBuilder out = new StringBuilder();
        write(node, out);
        return out.toString();
    }

    private static void write(JsonNode node, StringBuilder out) {
        if (node.isObject()) {
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>(node.properties());
            fields.sort(Comparator.comparing(Map.Entry::getKey));
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonNode> f : fields) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(JSON.writeValueAsString(f.getKey())).append(':');
                write(f.getValue(), out);
            }
            out.append('}');
        } else if (node.isArray()) {
            out.append('[');
            boolean first = true;
            for (JsonNode e : node) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(e, out);
            }
            out.append(']');
        } else {
            out.append(JSON.writeValueAsString(node));
        }
    }

    /** Appends every string value of the tree, space separated: the text for full-text search. */
    static void collectStrings(JsonNode node, StringBuilder out) {
        if (node.isString()) {
            out.append(node.asString()).append(' ');
        } else if (node.isObject() || node.isArray()) {
            node.forEach(child -> collectStrings(child, out));
        }
    }
}
