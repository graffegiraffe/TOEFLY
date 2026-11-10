package com.toefly.practice.application.port.in;

import com.toefly.common.web.ProblemType;

@ProblemType(value = "request-in-progress", status = 409)
public class RequestInProgressException extends RuntimeException {

    public RequestInProgressException() {
        super("A request with this Idempotency-Key is still running.");
    }
}
