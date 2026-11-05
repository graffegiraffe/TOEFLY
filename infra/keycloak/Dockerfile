# syntax=docker/dockerfile:1
# TOEFLY Keycloak image: the toefly theme, the providers of toefly-keycloak-extensions and the common-password
# blocklist of the password policy.
FROM maven:3.9-eclipse-temurin-21 AS extensions
WORKDIR /build
COPY extensions/pom.xml .
COPY extensions/src src
# Tests run in CI (ci-libs); the image only packages the providers.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM quay.io/keycloak/keycloak:26.8.1
ENV KC_HEALTH_ENABLED=true KC_METRICS_ENABLED=true
COPY --from=extensions /build/target/toefly-keycloak-extensions-*.jar /opt/keycloak/providers/
COPY theme/toefly /opt/keycloak/themes/toefly
COPY password-blacklists /opt/keycloak/data/password-blacklists
