package com.toefly.billing.domain.plan;

/** Plan tiers (docs/00-README.md §5.2). */
public enum PlanTierCode {
    STUDENT_FREE(Audience.STUDENT),
    STUDENT_PRO(Audience.STUDENT),
    TUTOR(Audience.TUTOR),
    TUTOR_PLUS(Audience.TUTOR);

    private final Audience audience;

    PlanTierCode(Audience audience) {
        this.audience = audience;
    }

    public Audience audience() {
        return audience;
    }

    public boolean tutor() {
        return audience == Audience.TUTOR;
    }

    /** Who may buy a tier: students buy Pro, tutors buy tutor tiers. */
    public enum Audience {
        STUDENT,
        TUTOR
    }
}
