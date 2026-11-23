package com.toefly.tutoragent.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Debounce window of learner-model-updated: one event per learner in 5 minutes (docs/06 §6.2). */
public interface PublicationSchedule {

    /** Opens a window ending at {@code dueAt} unless one is open already. */
    void open(UUID userId, Instant dueAt);

    List<UUID> due(Instant now, int limit);

    void close(UUID userId);
}
