package com.toefly.aigateway.domain.safety;

import org.jspecify.annotations.Nullable;

/**
 * Personal data at {@code [start, end)} of a text. Spans with the same {@code group} (all case forms of one name)
 * share one placeholder.
 */
public record PiiSpan(
        int start,
        int end,
        PiiType type,
        PiiStage stage,
        @Nullable String group) {

    public PiiSpan {
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("empty span " + start + ".." + end);
        }
    }

    public int length() {
        return end - start;
    }

    public boolean overlaps(PiiSpan other) {
        return start < other.end && other.start < end;
    }
}
