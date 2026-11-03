package com.toefly.common.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Kafka listener with TOEFLY defaults: consumer group from {@code spring.application.name}, non-blocking retries in
 * {@code <topic>.retry} and dead letters in {@code <topic>.dlq} configured by {@link ToeflyKafkaAutoConfiguration}. The
 * listener class must deduplicate through {@code ProcessedEventGuard}.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@KafkaListener
public @interface ToeflyListener {

    @AliasFor(annotation = KafkaListener.class, attribute = "topics")
    String[] topics();

    @AliasFor(annotation = KafkaListener.class, attribute = "id")
    String id() default "";

    @AliasFor(annotation = KafkaListener.class, attribute = "groupId")
    String groupId() default "${spring.application.name}";
}
