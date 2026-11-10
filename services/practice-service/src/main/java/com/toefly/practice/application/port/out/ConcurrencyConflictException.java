package com.toefly.practice.application.port.out;

import com.toefly.common.web.ProblemType;
import java.util.UUID;

/** Two writers appended to the same stream; the later one loses (INV-ATT-03). */
@ProblemType(value = "conflict", status = 409)
public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(UUID streamId, int expectedVersion) {
        super("Attempt " + streamId + " changed concurrently after version " + expectedVersion + "; try again.");
    }
}
