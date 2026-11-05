package com.toefly.profile.adapter.in.web;

import com.toefly.profile.adapter.in.web.api.OnboardingApi;
import com.toefly.profile.adapter.in.web.dto.OnboardingRequest;
import com.toefly.profile.adapter.in.web.dto.OnboardingState;
import com.toefly.profile.adapter.in.web.dto.StudyGoal;
import com.toefly.profile.adapter.in.web.dto.StudyGoalRequest;
import com.toefly.profile.application.port.in.GetProfileUseCase;
import com.toefly.profile.application.port.in.SaveOnboardingUseCase;
import com.toefly.profile.domain.profile.ExamPlan;
import com.toefly.profile.domain.profile.InvalidProfileValueException;
import com.toefly.profile.domain.profile.OnboardingDraft;
import com.toefly.profile.domain.profile.Preferences;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OnboardingController implements OnboardingApi {

    private final GetProfileUseCase getProfile;
    private final SaveOnboardingUseCase saveOnboarding;
    private final CurrentUser currentUser;
    private final Clock clock;

    public OnboardingController(
            GetProfileUseCase getProfile, SaveOnboardingUseCase saveOnboarding, CurrentUser currentUser, Clock clock) {
        this.getProfile = getProfile;
        this.saveOnboarding = saveOnboarding;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<OnboardingState> getOnboarding() {
        return ResponseEntity.ok(ProfileDtos.onboarding(getProfile.getOrCreate(currentUser.get())));
    }

    @Override
    public ResponseEntity<OnboardingState> saveOnboarding(OnboardingRequest request) {
        var user = currentUser.get();
        ZoneId zone = request.getTimezone() == null
                ? getProfile.getOrCreate(user).preferences().timezone()
                : Preferences.zone(request.getTimezone());
        var draft = new OnboardingDraft(
                ProfileDtos.goal(request.getOverallTarget(), request.getSectionTargets()),
                ProfileDtos.exam(request.getExamBooked(), request.getExamDate(), LocalDate.now(clock.withZone(zone))),
                request.getMinutesPerDay(),
                ProfileDtos.localTime(request.getPreferredStudyTime()),
                request.getTimezone() == null ? null : zone,
                request.getExplanationLanguage() == null
                        ? null
                        : ProfileDtos.language(request.getExplanationLanguage()));
        var profile = saveOnboarding.saveOnboarding(user, draft, Boolean.TRUE.equals(request.getComplete()));
        return ResponseEntity.ok(ProfileDtos.onboarding(profile));
    }

    @Override
    public ResponseEntity<StudyGoal> getStudyGoal() {
        var profile = getProfile.getOrCreate(currentUser.get());
        return ResponseEntity.ok().eTag(ETags.of(profile.version())).body(ProfileDtos.studyGoal(profile));
    }

    @Override
    public ResponseEntity<StudyGoal> changeStudyGoal(StudyGoalRequest request, @Nullable String ifMatch) {
        long version = ETags.requireVersion(ifMatch);
        var user = currentUser.get();
        ZoneId zone = getProfile.getOrCreate(user).preferences().timezone();
        var goal = ProfileDtos.goal(request.getOverallTarget(), request.getSectionTargets());
        if (goal == null) {
            throw new InvalidProfileValueException("overall-target", "Set the overall target or the section targets.");
        }
        ExamPlan exam = Objects.requireNonNull(
                ProfileDtos.exam(request.getExamBooked(), request.getExamDate(), LocalDate.now(clock.withZone(zone))));
        var profile = saveOnboarding.changeGoal(user, goal, exam, request.getMinutesPerDay(), version);
        return ResponseEntity.ok().eTag(ETags.of(profile.version())).body(ProfileDtos.studyGoal(profile));
    }
}
