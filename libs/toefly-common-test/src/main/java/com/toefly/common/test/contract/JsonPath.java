package com.toefly.common.test.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The small JSONPath subset of contracts: {@code $.a.b}, {@code $.items[0].id}, {@code $.roles}. */
final class JsonPath {

    private static final Pattern SEGMENT = Pattern.compile("([^.\\[\\]]+)|\\[(\\d+)]");

    private JsonPath() {}

    /** Path segments: names as strings, array indexes as integers. */
    static List<Object> parse(String path) {
        if (!path.startsWith("$")) {
            throw new IllegalArgumentException("A path starts with $: " + path);
        }
        List<Object> segments = new ArrayList<>();
        Matcher matcher = SEGMENT.matcher(path.substring(1));
        while (matcher.find()) {
            segments.add(matcher.group(1) != null ? matcher.group(1) : Integer.parseInt(matcher.group(2)));
        }
        return segments;
    }

    /** Value at the path; empty when a segment is missing. A present JSON null is {@code Optional.of(NULL)}. */
    static Optional<Object> read(Object root, String path) {
        Object current = root;
        for (Object segment : parse(path)) {
            if (segment instanceof String name && current instanceof Map<?, ?> map && map.containsKey(name)) {
                current = map.get(name);
            } else if (segment instanceof Integer index && current instanceof List<?> list && index < list.size()) {
                current = list.get(index);
            } else {
                return Optional.empty();
            }
            if (current == null) {
                current = NULL;
            }
        }
        return Optional.of(current);
    }

    static String child(String path, String name) {
        return path + "." + name;
    }

    static String index(String path, int index) {
        return path + "[" + index + "]";
    }

    /** Marker of a JSON null found at a path. */
    static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };
}
