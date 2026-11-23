package com.toefly.tutoragent.application.port.in;

import java.util.Map;

/** Receives the SSE events of one answer as they happen: meta, tool, token, action, retract, done, error. */
@FunctionalInterface
public interface ChatStreamSink {

    void send(int seq, String event, Map<String, Object> data);
}
