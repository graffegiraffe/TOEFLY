package com.toefly.common.outbox;

/** Publishes integration events; the only allowed way to send events to Kafka (docs/04-architecture.md §4.4). */
public interface IntegrationEventPublisher {

    /** Writes the event in the caller's transaction; fails when there is no transaction. */
    void publish(IntegrationEvent event);
}
