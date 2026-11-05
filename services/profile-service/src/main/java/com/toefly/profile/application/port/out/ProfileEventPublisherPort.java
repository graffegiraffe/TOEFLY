package com.toefly.profile.application.port.out;

import com.toefly.profile.domain.consent.Consent;
import com.toefly.profile.domain.privacy.AccountDeletion;
import com.toefly.profile.domain.profile.Role;
import com.toefly.profile.domain.profile.UserProfile;
import java.time.Instant;
import java.util.Set;

/** Writes profile events to the outbox in the transaction of the change. */
public interface ProfileEventPublisherPort {

    void profileCreated(UserProfile profile, boolean emailVerified);

    void onboardingCompleted(UserProfile profile, Instant occurredAt);

    void preferencesUpdated(UserProfile profile, Instant occurredAt);

    void consentChanged(Consent consent);

    void accountDeletionRequested(AccountDeletion deletion, Set<Role> roles);
}
