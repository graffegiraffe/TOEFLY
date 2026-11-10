package com.toefly.practice.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** Keyset position in an attempt list: the last row of the previous page. */
public record AttemptCursor(Instant startedAt, UUID attemptId) {}
