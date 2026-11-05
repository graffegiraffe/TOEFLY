package com.toefly.profile.application.port.in;

import com.toefly.profile.domain.profile.UserProfile;

public interface GetProfileUseCase {

    /** Returns the profile and creates it on the first call; creation publishes profile-created once (AC-01.01.5). */
    UserProfile getOrCreate(SignedInUser user);
}
