package com.toefly.aigateway.application.port.out;

import com.toefly.aigateway.domain.call.AiCall;
import com.toefly.aigateway.domain.call.CallCursor;
import com.toefly.aigateway.domain.call.CallFilter;
import com.toefly.aigateway.domain.call.UsageRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Journal {@code ai_call} and the daily aggregates {@code ai_usage_daily} (§2.7, §2.8). */
public interface AiCallRepository {

    void save(AiCall call);

    List<AiCall> page(CallFilter filter, @Nullable CallCursor after, int limit);

    List<UsageRow> usage(@Nullable UUID userId, LocalDate from, LocalDate to);

    int deleteUser(UUID userId);

    /** Clears masked bodies older than {@code before}; returns rows changed. */
    int clearPayloads(Instant before);

    /** Deletes rows older than {@code before}. */
    int deleteOlderThan(Instant before);
}
