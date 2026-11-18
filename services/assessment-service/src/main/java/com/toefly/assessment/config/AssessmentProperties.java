package com.toefly.assessment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings {@code toefly.assessment.*}. */
@ConfigurationProperties("toefly.assessment")
public record AssessmentProperties(
        @DefaultValue("http://localhost:8082") String contentUrl,
        @DefaultValue("http://localhost:8095") String aiGatewayUrl,
        @DefaultValue("PT90S") Duration aiReadTimeout,
        @DefaultValue Deferred deferred) {

    public record Deferred(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("PT1M") Duration interval) {}
}
