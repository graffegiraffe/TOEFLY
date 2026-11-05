package com.toefly.profile.adapter.in.web;

import com.toefly.profile.adapter.in.web.dto.AccountDeletion;
import com.toefly.profile.adapter.in.web.dto.Consent;
import com.toefly.profile.adapter.in.web.dto.ConsentSource;
import com.toefly.profile.adapter.in.web.dto.ConsentType;
import com.toefly.profile.adapter.in.web.dto.DataExport;
import com.toefly.profile.adapter.in.web.dto.ExplanationLanguage;
import com.toefly.profile.adapter.in.web.dto.OnboardingState;
import com.toefly.profile.adapter.in.web.dto.OnboardingStatus;
import com.toefly.profile.adapter.in.web.dto.OnboardingStep;
import com.toefly.profile.adapter.in.web.dto.Preferences;
import com.toefly.profile.adapter.in.web.dto.Role;
import com.toefly.profile.adapter.in.web.dto.SectionTargets;
import com.toefly.profile.adapter.in.web.dto.StudyGoal;
import com.toefly.profile.adapter.in.web.dto.UserProfile;
import com.toefly.profile.domain.profile.Band;
import com.toefly.profile.domain.profile.ExamPlan;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.jspecify.annotations.Nullable;

/** Mapping between the domain and the generated DTOs. */
final class ProfileDtos {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private ProfileDtos() {}

    static UserProfile profile(com.toefly.profile.domain.profile.UserProfile profile) {
        return new UserProfile()
                .id(profile.userId())
                .email(profile.email())
                .displayName(profile.displayName())
                .roles(profile.roles().stream()
                        .sorted()
                        .map(role -> Role.fromValue(role.name()))
                        .toList())
                .onboardingStatus(
                        OnboardingStatus.fromValue(profile.onboardingStatus().name()))
                .createdAt(time(profile.createdAt()));
    }

    static OnboardingState onboarding(com.toefly.profile.domain.profile.UserProfile profile) {
        com.toefly.profile.domain.profile.Preferences preferences = profile.preferences();
        var nextStep = profile.nextOnboardingStep();
        OnboardingState state = new OnboardingState()
                .status(OnboardingStatus.fromValue(profile.onboardingStatus().name()))
                .nextStep(nextStep == null ? null : OnboardingStep.fromValue(nextStep.name()))
                .filledSteps(profile.filledSteps().stream()
                        .sorted()
                        .map(step -> OnboardingStep.fromValue(step.name()))
                        .toList())
                .minutesPerDay(preferences.minutesPerDay())
                .preferredStudyTime(time(preferences.preferredStudyTime()))
                .timezone(preferences.timezone().getId())
                .explanationLanguage(language(preferences.explanationLanguage()));
        if (profile.goal() != null) {
            state.overallTarget(band(profile.goal().overallTarget())).sectionTargets(sections(profile.goal()));
        }
        if (profile.exam() != null) {
            state.examBooked(profile.exam().booked()).examDate(profile.exam().examDate());
        }
        return state;
    }

    static StudyGoal studyGoal(com.toefly.profile.domain.profile.UserProfile profile) {
        var goal = profile.goal();
        ExamPlan exam = profile.exam() == null ? ExamPlan.notBooked() : profile.exam();
        if (goal == null) {
            throw new ResourceNotFoundException("StudyGoal");
        }
        return new StudyGoal()
                .overallTarget(band(goal.overallTarget()))
                .sectionTargets(sections(goal))
                .examBooked(exam.booked())
                .examDate(exam.examDate())
                .minutesPerDay(profile.preferences().minutesPerDay())
                .revision(profile.goalRevision());
    }

    static Preferences preferences(com.toefly.profile.domain.profile.Preferences preferences) {
        return new Preferences()
                .timezone(preferences.timezone().getId())
                .explanationLanguage(language(preferences.explanationLanguage()))
                .minutesPerDay(preferences.minutesPerDay())
                .preferredStudyTime(time(preferences.preferredStudyTime()));
    }

    static Consent consent(com.toefly.profile.domain.consent.Consent consent) {
        return new Consent()
                .consentType(ConsentType.fromValue(consent.type().name()))
                .granted(consent.granted())
                .policyVersion(consent.policyVersion())
                .source(ConsentSource.fromValue(consent.source().name()))
                .recordedAt(time(consent.recordedAt()));
    }

    static DataExport export(com.toefly.profile.domain.privacy.DataExport export) {
        return new DataExport()
                .id(export.id())
                .status(DataExport.StatusEnum.fromValue(export.status().name()))
                .requestedAt(time(export.requestedAt()))
                .readyAt(export.readyAt() == null ? null : time(export.readyAt()));
    }

    static AccountDeletion deletion(com.toefly.profile.domain.privacy.AccountDeletion deletion) {
        return new AccountDeletion()
                .id(deletion.id())
                .status(AccountDeletion.StatusEnum.fromValue(deletion.status().name()))
                .requestedAt(time(deletion.requestedAt()))
                .legalDeadlineAt(time(deletion.legalDeadlineAt()));
    }

    static com.toefly.profile.domain.profile.@Nullable StudyGoal goal(
            @Nullable BigDecimal overall, @Nullable SectionTargets sections) {
        if (sections != null) {
            return com.toefly.profile.domain.profile.StudyGoal.perSection(
                    Band.of(sections.getReading().doubleValue()),
                    Band.of(sections.getListening().doubleValue()),
                    Band.of(sections.getWriting().doubleValue()),
                    Band.of(sections.getSpeaking().doubleValue()));
        }
        return overall == null
                ? null
                : com.toefly.profile.domain.profile.StudyGoal.overall(Band.of(overall.doubleValue()));
    }

    static @Nullable ExamPlan exam(@Nullable Boolean booked, @Nullable LocalDate examDate, LocalDate today) {
        if (booked == null) {
            return null;
        }
        if (!booked) {
            return ExamPlan.notBooked();
        }
        if (examDate == null) {
            throw new com.toefly.profile.domain.profile.InvalidProfileValueException(
                    "exam-date", "Pick a date within the next 12 months");
        }
        return ExamPlan.bookedOn(examDate, today);
    }

    static com.toefly.profile.domain.profile.ExplanationLanguage language(ExplanationLanguage language) {
        return com.toefly.profile.domain.profile.ExplanationLanguage.ofCode(language.getValue());
    }

    static @Nullable LocalTime localTime(@Nullable String value) {
        return value == null ? null : LocalTime.parse(value, HH_MM);
    }

    private static @Nullable SectionTargets sections(com.toefly.profile.domain.profile.StudyGoal goal) {
        if (!goal.hasSectionTargets()) {
            return null;
        }
        return new SectionTargets()
                .reading(band(goal.readingTarget()))
                .listening(band(goal.listeningTarget()))
                .writing(band(goal.writingTarget()))
                .speaking(band(goal.speakingTarget()));
    }

    private static ExplanationLanguage language(com.toefly.profile.domain.profile.ExplanationLanguage language) {
        return ExplanationLanguage.fromValue(language.code());
    }

    private static BigDecimal band(@Nullable Band band) {
        return BigDecimal.valueOf(band == null ? 0 : band.value());
    }

    private static String time(LocalTime time) {
        return time.format(HH_MM);
    }

    private static OffsetDateTime time(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
