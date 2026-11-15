package com.toefly.aigateway.domain.routing;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Generation settings of a route (§2.4). {@code reasoningEffort} goes only to gpt-oss models; {@code timeout} is the
 * whole answer of one candidate.
 */
public record RouteParams(
        double temperature,
        int maxOutputTokens,
        @Nullable String reasoningEffort,
        boolean cacheable,
        Duration timeout) {

    public RouteParams {
        if (maxOutputTokens < 0) {
            throw new IllegalArgumentException("maxOutputTokens < 0");
        }
    }

    public RouteParams with(@Nullable Double temperature, @Nullable Integer maxOutputTokens, @Nullable String effort) {
        return new RouteParams(
                temperature == null ? this.temperature : temperature,
                maxOutputTokens == null ? this.maxOutputTokens : maxOutputTokens,
                effort == null ? reasoningEffort : effort,
                cacheable,
                timeout);
    }
}
