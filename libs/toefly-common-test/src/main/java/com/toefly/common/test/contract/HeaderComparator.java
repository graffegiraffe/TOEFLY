package com.toefly.common.test.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Header names compare case-insensitively; a header with a regex matcher is checked by the regex. */
final class HeaderComparator {

    private HeaderComparator() {}

    static List<String> differences(
            Map<String, String> expected, Map<String, String> regex, Map<String, List<String>> actual) {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(actual);
        List<String> differences = new ArrayList<>();
        Map<String, String> patterns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        patterns.putAll(regex);
        expected.forEach((name, value) -> {
            List<String> values = headers.get(name);
            if (values == null || values.isEmpty()) {
                differences.add("header " + name + ": missing, expected " + value);
                return;
            }
            String first = values.getFirst();
            boolean ok = patterns.containsKey(name)
                    ? Pattern.compile(patterns.get(name)).matcher(first).matches()
                    : matches(name, value, first);
            if (!ok) {
                differences.add("header " + name + ": expected " + value + ", got " + first);
            }
        });
        patterns.forEach((name, pattern) -> {
            if (!expected.containsKey(name)) {
                List<String> values = headers.get(name);
                if (values == null
                        || values.isEmpty()
                        || !Pattern.compile(pattern).matcher(values.getFirst()).matches()) {
                    differences.add("header " + name + ": expected to match " + pattern + ", got " + values);
                }
            }
        });
        return differences;
    }

    private static boolean matches(String name, String expected, String actual) {
        if (name.toLowerCase(Locale.ROOT).equals("content-type")) {
            // application/json matches application/json;charset=UTF-8.
            return actual.toLowerCase(Locale.ROOT).startsWith(expected.toLowerCase(Locale.ROOT));
        }
        return expected.equals(actual);
    }
}
