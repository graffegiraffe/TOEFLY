package com.toefly.tutoragent.application.port.in;

/** Embeds pending records of the mistake memory. */
public interface MemoryUseCase {

    /** Returns how many records got an embedding. */
    int embedPending(int limit);
}
