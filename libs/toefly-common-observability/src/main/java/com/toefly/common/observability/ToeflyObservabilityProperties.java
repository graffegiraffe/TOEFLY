package com.toefly.common.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** @param userIdSalt salt of {@link UserIdHasher}; a secret outside {@code local} */
@ConfigurationProperties("toefly.observability")
public record ToeflyObservabilityProperties(
        @DefaultValue("local-only-salt") String userIdSalt) {}
