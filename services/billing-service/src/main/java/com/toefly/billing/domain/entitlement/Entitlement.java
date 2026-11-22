package com.toefly.billing.domain.entitlement;

/** Access rights (docs/02 §1.3). */
public enum Entitlement {
    UNLIMITED_WEEKS,
    AI_WRITING_CHECK,
    AI_SPEAKING_CHECK,
    SPEAKING,
    MOCK_EXAM,
    AI_TUTOR_CHAT,
    WEEKLY_REVIEW,
    TUTOR_WORKSPACE,
    TUTOR_MATERIALS;

    public boolean aiCheck() {
        return this == AI_WRITING_CHECK || this == AI_SPEAKING_CHECK;
    }
}
