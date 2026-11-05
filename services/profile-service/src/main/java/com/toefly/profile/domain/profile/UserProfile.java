package com.toefly.profile.domain.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Profile of a user, created on the first request with the Keycloak token (AC-01.01.5). Learners fill in the
 * onboarding step by step; values are kept between visits and completing it fixes the first goal revision.
 */
public record UserProfile(
        UUID userId,
        String email,
        @Nullable String displayName,
        Set<Role> roles,
        Preferences preferences,
        @Nullable StudyGoal goal,
        @Nullable ExamPlan exam,
        OnboardingStatus onboardingStatus,
        Set<OnboardingStep> filledSteps,
        int goalRevision,
        long version,
        Instant createdAt) {

    public static final int DISPLAY_NAME_MAX = 100;

    public UserProfile {
        roles = Set.copyOf(roles);
        filledSteps = filledSteps.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(filledSteps));
    }

    public static UserProfile firstSignIn(
            UUID userId, String email, @Nullable String displayName, Set<Role> roles, ZoneId zone, Instant now) {
        return new UserProfile(
                userId,
                email,
                trimName(displayName),
                roles.isEmpty() ? Set.of(Role.STUDENT) : roles,
                Preferences.defaults(zone),
                null,
                null,
                OnboardingStatus.NOT_STARTED,
                Set.of(),
                0,
                0,
                now);
    }

    public boolean isStudent() {
        return roles.contains(Role.STUDENT);
    }

    /** The first step the learner has not saved yet; null when every step is filled. */
    public @Nullable OnboardingStep nextOnboardingStep() {
        for (OnboardingStep step : OnboardingStep.values()) {
            if (!filledSteps.contains(step)) {
                return step;
            }
        }
        return null;
    }

    /** Saves the given steps of an unfinished onboarding (AC-02.04.2). */
    public UserProfile saveOnboarding(OnboardingDraft draft) {
        requireStudent();
        if (onboardingStatus == OnboardingStatus.COMPLETED) {
            throw new OnboardingAlreadyCompletedException();
        }
        EnumSet<OnboardingStep> steps =
                filledSteps.isEmpty() ? EnumSet.noneOf(OnboardingStep.class) : EnumSet.copyOf(filledSteps);
        StudyGoal newGoal = goal;
        ExamPlan newExam = exam;
        Preferences newPreferences = preferences;
        if (draft.goal() != null) {
            newGoal = draft.goal();
            steps.add(OnboardingStep.GOAL);
        }
        if (draft.exam() != null) {
            newExam = draft.exam();
            steps.add(OnboardingStep.EXAM_DATE);
        }
        if (draft.minutesPerDay() != null) {
            newPreferences = newPreferences.withSchedule(
                    draft.minutesPerDay(),
                    draft.preferredStudyTime() == null
                            ? newPreferences.preferredStudyTime()
                            : draft.preferredStudyTime());
            steps.add(OnboardingStep.SCHEDULE);
        }
        if (draft.timezone() != null) {
            newPreferences = newPreferences.withTimezone(draft.timezone());
        }
        if (draft.explanationLanguage() != null) {
            newPreferences = newPreferences.withExplanationLanguage(draft.explanationLanguage());
            steps.add(OnboardingStep.EXPLANATION_LANGUAGE);
        }
        return new UserProfile(
                userId,
                email,
                displayName,
                roles,
                newPreferences,
                newGoal,
                newExam,
                OnboardingStatus.IN_PROGRESS,
                steps,
                goalRevision,
                version + 1,
                createdAt);
    }

    /** Finishes the onboarding once every step is filled; the goal becomes revision 1 (AC-02.04.1). */
    public UserProfile completeOnboarding() {
        requireStudent();
        if (onboardingStatus == OnboardingStatus.COMPLETED) {
            throw new OnboardingAlreadyCompletedException();
        }
        EnumSet<OnboardingStep> missing = EnumSet.allOf(OnboardingStep.class);
        missing.removeAll(filledSteps);
        if (!missing.isEmpty()) {
            throw new OnboardingIncompleteException(missing);
        }
        return new UserProfile(
                userId,
                email,
                displayName,
                roles,
                preferences,
                goal,
                exam,
                OnboardingStatus.COMPLETED,
                filledSteps,
                1,
                version + 1,
                createdAt);
    }

    /** Changes the goal after the onboarding; consumers see a new revision (US-02.05). */
    public UserProfile changeGoal(StudyGoal newGoal, ExamPlan newExam, int minutesPerDay) {
        requireStudent();
        if (onboardingStatus != OnboardingStatus.COMPLETED) {
            throw new InvalidProfileValueException("onboarding-incomplete", "Finish the onboarding first.");
        }
        return new UserProfile(
                userId,
                email,
                displayName,
                roles,
                preferences.withSchedule(minutesPerDay, preferences.preferredStudyTime()),
                newGoal,
                newExam,
                onboardingStatus,
                filledSteps,
                goalRevision + 1,
                version + 1,
                createdAt);
    }

    public UserProfile updatePreferences(Preferences newPreferences) {
        return new UserProfile(
                userId,
                email,
                displayName,
                roles,
                newPreferences,
                goal,
                exam,
                onboardingStatus,
                filledSteps,
                goalRevision,
                version + 1,
                createdAt);
    }

    public UserProfile rename(@Nullable String newName) {
        return new UserProfile(
                userId,
                email,
                trimName(newName),
                roles,
                preferences,
                goal,
                exam,
                onboardingStatus,
                filledSteps,
                goalRevision,
                version + 1,
                createdAt);
    }

    /**
     * Adds the TUTOR role to a learner; the STUDENT role stays, so the tutor keeps preparing (docs/10 §5).
     * The terms of the tutor workspace must be accepted.
     */
    public UserProfile becomeTutor(boolean acceptTutorTerms) {
        requireStudent();
        if (roles.contains(Role.TUTOR)) {
            throw new TutorRoleAlreadyGrantedException();
        }
        if (!acceptTutorTerms) {
            throw new InvalidProfileValueException("tutor-terms", "Accept the tutor terms to open a workspace.");
        }
        EnumSet<Role> withTutor = EnumSet.copyOf(roles);
        withTutor.add(Role.TUTOR);
        return new UserProfile(
                userId,
                email,
                displayName,
                withTutor,
                preferences,
                goal,
                exam,
                onboardingStatus,
                filledSteps,
                goalRevision,
                version + 1,
                createdAt);
    }

    public void requireVersion(long expected) {
        if (expected != version) {
            throw new VersionMismatchException(version);
        }
    }

    /** Exam date as it was saved, for display; null without a booked exam. */
    public @Nullable LocalDate examDate() {
        return exam == null ? null : exam.examDate();
    }

    private void requireStudent() {
        if (!isStudent()) {
            throw new StudentOnlyException();
        }
    }

    private static @Nullable String trimName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.strip();
        if (trimmed.length() > DISPLAY_NAME_MAX) {
            throw new InvalidProfileValueException("display-name", "A name has at most 100 characters.");
        }
        return trimmed;
    }
}
