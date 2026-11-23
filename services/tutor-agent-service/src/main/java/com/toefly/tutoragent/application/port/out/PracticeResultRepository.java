package com.toefly.tutoragent.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Results of attempts and evaluated work, for the scores of the weekly review. */
public interface PracticeResultRepository {

    /** A later result of the same source replaces the earlier one (Attempt 2 of a Writing task). */
    void save(Result result);

    List<Result> between(UUID userId, Instant from, Instant to);

    record Result(
            UUID sourceId,
            UUID userId,
            String taskType,
            @Nullable Double accuracy,
            @Nullable Double score,
            Instant occurredAt) {}
}
