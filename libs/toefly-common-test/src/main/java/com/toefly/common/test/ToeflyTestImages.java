package com.toefly.common.test;

import org.testcontainers.utility.DockerImageName;

/** Container images of component tests; the same families as {@code infra/compose/compose.yaml}. */
public final class ToeflyTestImages {

    public static final DockerImageName POSTGRES =
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg17-trixie").asCompatibleSubstituteFor("postgres");
    public static final DockerImageName KAFKA = DockerImageName.parse("apache/kafka:4.1.2");
    public static final DockerImageName APICURIO = DockerImageName.parse("quay.io/apicurio/apicurio-registry:3.3.3");
    public static final DockerImageName KEYCLOAK = DockerImageName.parse("quay.io/keycloak/keycloak:26.8.1");
    public static final DockerImageName DEBEZIUM_CONNECT =
            DockerImageName.parse("quay.io/debezium/connect:3.7.0.Final");
    public static final DockerImageName SEAWEEDFS = DockerImageName.parse("chrislusf/seaweedfs:4.52");

    private ToeflyTestImages() {}
}
