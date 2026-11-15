package com.toefly.aigateway.domain.call;

import java.time.Instant;

/** Position in the journal, newest first. */
public record CallCursor(Instant createdAt, String id) {}
