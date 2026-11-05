package com.toefly.profile.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.profile.domain.profile.InvalidProfileValueException;
import com.toefly.profile.domain.profile.Role;
import com.toefly.profile.domain.profile.TutorRoleAlreadyGrantedException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountCommandServiceTest {

    final Fakes.Profiles profiles = new Fakes.Profiles();
    final Fakes.Events events = new Fakes.Events();
    final Fakes.Identity identity = new Fakes.Identity();
    final ProfileCommandService profileService =
            new ProfileCommandService(profiles, new Fakes.Consents(), events, identity, Fakes.CLOCK, "2026-11-01");
    final AccountCommandService service = new AccountCommandService(profileService, profiles, identity, identity);

    final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("AC-01.05.4 signing out everywhere ends Keycloak sessions, then the gateway sessions")
    void signOutEverywhere() {
        service.signOutEverywhere(Fakes.student(userId));

        assertThat(identity.calls).containsExactly("endAllSessions", "revokeSessions");
    }

    @Test
    @DisplayName("AC-01.07.3 a learner becomes a tutor, keeps STUDENT and signs in again to set up MFA")
    void becomeTutor() {
        var tutor = service.becomeTutor(Fakes.student(userId), true);

        assertThat(tutor.roles()).containsExactlyInAnyOrder(Role.STUDENT, Role.TUTOR);
        assertThat(profiles.rows.get(userId).roles()).contains(Role.TUTOR);
        assertThat(identity.calls).containsExactly("addTutorRole", "endAllSessions", "revokeSessions");
    }

    @Test
    @DisplayName("POST /me/tutor-role without accepted terms is rejected and changes nothing")
    void becomeTutorNeedsTerms() {
        assertThatThrownBy(() -> service.becomeTutor(Fakes.student(userId), false))
                .isInstanceOf(InvalidProfileValueException.class);
        assertThat(identity.calls).isEmpty();
    }

    @Test
    @DisplayName("POST /me/tutor-role for a tutor is a conflict")
    void becomeTutorTwice() {
        service.becomeTutor(Fakes.student(userId), true);

        assertThatThrownBy(() -> service.becomeTutor(Fakes.student(userId), true))
                .isInstanceOf(TutorRoleAlreadyGrantedException.class);
    }
}
