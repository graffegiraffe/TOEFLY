package com.toefly.practice.application.service;

import com.toefly.common.observability.TraceparentProvider;
import org.springframework.stereotype.Component;

/** W3C traceparent of the current request, stored in the metadata of attempt events. */
@Component
class TraceContext {

    private final TraceparentProvider provider;

    TraceContext(TraceparentProvider provider) {
        this.provider = provider;
    }

    String traceparent() {
        return provider.current().orElse("");
    }
}
