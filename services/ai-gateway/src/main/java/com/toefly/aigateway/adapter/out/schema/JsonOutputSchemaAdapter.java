package com.toefly.aigateway.adapter.out.schema;

import com.toefly.aigateway.application.port.out.OutputSchemaPort;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeType;

/**
 * Structured answers: finds the JSON object in a model answer and checks it against the subset of JSON Schema the
 * worker schemas use ({@code type}, {@code properties}, {@code required}, {@code additionalProperties},
 * {@code items}, {@code enum}, {@code const}, bounds, lengths, {@code pattern}, {@code anyOf}/{@code oneOf},
 * local {@code $ref}).
 */
public class JsonOutputSchemaAdapter implements OutputSchemaPort {

    static final int MAX_VIOLATIONS = 10;

    private final JsonMapper json = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Override
    public @Nullable Object parse(String answer) {
        String text = answer.strip();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int fence = text.lastIndexOf("```");
            text = firstLine >= 0 && fence > firstLine ? text.substring(firstLine + 1, fence) : text;
        }
        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        if (open < 0 || close < open) {
            return null;
        }
        try {
            JsonNode node = json.readTree(text.substring(open, close + 1));
            return node.isObject() ? json.convertValue(node, Object.class) : null;
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    @Override
    public List<String> validate(String schema, Object value) {
        JsonNode root = json.readTree(schema);
        List<String> violations = new ArrayList<>();
        check(root, root, json.valueToTree(value), "$", violations);
        return violations.size() > MAX_VIOLATIONS ? violations.subList(0, MAX_VIOLATIONS) : violations;
    }

    @Override
    public Object mapStrings(Object value, UnaryOperator<String> strings) {
        if (value instanceof String text) {
            return strings.apply(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v == null ? null : mapStrings(v, strings)));
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            list.forEach(v -> out.add(v == null ? null : mapStrings(v, strings)));
            return out;
        }
        return value;
    }

    @Override
    public String write(Object value) {
        return json.writeValueAsString(value);
    }

    private void check(JsonNode root, JsonNode schema, JsonNode value, String path, List<String> out) {
        if (out.size() > MAX_VIOLATIONS) {
            return;
        }
        if (schema.has("$ref")) {
            JsonNode target = resolve(root, schema.path("$ref").asString(""));
            if (target == null) {
                out.add(path + ": unresolved $ref " + schema.path("$ref").asString(""));
                return;
            }
            check(root, target, value, path, out);
            return;
        }
        if (schema.has("anyOf") || schema.has("oneOf")) {
            JsonNode options = schema.has("anyOf") ? schema.path("anyOf") : schema.path("oneOf");
            boolean any = false;
            for (JsonNode option : options) {
                List<String> trial = new ArrayList<>();
                check(root, option, value, path, trial);
                if (trial.isEmpty()) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                out.add(path + ": matches none of the allowed shapes");
            }
        }
        if (schema.has("type") && !typeMatches(schema.path("type"), value)) {
            out.add(path + ": expected " + schema.path("type") + ", got " + kind(value));
            return;
        }
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonNode option : schema.path("enum")) {
                found |= option.equals(value);
            }
            if (!found) {
                out.add(path + ": must be one of " + schema.path("enum"));
            }
        }
        if (schema.has("const") && !schema.path("const").equals(value)) {
            out.add(path + ": must be " + schema.path("const"));
        }
        if (value.isNumber()) {
            double number = value.asDouble();
            if (schema.has("minimum") && number < schema.path("minimum").asDouble()) {
                out.add(path + ": must be >= " + schema.path("minimum"));
            }
            if (schema.has("maximum") && number > schema.path("maximum").asDouble()) {
                out.add(path + ": must be <= " + schema.path("maximum"));
            }
        }
        if (value.isString()) {
            String text = value.asString();
            if (schema.has("minLength")
                    && text.length() < schema.path("minLength").asInt()) {
                out.add(path + ": shorter than " + schema.path("minLength"));
            }
            if (schema.has("maxLength")
                    && text.length() > schema.path("maxLength").asInt()) {
                out.add(path + ": longer than " + schema.path("maxLength"));
            }
            if (schema.has("pattern")
                    && !Pattern.compile(schema.path("pattern").asString())
                            .matcher(text)
                            .find()) {
                out.add(path + ": does not match " + schema.path("pattern").asString());
            }
        }
        if (value.isArray()) {
            if (schema.has("minItems") && value.size() < schema.path("minItems").asInt()) {
                out.add(path + ": fewer than " + schema.path("minItems") + " items");
            }
            if (schema.has("maxItems") && value.size() > schema.path("maxItems").asInt()) {
                out.add(path + ": more than " + schema.path("maxItems") + " items");
            }
            if (schema.has("items")) {
                for (int i = 0; i < value.size(); i++) {
                    check(root, schema.path("items"), value.get(i), path + "[" + i + "]", out);
                }
            }
        }
        if (value.isObject()) {
            for (JsonNode required : schema.path("required")) {
                if (!value.has(required.asString())) {
                    out.add(path + ": missing required property " + required.asString());
                }
            }
            JsonNode properties = schema.path("properties");
            Iterator<String> names = value.propertyNames().iterator();
            while (names.hasNext()) {
                String name = names.next();
                if (properties.has(name)) {
                    check(root, properties.path(name), value.path(name), path + "." + name, out);
                } else if (schema.path("additionalProperties").isBoolean()
                        && !schema.path("additionalProperties").asBoolean()) {
                    out.add(path + ": unexpected property " + name);
                } else if (schema.path("additionalProperties").isObject()) {
                    check(root, schema.path("additionalProperties"), value.path(name), path + "." + name, out);
                }
            }
        }
    }

    private static @Nullable JsonNode resolve(JsonNode root, String ref) {
        if (!ref.startsWith("#/")) {
            return null;
        }
        JsonNode node = root;
        for (String part : ref.substring(2).split("/")) {
            node = node.path(part.replace("~1", "/").replace("~0", "~"));
        }
        return node.isMissingNode() ? null : node;
    }

    private static boolean typeMatches(JsonNode type, JsonNode value) {
        if (type.isArray()) {
            for (JsonNode option : type) {
                if (typeMatches(option, value)) {
                    return true;
                }
            }
            return false;
        }
        return switch (type.asString("")) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isString();
            case "integer" ->
                value.isIntegralNumber() || (value.isNumber() && value.asDouble() == Math.rint(value.asDouble()));
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> true;
        };
    }

    private static String kind(JsonNode value) {
        JsonNodeType type = value.getNodeType();
        return type.name().toLowerCase(java.util.Locale.ROOT);
    }
}
