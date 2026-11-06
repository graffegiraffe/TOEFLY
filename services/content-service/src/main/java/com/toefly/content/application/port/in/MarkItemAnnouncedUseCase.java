package com.toefly.content.application.port.in;

import java.util.UUID;

/** Reaction to the service's own ItemCreated event; shows an idempotent consumer. */
public interface MarkItemAnnouncedUseCase {

    void markAnnounced(UUID itemId);
}
