package com.toefly.common.events;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.web.client.ResourceAccessException;

/**
 * Retry topics, DLQ and in-memory backoff shared by every TOEFLY consumer. Values are deserialized just before the
 * listener ({@link ValueDeserializingInterceptor}), so a failed record goes to {@code <topic>.retry} or
 * {@code <topic>.dlq} with its original bytes. A record the deserializer cannot read becomes a
 * {@link DeserializationException} and goes straight to {@code <topic>.dlq}, instead of failing on the same offset
 * forever.
 */
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

    /** Deserializes record values in the listener, so retries and dead letters keep the original bytes. */
    @Bean
    @ConditionalOnMissingBean(RecordInterceptor.class)
    ValueDeserializingInterceptor toeflyValueDeserializingInterceptor() {
        return new ValueDeserializingInterceptor();
    }

    /**
     * The consumer reads values as {@code byte[]} and hands the configured value deserializer to
     * {@link ValueDeserializingInterceptor}; the key deserializer is wrapped in {@link ErrorHandlingDeserializer}, so a
     * failure reaches the error handler with the record. Without the interceptor (a service has its own) the value
     * deserializer is wrapped like the key one.
     */
    @Bean
    DefaultKafkaConsumerFactoryCustomizer toeflyErrorHandlingDeserializer(
            ObjectProvider<ValueDeserializingInterceptor> interceptor) {
        return factory -> {
            if (factory.getKeyDeserializer() != null || factory.getValueDeserializer() != null) {
                return; // deserializer instances are the service's own choice
            }
            Map<String, Object> configs = factory.getConfigurationProperties();
            Map<String, Object> updates = new HashMap<>();
            wrap(
                    configs,
                    updates,
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                    ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS);
            ValueDeserializingInterceptor values = interceptor.getIfAvailable();
            Object value = configs.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
            if (values != null && value != null && !isErrorHandling(value) && !isByteArray(value)) {
                values.adopt(configs);
                updates.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
            } else {
                wrap(
                        configs,
                        updates,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                        ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS);
            }
            factory.updateConfigs(updates);
        };
    }

    private static void wrap(Map<String, Object> configs, Map<String, Object> updates, String key, String delegateKey) {
        Object configured = configs.get(key);
        if (configured == null || isErrorHandling(configured)) {
            return;
        }
        updates.put(key, ErrorHandlingDeserializer.class);
        updates.put(delegateKey, configured);
    }

    private static boolean isErrorHandling(Object configured) {
        return className(configured).equals(ErrorHandlingDeserializer.class.getName());
    }

    private static boolean isByteArray(Object configured) {
        return className(configured).equals(ByteArrayDeserializer.class.getName());
    }

    private static String className(Object configured) {
        return configured instanceof Class<?> type ? type.getName() : configured.toString();
    }

    /**
     * The template of retry topics and the DLQ writes records as they were polled, that is the original bytes; the
     * default {@code StringSerializer} would fail on them and leave the record where it is.
     */
    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    DefaultKafkaProducerFactoryCustomizer toeflyDeadLetterSerializer() {
        return factory -> {
            Object configured = factory.getConfigurationProperties().get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);
            boolean defaultString = configured == null
                    || configured == StringSerializer.class
                    || StringSerializer.class.getName().equals(configured);
            if (factory.getValueSerializer() != null || !defaultString) {
                return;
            }
            ((DefaultKafkaProducerFactory) factory)
                    .setValueSerializer(new DelegatingByTypeSerializer(
                            Map.of(byte[].class, new ByteArraySerializer(), String.class, new StringSerializer())));
        };
    }
}
