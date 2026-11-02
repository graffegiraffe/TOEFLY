package com.toefly.common.events;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.web.client.ResourceAccessException;

/** Retry topics, DLQ and in-memory backoff shared by every TOEFLY consumer. */
@AutoConfiguration(afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@EnableConfigurationProperties(ToeflyEventsProperties.class)
public class ToeflyKafkaAutoConfiguration {

    static final List<Class<? extends Throwable>> NOT_RETRYABLE =
            List.of(DeserializationException.class, NonRetryableEventException.class, IllegalArgumentException.class);

    /** Two blocking retries, 200 ms then 800 ms, only for transient failures. */
    @Bean
    @ConditionalOnMissingBean
    DefaultErrorHandler toeflyBlockingErrorHandler() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(2);
        backOff.setInitialInterval(200);
        backOff.setMultiplier(4);
        backOff.setJitter(40);
        DefaultErrorHandler handler = new DefaultErrorHandler(backOff);
        handler.defaultFalse();
        handler.addRetryableExceptions(TransientDataAccessException.class, ResourceAccessException.class);
        return handler;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    RetryTopicConfiguration toeflyRetryTopicConfiguration(
            KafkaTemplate<Object, Object> template, ToeflyEventsProperties properties) {
        return RetryTopicConfigurationBuilder.newInstance()
                .fixedBackOff(properties.retry().delay().toMillis())
                .maxAttempts(properties.retry().attempts() + 1)
                .sameIntervalTopicReuseStrategy(SameIntervalTopicReuseStrategy.SINGLE_TOPIC)
                .retryTopicSuffix(TopicNames.RETRY_SUFFIX)
                .dltSuffix(TopicNames.DLQ_SUFFIX)
                .notRetryOn(NOT_RETRYABLE)
                .doNotAutoCreateRetryTopics()
                .create(template);
    }
}
