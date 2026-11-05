package com.toefly.profile.domain.consent;

import java.util.EnumSet;
import java.util.Set;

/** Kinds of consent (docs/00-README.md §5.2, AC-31.01.1). */
public enum ConsentType {
    TERMS,
    PRIVACY,
    AGE_16_PLUS,
    AI_PROCESSING,
    VOICE_RECORDINGS,
    MARKETING_EMAILS;

    /** Consents without which an account is not created. */
    public static final Set<ConsentType> REQUIRED_AT_SIGN_UP = EnumSet.of(TERMS, PRIVACY, AGE_16_PLUS);

    public boolean requiredAtSignUp() {
        return REQUIRED_AT_SIGN_UP.contains(this);
    }
}
