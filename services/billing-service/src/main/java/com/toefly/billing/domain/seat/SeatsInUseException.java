package com.toefly.billing.domain.seat;

/** AC-27.05.4: more students than the seats of the target plan. */
public class SeatsInUseException extends RuntimeException {

    public SeatsInUseException(int used, int limit) {
        super("Remove students to " + limit + " or fewer to switch to this plan (" + used + " connected).");
    }
}
