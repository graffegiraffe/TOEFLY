package com.toefly.aigateway.domain.prompt;

import org.jspecify.annotations.Nullable;

/** Generation settings a version pins; unset values come from the route (§2.4). */
public record PromptParams(
        @Nullable Double temperature,
        @Nullable Integer maxOutputTokens,
        @Nullable String reasoningEffort) {

    public static final PromptParams NONE = new PromptParams(null, null, null);
}
