package com.toefly.tutoragent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Addresses of the owner services and the timeouts of docs/05 §9.1: tools have 500 ms to connect and 2 s to read,
 * background AI calls 90 s.
 */
@ConfigurationProperties("toefly.tutor-agent")
public record TutorAgentProperties(
        @DefaultValue("http://localhost:8095") String aiGatewayUrl,
        @DefaultValue("http://localhost:8084") String practiceUrl,
        @DefaultValue("http://localhost:8087") String planningUrl,
        @DefaultValue("http://localhost:8085") String assessmentUrl,
        @DefaultValue("http://localhost:8082") String contentUrl,
        @DefaultValue("http://localhost:8094") String analyticsUrl,
        @DefaultValue("PT2S") Duration toolReadTimeout,
        @DefaultValue("PT90S") Duration aiReadTimeout,
        @DefaultValue("true") boolean jobsEnabled) {}
