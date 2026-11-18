package com.toefly.assessment.domain.grading;

/** How a bullet of a Write an Email task is covered (docs/06-ai-agent.md §5.4.3). */
public record BulletCoverage(int bullet, Status status) {

    public enum Status {
        FULL,
        PARTIAL,
        MISSING
    }

    public boolean attempted() {
        return status != Status.MISSING;
    }
}
