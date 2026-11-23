package com.toefly.tutoragent.application.port.in;

import java.time.Instant;

/** The morning check of ReengagementWriter (docs/06 §5.10). */
public interface ReengagementUseCase {

    /** Writes messages for learners whose local time is the morning hour; returns how many were written. */
    int writeDue(Instant now);
}
