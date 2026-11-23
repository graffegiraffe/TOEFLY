package com.toefly.tutoragent.application.port.out;

import java.util.Map;
import java.util.UUID;

/** {@code tool_invocation} of docs/06 §4.5. */
public interface ToolLog {

    void record(UUID threadId, UUID messageId, String tool, Map<String, Object> args, String status, long latencyMs);
}
