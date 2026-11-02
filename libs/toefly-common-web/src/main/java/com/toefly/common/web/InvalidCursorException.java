package com.toefly.common.web;

/** The cursor is damaged or does not match the sort and filters of the request. */
@ProblemType(value = "invalid-cursor", status = 400)
public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException() {
        super("The cursor is invalid for this request.");
    }
}
