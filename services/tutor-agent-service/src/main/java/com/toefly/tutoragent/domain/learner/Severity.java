package com.toefly.tutoragent.domain.learner;

/** Weight of one mistake record (docs/06 §6.2): MINOR 0.5, MAJOR 1.0, CRITICAL 1.5, a closed item 1.0. */
public enum Severity {
    MINOR(0.5),
    MAJOR(1.0),
    CRITICAL(1.5),
    CLOSED_ITEM(1.0);

    private final double weight;

    Severity(double weight) {
        this.weight = weight;
    }

    public double weight() {
        return weight;
    }

    /** Only these records go to the vector memory of mistakes (docs/06 §6.4). */
    public boolean memorable() {
        return this != MINOR;
    }
}
