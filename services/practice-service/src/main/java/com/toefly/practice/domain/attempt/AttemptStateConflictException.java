package com.toefly.practice.domain.attempt;

import com.toefly.common.web.ProblemType;

/** A command does not fit the current state, for example a pause in EXAM (INV-ATT-07). */
@ProblemType(value = "conflict", status = 409)
public class AttemptStateConflictException extends RuntimeException {

    public AttemptStateConflictException(String message) {
        super(message);
    }
}
