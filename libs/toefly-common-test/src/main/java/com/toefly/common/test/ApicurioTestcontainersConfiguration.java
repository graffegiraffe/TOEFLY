package com.toefly.common.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** In-memory Apicurio Registry 3 with schema auto-registration, as in {@code local}. */
@TestConfiguration(proxyBeanMethods = false)
public class ApicurioTestcontainersConfiguration {

    @Bean
    GenericContainer<?> apicurioContainer() {
        return new GenericContainer<>(ToeflyTestImages.APICURIO)
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/apis/registry/v3/system/info").forPort(8080));
    }

    @Bean
    DynamicPropertyRegistrar apicurioProperties(GenericContainer<?> apicurioContainer) {
        return registry -> {
            registry.add(
                    "toefly.events.registry-url",
                    () -> "http://" + apicurioContainer.getHost() + ":" + apicurioContainer.getMappedPort(8080)
                            + "/apis/registry/v3");
            registry.add("toefly.events.auto-register", () -> "true");
        };
    }
}
