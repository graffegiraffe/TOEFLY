package com.toefly.planning.application.port.in;

import com.toefly.common.web.ProblemType;

@ProblemType(value = "idempotency-key-reuse", status = 422)
public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException() {
        super("This Idempotency-Key was used for another request.");
    }
}
