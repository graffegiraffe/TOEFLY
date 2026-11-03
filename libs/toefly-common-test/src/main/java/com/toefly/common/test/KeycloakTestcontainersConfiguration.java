package com.toefly.common.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Keycloak in dev mode that imports a realm file from the test classpath ({@code keycloak/toefly-realm.json}) and
 * points the resource server at it. Most tests use {@link JwtFixtures} instead.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KeycloakTestcontainersConfiguration {

    @Bean
    GenericContainer<?> keycloakContainer() {
        return new GenericContainer<>(ToeflyTestImages.KEYCLOAK)
                .withCommand("start-dev", "--import-realm")
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("keycloak/toefly-realm.json"),
                        "/opt/keycloak/data/import/toefly-realm.json")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/realms/toefly").forPort(8080));
    }

    @Bean
    DynamicPropertyRegistrar keycloakProperties(GenericContainer<?> keycloakContainer) {
        return registry -> registry.add(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://" + keycloakContainer.getHost() + ":" + keycloakContainer.getMappedPort(8080)
                        + "/realms/toefly");
    }
}
