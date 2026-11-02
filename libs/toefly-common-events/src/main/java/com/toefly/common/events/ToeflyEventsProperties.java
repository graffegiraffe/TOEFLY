package com.toefly.common.events;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Event infrastructure settings.
 *
 * @param registryUrl Apicurio Registry API base, for example {@code http://localhost:8380/apis/registry/v3}
 * @param autoRegister register schemas on first use; only in {@code local}
 * @param retry non-blocking retry policy of the consumer class (docs/04-architecture.md §4.5)
 */
@ConfigurationProperties("toefly.events")
public record ToeflyEventsProperties(
        @DefaultValue("http://localhost:8380/apis/registry/v3")
        String registryUrl,

        @DefaultValue("false") boolean autoRegister,
        @DefaultValue Retry retry) {

    /**
     * @param delay fixed delay between attempts in {@code .retry}
     * @param attempts attempts in {@code .retry} before {@code .dlq}
     */
    public record Retry(
            @DefaultValue("30s") Duration delay,
            @DefaultValue("3") int attempts) {}
}
