package com.toefly.common.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PostgreSQL 17 with pgvector and logical replication, wired as the service DataSource. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return newPostgres();
    }

    public static PostgreSQLContainer newPostgres() {
        return new PostgreSQLContainer(ToeflyTestImages.POSTGRES)
                .withDatabaseName("service_db")
                .withUsername("service_app")
                .withPassword("service_app")
                .withCommand("postgres", "-c", "wal_level=logical", "-c", "max_replication_slots=4");
    }
}
