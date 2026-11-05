package com.toefly.common.test.contract;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One contract file: a REST exchange or a published message (docs/11-quality-testing.md §8.2, ADR-0031). The format
 * is a subset of the Spring Cloud Contract YAML.
 */
public record Contract(
        String name,
        String description,
        @Nullable Request request,
        @Nullable Response response,
        @Nullable String triggeredBy,
        @Nullable OutputMessage outputMessage,
        Path file) {

    public boolean isRest() {
        return request != null && response != null;
    }

    public boolean isMessage() {
        return triggeredBy != null && outputMessage != null;
    }

    /** Method name of {@code input.triggeredBy} without the parentheses. */
    public String triggerMethod() {
        String trigger = triggeredBy == null ? "" : triggeredBy.strip();
        return trigger.endsWith("()") ? trigger.substring(0, trigger.length() - 2) : trigger;
    }

    public record Request(
            String method,
            String url,
            Map<String, String> headers,
            @Nullable Object body,
            Map<String, String> headerRegex,
            List<BodyMatcher> bodyMatchers) {}

    public record Response(
            int status,
            Map<String, String> headers,
            @Nullable Object body,
            List<BodyMatcher> bodyMatchers) {}

    public record OutputMessage(
            String sentTo,
            Map<String, String> headers,
            @Nullable Object body,
            List<BodyMatcher> bodyMatchers) {}

    /** A matcher of {@code matchers.body}: {@code by_regex}, {@code by_type} or {@code by_equality}. */
    public record BodyMatcher(
            String path,
            String type,
            @Nullable String regex,
            @Nullable String predefined,
            @Nullable Integer minOccurrence,
            @Nullable Integer maxOccurrence) {}
}
