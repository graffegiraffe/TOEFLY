package com.toefly.billing.domain.seat;

/** Every seat of the tutor is taken. */
public class NoFreeSeatException extends RuntimeException {

    public NoFreeSeatException(int limit) {
        super("All " + limit + " seats are taken.");
    }
}
