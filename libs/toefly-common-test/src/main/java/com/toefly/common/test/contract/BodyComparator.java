package com.toefly.common.test.contract;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Compares the body of a contract with an actual JSON body (ADR-0031): every value of the contract must be present
 * and equal, extra fields are allowed, and paths with a matcher are checked by the matcher instead.
 */
final class BodyComparator {

    private static final Map<String, Pattern> PREDEFINED = Map.of(
            "uuid", Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
            "iso_date_time",
                    Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?"),
            "iso_date", Pattern.compile("\\d{4}-\\d{2}-\\d{2}"),
            "number", Pattern.compile("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?"),
            "non_empty", Pattern.compile("(?s).+"));

    private BodyComparator() {}

    /** Every difference with its path; empty when the actual body fulfils the contract. */
    static List<String> differences(
            @Nullable Object expected, @Nullable Object actual, List<Contract.BodyMatcher> matchers) {
        List<String> differences = new ArrayList<>();
        Set<String> matched = new HashSet<>();
        for (Contract.BodyMatcher matcher : matchers) {
            if (!matcher.type().equals("by_equality")) {
                // by_equality keeps the comparison with the contract value; other matchers replace it.
                matched.add(matcher.path());
            }
            check(matcher, actual, differences);
        }
        if (expected != null) {
            compare("$", expected, actual, matched, differences);
        }
        return differences;
    }

    private static void check(Contract.BodyMatcher matcher, @Nullable Object actual, List<String> differences) {
        Optional<Object> found = actual == null ? Optional.empty() : JsonPath.read(actual, matcher.path());
        if (found.isEmpty() || found.get() == JsonPath.NULL) {
            differences.add(matcher.path() + ": missing, expected " + describe(matcher));
            return;
        }
        Object value = found.get();
        switch (matcher.type()) {
            case "by_regex" -> {
                Pattern pattern = matcher.regex() != null
                        ? Pattern.compile(matcher.regex())
                        : PREDEFINED.get(String.valueOf(matcher.predefined()));
                if (pattern == null) {
                    differences.add(matcher.path() + ": unknown predefined matcher " + matcher.predefined());
                } else if (value instanceof Map
                        || value instanceof List
                        || !pattern.matcher(String.valueOf(value)).matches()) {
                    differences.add(matcher.path() + ": " + value + " does not match " + pattern.pattern());
                }
            }
            case "by_type" -> {
                if (value instanceof List<?> list) {
                    if (matcher.minOccurrence() != null && list.size() < matcher.minOccurrence()) {
                        differences.add(matcher.path() + ": " + list.size() + " items, at least "
                                + matcher.minOccurrence() + " expected");
                    }
                    if (matcher.maxOccurrence() != null && list.size() > matcher.maxOccurrence()) {
                        differences.add(matcher.path() + ": " + list.size() + " items, at most "
                                + matcher.maxOccurrence() + " expected");
                    }
                }
            }
            default -> {
                // by_equality: the value of the contract body is compared below.
            }
        }
    }

    private static void compare(
            String path, Object expected, @Nullable Object actual, Set<String> matched, List<String> differences) {
        if (matched.contains(path)) {
            return;
        }
        if (actual == null || actual == JsonPath.NULL) {
            differences.add(path + ": missing, expected " + expected);
            return;
        }
        if (expected instanceof Map<?, ?> expectedMap) {
            if (!(actual instanceof Map<?, ?> actualMap)) {
                differences.add(path + ": expected an object, got " + actual);
                return;
            }
            expectedMap.forEach((key, value) -> {
                String child = JsonPath.child(path, String.valueOf(key));
                if (value == null) {
                    if (actualMap.containsKey(key) && actualMap.get(key) != null) {
                        differences.add(child + ": expected null, got " + actualMap.get(key));
                    }
                } else if (!actualMap.containsKey(key)) {
                    if (!matched.contains(child)) {
                        differences.add(child + ": missing, expected " + value);
                    }
                } else {
                    compare(child, value, actualMap.get(key), matched, differences);
                }
            });
        } else if (expected instanceof List<?> expectedList) {
            if (!(actual instanceof List<?> actualList)) {
                differences.add(path + ": expected an array, got " + actual);
                return;
            }
            if (expectedList.size() != actualList.size()) {
                differences.add(path + ": expected " + expectedList.size() + " items, got " + actualList.size());
                return;
            }
            for (int i = 0; i < expectedList.size(); i++) {
                compare(JsonPath.index(path, i), expectedList.get(i), actualList.get(i), matched, differences);
            }
        } else if (!scalarEquals(expected, actual)) {
            differences.add(path + ": expected " + expected + ", got " + actual);
        }
    }

    private static boolean scalarEquals(Object expected, Object actual) {
        if (expected instanceof Number left && actual instanceof Number right) {
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0;
        }
        if (expected instanceof Boolean || actual instanceof Boolean) {
            return expected.equals(actual);
        }
        if (expected instanceof Number || actual instanceof Number) {
            return false;
        }
        return String.valueOf(expected).equals(String.valueOf(actual));
    }

    private static String describe(Contract.BodyMatcher matcher) {
        return matcher.type()
                + (matcher.predefined() != null ? " " + matcher.predefined() : "")
                + (matcher.regex() != null ? " " + matcher.regex() : "");
    }
}
