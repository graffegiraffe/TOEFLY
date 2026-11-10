package com.toefly.practice.application.port.in;

import com.toefly.practice.domain.time.ContextType;
import com.toefly.practice.domain.time.HeartbeatState;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface RecordHeartbeatUseCase {

    /** Active time of an attempt every 15 s; PAUSED and RESUMED also pause the attempt (AC-16.01.1, AC-06.01.3). */
    void attemptHeartbeat(AttemptHeartbeat heartbeat);

    /** Active time outside attempts: grammar steps, materials, external resources. */
    void timeHeartbeat(TimeHeartbeat heartbeat);

    record AttemptHeartbeat(
            UUID userId,
            UUID attemptId,
            long sequence,
            HeartbeatState state,
            int activeSecondsDelta,
            @Nullable Integer audioReplays,
            UUID correlationId) {}

    record TimeHeartbeat(
            UUID userId,
            long sequence,
            ContextType contextType,
            @Nullable UUID weekTaskId,
            @Nullable String refId,
            @Nullable String weekSection,
            int activeSecondsDelta) {}
}
