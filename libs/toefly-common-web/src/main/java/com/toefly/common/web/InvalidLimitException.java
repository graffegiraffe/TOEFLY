package com.toefly.common.web;

/** The {@code limit} parameter is outside 1–100. */
@ProblemType(value = "malformed-request", status = 400)
public class InvalidLimitException extends RuntimeException {

    public InvalidLimitException(int limit) {
        super("limit must be between 1 and " + CursorCodec.MAX_LIMIT + ", got " + limit + ".");
    }
}
