package com.toefly.common.observability;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Lowest-precedence defaults of every service: Kafka observations (trace context in record headers, docs/09 §9.3),
 * virtual threads and full sampling; the collector applies tail sampling.
 */
public class ToeflyObservabilityDefaults implements EnvironmentPostProcessor {

    static final Map<String, Object> DEFAULTS = Map.of(
            "spring.kafka.template.observation-enabled", "true",
            "spring.kafka.listener.observation-enabled", "true",
            "management.tracing.sampling.probability", "1.0",
            "management.tracing.propagation.type", "w3c",
            "spring.threads.virtual.enabled", "true",
            "management.endpoints.web.exposure.include", "health,info,prometheus,loggers");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource("toeflyObservabilityDefaults", DEFAULTS));
    }
}
