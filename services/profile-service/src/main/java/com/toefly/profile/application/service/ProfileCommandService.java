package com.toefly.profile.application.service;

import com.toefly.profile.application.port.in.GetProfileUseCase;
import com.toefly.profile.application.port.in.SaveOnboardingUseCase;
import com.toefly.profile.application.port.in.SignedInUser;
import com.toefly.profile.application.port.in.UpdateProfileUseCase;
import com.toefly.profile.application.port.out.ConsentStorePort;
import com.toefly.profile.application.port.out.IdentityProviderPort;
import com.toefly.profile.application.port.out.ProfileEventPublisherPort;
import com.toefly.profile.application.port.out.ProfileStorePort;
import com.toefly.profile.domain.consent.Consent;
import com.toefly.profile.domain.consent.ConsentSource;
import com.toefly.profile.domain.consent.ConsentType;
import com.toefly.profile.domain.profile.ExamPlan;
import com.toefly.profile.domain.profile.OnboardingDraft;
import com.toefly.profile.domain.profile.Preferences;
import com.toefly.profile.domain.profile.Role;
import com.toefly.profile.domain.profile.StudyGoal;
import com.toefly.profile.domain.profile.UserProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileCommandService implements GetProfileUseCase, UpdateProfileUseCase, SaveOnboardingUseCase {

    private final ProfileStorePort profiles;
    private final ConsentStorePort consents;
    private final ProfileEventPublisherPort events;
    private final IdentityProviderPort identity;
    private final Clock clock;
    private final String policyVersion;

    public ProfileCommandService(
            ProfileStorePort profiles,
            ConsentStorePort consents,
            ProfileEventPublisherPort events,
            IdentityProviderPort identity,
            Clock clock,
            @Value("${toefly.profile.policy-version}") String policyVersion) {
        this.profiles = profiles;
        this.consents = consents;
        this.events = events;
        this.identity = identity;
        this.clock = clock;
        this.policyVersion = policyVersion;
    }

    @Override
    @Transactional
    public UserProfile getOrCreate(SignedInUser user) {
        return profiles.findById(user.userId()).orElseGet(() -> create(user));
    }

    private UserProfile create(SignedInUser user) {
        Instant now = clock.instant();
        boolean tutorSignUp =
                user.requestedRole() == Role.TUTOR && !user.roles().contains(Role.TUTOR);
        Set<Role> roles = tutorSignUp ? Set.of(Role.TUTOR) : user.roles();
        UserProfile profile =
                UserProfile.firstSignIn(user.userId(), user.email(), user.displayName(), roles, ZoneOffset.UTC, now);
        if (!profiles.insertIfAbsent(profile)) {
            return profiles.findById(user.userId()).orElseThrow();
        }
        if (tutorSignUp) {
            identity.makeTutor(user.userId());
        }
        for (ConsentType type : user.signUpConsents()) {
            Consent consent = Consent.record(
                    user.userId(), type, true, policyVersion, ConsentSource.SIGN_UP, user.clientIp(), now);
            consents.append(consent);
            events.consentChanged(consent);
        }
        events.profileCreated(profile, user.emailVerified());
        return profile;
    }

    @Override
    @Transactional
    public UserProfile rename(SignedInUser user, @Nullable String displayName, long expectedVersion) {
        UserProfile profile = getOrCreate(user);
        profile.requireVersion(expectedVersion);
        UserProfile renamed = profile.rename(displayName);
        profiles.update(renamed, profile.version());
        return renamed;
    }

    @Override
    @Transactional
    public UserProfile updatePreferences(SignedInUser user, Preferences preferences, long expectedVersion) {
        UserProfile profile = getOrCreate(user);
        profile.requireVersion(expectedVersion);
        UserProfile updated = profile.updatePreferences(preferences);
        profiles.update(updated, profile.version());
        events.preferencesUpdated(updated, clock.instant());
        return updated;
    }

    @Override
    @Transactional
    public UserProfile saveOnboarding(SignedInUser user, OnboardingDraft draft, boolean complete) {
        UserProfile profile = getOrCreate(user);
        UserProfile saved = profile.saveOnboarding(draft);
        if (!complete) {
            profiles.update(saved, profile.version());
            return saved;
        }
        UserProfile completed = saved.completeOnboarding();
        profiles.update(completed, profile.version());
        profiles.appendGoalRevision(completed);
        Instant now = clock.instant();
        events.onboardingCompleted(completed, now);
        events.preferencesUpdated(completed, now);
        return completed;
    }

    @Override
    @Transactional
    public UserProfile changeGoal(
            SignedInUser user, StudyGoal goal, ExamPlan exam, int minutesPerDay, long expectedVersion) {
        UserProfile profile = getOrCreate(user);
        profile.requireVersion(expectedVersion);
        UserProfile changed = profile.changeGoal(goal, exam, minutesPerDay);
        profiles.update(changed, profile.version());
        profiles.appendGoalRevision(changed);
        events.onboardingCompleted(changed, clock.instant());
        return changed;
    }
}
