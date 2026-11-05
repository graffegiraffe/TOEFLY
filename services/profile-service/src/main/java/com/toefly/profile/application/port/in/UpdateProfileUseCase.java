package com.toefly.profile.application.port.in;

import com.toefly.profile.domain.profile.Preferences;
import com.toefly.profile.domain.profile.UserProfile;
import org.jspecify.annotations.Nullable;

public interface UpdateProfileUseCase {

    UserProfile rename(SignedInUser user, @Nullable String displayName, long expectedVersion);

    /** Saves preferences and publishes their full snapshot (AC-02.03.3). */
    UserProfile updatePreferences(SignedInUser user, Preferences preferences, long expectedVersion);
}
