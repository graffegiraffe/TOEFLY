package com.toefly.profile.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.profile.application.port.in.SignedInUser;
import com.toefly.profile.domain.consent.ConsentSource;
import com.toefly.profile.domain.consent.ConsentType;
import com.toefly.profile.domain.profile.Band;
import com.toefly.profile.domain.profile.ExamPlan;
import com.toefly.profile.domain.profile.ExplanationLanguage;
import com.toefly.profile.domain.profile.OnboardingDraft;
import com.toefly.profile.domain.profile.OnboardingStatus;
import com.toefly.profile.domain.profile.Role;
import com.toefly.profile.domain.profile.StudyGoal;
import com.toefly.profile.domain.profile.UserProfile;
import com.toefly.profile.domain.profile.VersionMismatchException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileCommandServiceTest {

    final Fakes.Profiles profiles = new Fakes.Profiles();
    final Fakes.Consents consents = new Fakes.Consents();
    final Fakes.Events events = new Fakes.Events();
    final Fakes.Identity identity = new Fakes.Identity();
    final ProfileCommandService service =
            new ProfileCommandService(profiles, consents, events, identity, Fakes.CLOCK, "2026-11-01");

    final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("AC-01.01.5 the first request creates the profile and publishes profile-created exactly once")
    void createsProfileOnce() {
        UserProfile first = service.getOrCreate(Fakes.student(userId));
        UserProfile second = service.getOrCreate(Fakes.student(userId));

        assertThat(first.userId()).isEqualTo(userId);
        assertThat(second).isEqualTo(first);
        assertThat(events.published).containsExactly("profile-created");
    }

    @Test
    @DisplayName("AC-01.01.5 a concurrent creation does not publish a second event")
    void concurrentCreationReadsTheWinner() {
        UserProfile winner = UserProfile.firstSignIn(
                userId, "kate@example.com", null, Set.of(Role.STUDENT), ZoneId.of("UTC"), Fakes.CLOCK.instant());
        Fakes.Profiles racing = new Fakes.Profiles() {
            @Override
            public java.util.Optional<UserProfile> findById(UUID id) {
                return rows.isEmpty() ? java.util.Optional.empty() : super.findById(id);
            }

            @Override
            public boolean insertIfAbsent(UserProfile profile) {
                rows.put(winner.userId(), winner);
                return false;
            }
        };
        var racingService = new ProfileCommandService(racing, consents, events, identity, Fakes.CLOCK, "v1");

        assertThat(racingService.getOrCreate(Fakes.student(userId))).isEqualTo(winner);
        assertThat(events.published).isEmpty();
    }

    @Test
    @DisplayName("AC-01.01.1 a tutor sign-up gets the TUTOR role and the sign-up consents are recorded")
    void tutorSignUp() {
        SignedInUser tutor = new SignedInUser(
                userId,
                "tutor@example.com",
                true,
                null,
                Set.of(Role.STUDENT),
                Role.TUTOR,
                ConsentType.REQUIRED_AT_SIGN_UP,
                null,
                "198.51.100.4");

        UserProfile profile = service.getOrCreate(tutor);

        assertThat(profile.roles()).containsExactly(Role.TUTOR);
        assertThat(identity.calls).containsExactly("makeTutor");
        assertThat(consents.journal)
                .extracting(c -> c.source(), c -> c.ipPrefix())
                .containsOnly(org.assertj.core.groups.Tuple.tuple(ConsentSource.SIGN_UP, "198.51.100.0/24"));
        assertThat(events.published)
                .contains("profile-created")
                .filteredOn(name -> name.startsWith("consent-changed"))
                .hasSize(3);
    }

    @Test
    @DisplayName("AC-02.04.1 completing the onboarding stores goal revision 1 and publishes onboarding-completed")
    void completesOnboarding() {
        LocalDate today = LocalDate.now(Fakes.CLOCK);
        service.saveOnboarding(
                Fakes.student(userId),
                new OnboardingDraft(StudyGoal.overall(Band.of(5)), null, null, null, null, null),
                false);

        UserProfile completed = service.saveOnboarding(
                Fakes.student(userId),
                new OnboardingDraft(
                        null,
                        ExamPlan.bookedOn(today.plusDays(30), today),
                        60,
                        LocalTime.of(19, 0),
                        ZoneId.of("Europe/Minsk"),
                        ExplanationLanguage.EN),
                true);

        assertThat(completed.onboardingStatus()).isEqualTo(OnboardingStatus.COMPLETED);
        assertThat(profiles.goalRevisions).hasSize(1);
        assertThat(events.published)
                .containsExactly("profile-created", "onboarding-completed:1", "preferences-updated");
    }

    @Test
    @DisplayName("AC-02.05.1 a goal change publishes the next revision")
    void changeGoal() {
        completesOnboarding();
        long version = profiles.rows.get(userId).version();

        UserProfile changed = service.changeGoal(
                Fakes.student(userId), StudyGoal.overall(Band.of(5.5)), ExamPlan.notBooked(), 30, version);

        assertThat(changed.goalRevision()).isEqualTo(2);
        assertThat(events.published).last().isEqualTo("onboarding-completed:2");
    }

    @Test
    @DisplayName("AC-02.03.3 changing preferences publishes preferences-updated")
    void updatePreferences() {
        UserProfile profile = service.getOrCreate(Fakes.student(userId));

        UserProfile updated = service.updatePreferences(
                Fakes.student(userId), profile.preferences().withTimezone(ZoneId.of("Asia/Tokyo")), 0);

        assertThat(updated.preferences().timezone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
        assertThat(events.published).last().isEqualTo("preferences-updated");
    }

    @Test
    void staleVersionIsRejected() {
        service.getOrCreate(Fakes.student(userId));

        assertThatThrownBy(() -> service.rename(Fakes.student(userId), "New", 5))
                .isInstanceOf(VersionMismatchException.class);
        assertThat(service.rename(Fakes.student(userId), "New", 0).displayName())
                .isEqualTo("New");
    }
}
