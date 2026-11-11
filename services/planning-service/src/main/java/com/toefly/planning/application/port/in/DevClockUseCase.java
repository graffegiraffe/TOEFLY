package com.toefly.planning.application.port.in;

import java.time.Duration;
import java.time.Instant;

/** The clock of the profiles {@code local} and {@code demo}: moves forward for the demo (docs/12, phase 4, decision 7). */
public interface DevClockUseCase {

    Instant now();

    Duration offset();

    /** Moves the clock by {@code delta} (may be negative); returns the new offset. */
    Duration shift(Duration delta);

    void reset();
}
