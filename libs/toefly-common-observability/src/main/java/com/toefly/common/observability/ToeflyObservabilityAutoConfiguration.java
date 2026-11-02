package com.toefly.common.observability;

import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Trace context helpers shared by the outbox and the logs. */
@AutoConfiguration(
        afterName = "org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration")
@EnableConfigurationProperties(ToeflyObservabilityProperties.class)
public class ToeflyObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Tracer.class)
    TraceparentProvider traceparentProvider(Tracer tracer) {
        return new TraceparentProvider(tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    UserIdHasher userIdHasher(ToeflyObservabilityProperties properties) {
        return new UserIdHasher(properties.userIdSalt());
    }
}
