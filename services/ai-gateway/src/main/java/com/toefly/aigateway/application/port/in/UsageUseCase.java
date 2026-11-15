package com.toefly.aigateway.application.port.in;

import com.toefly.aigateway.domain.call.AiCall;
import com.toefly.aigateway.domain.call.CallCursor;
import com.toefly.aigateway.domain.call.CallFilter;
import com.toefly.aigateway.domain.call.UsageRow;
import com.toefly.aigateway.domain.routing.AiProvider;
import com.toefly.aigateway.domain.routing.AiUseCase;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Accounting: external usage reports, per-user and admin reports, the call journal, erasure. */
public interface UsageUseCase {

    String report(UsageReport report);

    List<UsageRow> usage(@Nullable UUID userId, LocalDate from, LocalDate to);

    List<AiCall> calls(CallFilter filter, @Nullable CallCursor after, int limit);

    void deleteUser(UUID userId);

    /** Clears masked bodies after 30 days and rows after 90 days (§2.8, INV-AIG-04). */
    void applyRetention();

    record UsageReport(
            String callerService,
            AiProvider provider,
            String model,
            AiUseCase useCase,
            @Nullable UUID userId,
            @Nullable String feature,
            int inputTokens,
            int outputTokens,
            double audioSeconds,
            int characters,
            long latencyMs) {}
}
