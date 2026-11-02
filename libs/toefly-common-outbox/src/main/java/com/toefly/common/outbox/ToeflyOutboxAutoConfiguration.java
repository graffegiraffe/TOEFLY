package com.toefly.common.outbox;

import com.toefly.common.events.ApicurioEventPayloadSerializer;
import com.toefly.common.events.EventPayloadSerializer;
import com.toefly.common.events.ToeflyEventsProperties;
import com.toefly.common.observability.TraceparentProvider;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Outbox publisher, idempotent consumer guard and cleanup for services with a database. */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
            "com.toefly.common.observability.ToeflyObservabilityAutoConfiguration"
        })
@ConditionalOnBean(JdbcClient.class)
@EnableConfigurationProperties(ToeflyEventsProperties.class)
@EnableScheduling
public class ToeflyOutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    EventPayloadSerializer eventPayloadSerializer(ToeflyEventsProperties properties) {
        return new ApicurioEventPayloadSerializer(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxWriter outboxWriter(JdbcClient jdbc) {
        return new JdbcOutboxWriter(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    IntegrationEventPublisher integrationEventPublisher(
            OutboxWriter writer,
            EventPayloadSerializer serializer,
            ObjectProvider<TraceparentProvider> traceparent,
            @Value("${spring.application.name}") String serviceName) {
        return new OutboxIntegrationEventPublisher(writer, serializer, traceparent.getIfAvailable(), serviceName);
    }

    @Bean
    @ConditionalOnMissingBean
    ProcessedEventGuard processedEventGuard(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        return new ProcessedEventGuard(jdbc, new TransactionTemplate(transactionManager));
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxCleanupJob outboxCleanupJob(JdbcClient jdbc, Clock clock) {
        return new OutboxCleanupJob(jdbc, clock);
    }
}
