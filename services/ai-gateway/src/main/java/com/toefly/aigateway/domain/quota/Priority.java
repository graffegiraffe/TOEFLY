package com.toefly.aigateway.domain.quota;

import java.time.Duration;

/** Queue of a request (§2.5.2): how long it may wait for quota and which reserve it must leave to others. */
public enum Priority {
    INTERACTIVE(Duration.ofSeconds(3)),
    GRADING(Duration.ofSeconds(120)),
    BACKGROUND(Duration.ofMinutes(10));

    private final Duration waitPerCandidate;

    Priority(Duration waitPerCandidate) {
        this.waitPerCandidate = waitPerCandidate;
    }

    /** Default wait for one candidate before the chain moves on. */
    public Duration waitPerCandidate() {
        return waitPerCandidate;
    }
}
