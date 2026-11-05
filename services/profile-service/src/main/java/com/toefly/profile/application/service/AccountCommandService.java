package com.toefly.profile.application.service;

import com.toefly.profile.application.port.in.BecomeTutorUseCase;
import com.toefly.profile.application.port.in.GetProfileUseCase;
import com.toefly.profile.application.port.in.RevokeSessionsUseCase;
import com.toefly.profile.application.port.in.SignedInUser;
import com.toefly.profile.application.port.out.IdentityProviderPort;
import com.toefly.profile.application.port.out.ProfileStorePort;
import com.toefly.profile.application.port.out.SessionRevocationPort;
import com.toefly.profile.domain.profile.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-in state of the account: signing out everywhere and opening a tutor workspace. */
@Service
public class AccountCommandService implements RevokeSessionsUseCase, BecomeTutorUseCase {

    private final GetProfileUseCase profiles;
    private final ProfileStorePort store;
    private final IdentityProviderPort identity;
    private final SessionRevocationPort sessions;

    public AccountCommandService(
            GetProfileUseCase profiles,
            ProfileStorePort store,
            IdentityProviderPort identity,
            SessionRevocationPort sessions) {
        this.profiles = profiles;
        this.store = store;
        this.identity = identity;
        this.sessions = sessions;
    }

    @Override
    public void signOutEverywhere(SignedInUser user) {
        // Keycloak first: refresh tokens stop working; then the gateway rejects access tokens issued before now.
        identity.endAllSessions(user.userId());
        sessions.revokeSessions(user.userId());
    }

    @Override
    @Transactional
    public UserProfile becomeTutor(SignedInUser user, boolean acceptTutorTerms) {
        UserProfile profile = profiles.getOrCreate(user);
        UserProfile tutor = profile.becomeTutor(acceptTutorTerms);
        store.update(tutor, profile.version());
        identity.addTutorRole(user.userId());
        signOutEverywhere(user);
        return tutor;
    }
}
