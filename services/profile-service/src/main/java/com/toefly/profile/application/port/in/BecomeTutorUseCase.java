package com.toefly.profile.application.port.in;

import com.toefly.profile.domain.profile.UserProfile;

public interface BecomeTutorUseCase {

    /**
     * Adds the TUTOR role in Keycloak and ends the sessions: the next sign-in asks to set up two-factor
     * authentication, which is required for tutors (AC-01.07.3).
     */
    UserProfile becomeTutor(SignedInUser user, boolean acceptTutorTerms);
}
