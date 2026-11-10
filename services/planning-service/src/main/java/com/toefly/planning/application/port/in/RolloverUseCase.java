package com.toefly.planning.application.port.in;

/** The start of a new day of the learner: carry-over, close and rebalance of the week (docs/03 §10.4–10.6). */
public interface RolloverUseCase {

    /** Processes every plan with an open week; returns the number of plans that changed. */
    int rolloverAll();
}
