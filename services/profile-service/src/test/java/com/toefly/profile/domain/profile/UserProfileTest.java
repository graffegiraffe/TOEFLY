package com.toefly.profile.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserProfileTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    static UserProfile student() {
        return UserProfile.firstSignIn(
                UUID.randomUUID(), "kate@example.com", " Kate ", Set.of(Role.STUDENT), ZoneOffset.UTC, NOW);
    }

    static OnboardingDraft allSteps() {
        return new OnboardingDraft(
                StudyGoal.overall(Band.of(5)),
                ExamPlan.bookedOn(TODAY.plusDays(60), TODAY),
                90,
                LocalTime.of(8, 0),
                ZoneId.of("Europe/Minsk"),
                ExplanationLanguage.RU);
    }

    @Test
    void firstSignInStartsWithDefaults() {
        UserProfile profile = student();

        assertThat(profile.displayName()).isEqualTo("Kate");
        assertThat(profile.onboardingStatus()).isEqualTo(OnboardingStatus.NOT_STARTED);
        assertThat(profile.nextOnboardingStep()).isEqualTo(OnboardingStep.GOAL);
        assertThat(profile.version()).isZero();
    }

    @Test
    void userWithoutRolesIsAStudent() {
        UserProfile profile = UserProfile.firstSignIn(UUID.randomUUID(), "a@b.c", null, Set.of(), ZoneOffset.UTC, NOW);

        assertThat(profile.roles()).containsExactly(Role.STUDENT);
    }

    @Test
    @DisplayName("AC-02.04.2 saved steps are kept and the onboarding resumes at the first unfilled step")
    void resumesAtFirstUnfilledStep() {
        UserProfile profile = student()
                .saveOnboarding(new OnboardingDraft(StudyGoal.overall(Band.of(4.5)), null, null, null, null, null))
                .saveOnboarding(new OnboardingDraft(null, ExamPlan.notBooked(), null, null, null, null));

        assertThat(profile.onboardingStatus()).isEqualTo(OnboardingStatus.IN_PROGRESS);
        assertThat(profile.goal()).isEqualTo(StudyGoal.overall(Band.of(4.5)));
        assertThat(profile.filledSteps()).containsExactlyInAnyOrder(OnboardingStep.GOAL, OnboardingStep.EXAM_DATE);
        assertThat(profile.nextOnboardingStep()).isEqualTo(OnboardingStep.SCHEDULE);
        assertThat(profile.version()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-02.04.1 completing with every step fixes goal revision 1")
    void completesWhenEveryStepIsFilled() {
        UserProfile completed = student().saveOnboarding(allSteps()).completeOnboarding();

        assertThat(completed.onboardingStatus()).isEqualTo(OnboardingStatus.COMPLETED);
        assertThat(completed.goalRevision()).isEqualTo(1);
        assertThat(completed.nextOnboardingStep()).isNull();
        assertThat(completed.preferences().minutesPerDay()).isEqualTo(90);
        assertThat(completed.preferences().timezone()).isEqualTo(ZoneId.of("Europe/Minsk"));
        assertThat(completed.examDate()).isEqualTo(TODAY.plusDays(60));
    }

    @Test
    @DisplayName("AC-02.04.1 completing with a missing step is rejected")
    void incompleteOnboardingCannotBeCompleted() {
        UserProfile partial = student()
                .saveOnboarding(new OnboardingDraft(StudyGoal.overall(Band.of(4)), null, null, null, null, null));

        assertThatThrownBy(partial::completeOnboarding).isInstanceOf(OnboardingIncompleteException.class);
    }

    @Test
    @DisplayName("AC-02.04.3 the onboarding cannot be changed after it is completed")
    void completedOnboardingIsClosed() {
        UserProfile completed = student().saveOnboarding(allSteps()).completeOnboarding();

        assertThatThrownBy(() -> completed.saveOnboarding(OnboardingDraft.empty()))
                .isInstanceOf(OnboardingAlreadyCompletedException.class);
        assertThatThrownBy(completed::completeOnboarding).isInstanceOf(OnboardingAlreadyCompletedException.class);
    }

    @Test
    @DisplayName("INV-PRF-04 a tutor has no onboarding")
    void tutorHasNoOnboarding() {
        UserProfile tutor =
                UserProfile.firstSignIn(UUID.randomUUID(), "t@b.c", null, Set.of(Role.TUTOR), ZoneOffset.UTC, NOW);

        assertThatThrownBy(() -> tutor.saveOnboarding(allSteps())).isInstanceOf(StudentOnlyException.class);
    }

    @Test
    @DisplayName("AC-02.05.1 a goal change after the onboarding raises the revision")
    void changeGoalRaisesRevision() {
        UserProfile changed = student()
                .saveOnboarding(allSteps())
                .completeOnboarding()
                .changeGoal(StudyGoal.overall(Band.of(5.5)), ExamPlan.notBooked(), 45);

        assertThat(changed.goalRevision()).isEqualTo(2);
        assertThat(changed.preferences().minutesPerDay()).isEqualTo(45);
        assertThat(changed.examDate()).isNull();
    }

    @Test
    void goalCannotChangeBeforeOnboarding() {
        assertThatThrownBy(() -> student().changeGoal(StudyGoal.overall(Band.of(4)), ExamPlan.notBooked(), 30))
                .isInstanceOf(InvalidProfileValueException.class);
    }

    @Test
    void renameAndPreferencesBumpVersion() {
        UserProfile profile = student();
        UserProfile renamed = profile.rename("  ");
        UserProfile updated =
                renamed.updatePreferences(profile.preferences().withExplanationLanguage(ExplanationLanguage.RU));

        assertThat(renamed.displayName()).isNull();
        assertThat(updated.version()).isEqualTo(2);
        assertThatThrownBy(() -> profile.rename("x".repeat(101))).isInstanceOf(InvalidProfileValueException.class);
    }

    @Test
    void requireVersionDetectsLostUpdates() {
        UserProfile profile = student();

        profile.requireVersion(0);
        assertThatThrownBy(() -> profile.requireVersion(3))
                .isInstanceOf(VersionMismatchException.class)
                .satisfies(e -> assertThat(((VersionMismatchException) e).problemProperties())
                        .containsEntry("currentVersion", 0L));
    }
}
