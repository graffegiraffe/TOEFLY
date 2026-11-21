import Foundation
import Testing
@testable import ToeflyCore

@Suite("Band scale")
struct BandTests {
    @Test(arguments: [
        (6.0, "6.0 ≈ C2 ≈ 114+"), (5.0, "5.0 ≈ C1 ≈ 95+"), (4.0, "4.0 ≈ B2 ≈ 72+"), (3.0, "3.0 ≈ B1 ≈ 44+"),
        (4.5, "4.5 ≈ B2 ≈ 84+"), (1.0, "1.0 ≈ A1 ≈ 0+"), (2.5, "2.5 ≈ A2 ≈ 33+"),
    ])
    func `AC-02.01.1 the hint shows CEFR and the comparable 0–120 score`(band: Double, expected: String) {
        #expect(Band.hint(band) == expected)
    }

    @Test("AC-02.01.1 the scale is 1.0–6.0 with a step of 0.5")
    func ac02011TheScale() {
        #expect(Band.all.count == 11)
        #expect(Band.all.allSatisfy(Band.isBand))
        #expect(!Band.isBand(6.5))
        #expect(!Band.isBand(4.2))
        #expect(Band.comparableScore(of: 4.2) == nil)
        #expect(Band.hint(4.2).hasSuffix("—"))
    }

    @Test("AC-02.01.2 the overall target is the mean of the sections rounded to 0.5")
    func ac02012TheOverall() {
        #expect(Band.overall(of: [5, 5, 4, 4]) == 4.5)
        #expect(Band.overall(of: [5, 4.5, 4.5, 4.5]) == 4.5)
        #expect(Band.overall(of: [5, 5, 5, 4.5]) == 5)
        #expect(Band.overall(of: [6, 6, 6, 5.5]) == 6)
        #expect(Band.overall(of: []) == 0)
        #expect(SectionTargets(reading: 6, listening: 5, writing: 4, speaking: 3).overall == 4.5)
    }

    @Test("VoiceOver reads the band hint in words")
    func voiceoverReadsTheBandHintIn() {
        #expect(Band.spokenHint(5) == "5.0, C1, about 95 of 120")
    }
}

@Suite("Onboarding rules")
struct OnboardingRulesTests {
    @Test("AC-02.01.3 exam dates from tomorrow to five years ahead")
    func ac02013ExamDates() throws {
        let calendar = testCalendar
        #expect(OnboardingRules
            .examDateError(day(0), today: testNow, calendar: calendar) == "Pick a date within the next 5 years")
        #expect(OnboardingRules.examDateError(day(-3), today: testNow, calendar: calendar) != nil)
        #expect(OnboardingRules.examDateError(day(1), today: testNow, calendar: calendar) == nil)
        let fiveYears = try #require(calendar.date(byAdding: .year, value: 5, to: day(0)))
        #expect(OnboardingRules.examDateError(fiveYears, today: testNow, calendar: calendar) == nil)
        #expect(try OnboardingRules.examDateError(
            #require(calendar.date(byAdding: .day, value: 1, to: fiveYears)),
            today: testNow,
            calendar: calendar,
        ) != nil)
    }

    @Test("docs/07 §5.3 fewer than 7 days to the exam warns about an intensive plan")
    func docs0753FewerThan() {
        #expect(OnboardingRules.isExamSoon(day(6), today: testNow, calendar: testCalendar))
        #expect(!OnboardingRules.isExamSoon(day(7), today: testNow, calendar: testCalendar))
    }

    @Test("AC-02.01.4 AC-02.01.5 plan length from the exam date")
    func ac02014Ac02() {
        #expect(OnboardingRules.planWeeks(examDate: nil, today: testNow, calendar: testCalendar) == 8)
        #expect(OnboardingRules.planWeeks(examDate: day(3), today: testNow, calendar: testCalendar) == 1)
        #expect(OnboardingRules.planWeeks(examDate: day(60), today: testNow, calendar: testCalendar) == 8)
        #expect(OnboardingRules.planWeeks(examDate: day(400), today: testNow, calendar: testCalendar) == 16)
    }

    @Test("AC-02.02.1 minute options and the default of 60")
    func ac02021MinuteOptions() {
        #expect(OnboardingRules.minuteOptions == [15, 30, 45, 60, 90, 120, 180])
        #expect(OnboardingRules.defaultMinutes == 60)
    }

    @Test("AC-02.04.2 the onboarding resumes at the first unfilled step")
    func ac02042TheOnboarding() {
        #expect(OnboardingState(nextStep: .goal).resumeStep == .goal)
        #expect(OnboardingState(nextStep: .examDate).resumeStep == .examDate)
        #expect(OnboardingState(nextStep: .schedule).resumeStep == .schedule)
        #expect(OnboardingState(nextStep: .explanationLanguage).resumeStep == .explanationLanguage)
        #expect(OnboardingState(nextStep: nil).resumeStep == .consents)
    }

    @Test("docs/07 §5.3 six steps in order")
    func docs0753SixSteps() {
        #expect(OnboardingStep.count == 6)
        #expect(OnboardingStep.goal.previous == nil)
        #expect(OnboardingStep.goal.next == .examDate)
        #expect(OnboardingStep.diagnosticIntro.next == nil)
        #expect(OnboardingStep.diagnosticIntro.number == 6)
    }
}

@Suite("Profile")
struct ProfileModelTests {
    @Test("AC-02.04.3 only students who have not finished go to the onboarding")
    func ac02043OnlyStudents() {
        let id = UUID()
        #expect(UserProfile(id: id, email: "a@b.c", displayName: nil, roles: [.student], onboardingStatus: .inProgress)
            .needsOnboarding)
        #expect(!UserProfile(id: id, email: "a@b.c", displayName: nil, roles: [.student], onboardingStatus: .completed)
            .needsOnboarding)
        #expect(!UserProfile(id: id, email: "a@b.c", displayName: nil, roles: [.tutor], onboardingStatus: .notStarted)
            .needsOnboarding)
    }

    @Test("Initials and first name for the avatar and the greeting")
    func initialsAndFirstNameForThe() {
        let named = UserProfile(
            id: UUID(),
            email: "maria@example.com",
            displayName: "Maria Petrova",
            roles: [.student],
            onboardingStatus: .completed,
        )
        #expect(named.initials == "MP")
        #expect(named.firstName == "Maria")
        let anonymous = UserProfile(
            id: UUID(),
            email: "maria.petrova@example.com",
            displayName: nil,
            roles: [.student],
            onboardingStatus: .completed,
        )
        #expect(anonymous.initials == "MP")
        #expect(anonymous.firstName == nil)
    }

    @Test("AC-05.01.1 overdue tasks come first")
    func ac05011OverdueTasks() {
        let task = { (title: String) in WeekTask(
            id: UUID(),
            title: title,
            section: .reading,
            status: .pending,
            estimatedMinutes: 3,
        ) }
        let today = TodaySummary(
            weekNo: 1,
            dayIndex: 1,
            overdue: [task("old")],
            today: [task("new")],
            vocabularyDue: 0,
            waitingForTutor: false,
            estimatedMinutes: 6,
            doneMinutes: 0,
        )
        #expect(today.orderedTasks.map(\.title) == ["old", "new"])
        #expect(WeekSection.vocabulary.label == "Vocabulary")
    }
}

@Suite("Configuration")
struct ConfigurationTests {
    @Test("Info.plist overrides the local endpoints; Apple stays off by default (AC-01.04.4)")
    func infoPlistOverridesTheLocalEndpoints() {
        let defaults = AppConfiguration(infoDictionary: [:])
        #expect(defaults == AppConfiguration(infoDictionary: ["CFBundleShortVersionString": "0.1.0"]))
        #expect(defaults.apiV1URL.absoluteString == "http://localhost:8080/api/v1")
        #expect(!defaults.appleSignInEnabled)
        let custom = AppConfiguration(infoDictionary: [
            "TOEFLYApiBaseURL": "https://api.toefly.app", "TOEFLYAuthIssuer": "https://auth.toefly.app/realms/toefly",
            "TOEFLYAppleSignInEnabled": "YES", "CFBundleShortVersionString": "1.2.0",
        ])
        #expect(custom.apiBaseURL.absoluteString == "https://api.toefly.app")
        #expect(custom.oidc.tokenEndpoint
            .absoluteString == "https://auth.toefly.app/realms/toefly/protocol/openid-connect/token")
        #expect(custom.appleSignInEnabled)
        #expect(custom.clientVersion == "1.2.0")
        #expect(AppConfiguration(infoDictionary: ["TOEFLYAppleSignInEnabled": true]).appleSignInEnabled)
    }

    @Test("docs/10 §4.1 the public client toefly-ios with the app.toefly redirect")
    func docs1041ThePublic() {
        let oidc = AppConfiguration.local.oidc
        #expect(oidc.clientID == "toefly-ios")
        #expect(oidc.redirectURI.absoluteString == "app.toefly://oauth/callback")
        #expect(oidc.callbackScheme == "app.toefly")
        #expect(oidc.scopes.contains("offline_access"))
        #expect(oidc.revocationEndpoint
            .absoluteString == "http://localhost:8180/realms/toefly/protocol/openid-connect/revoke")
    }

    @Test("Local dates are written in the learner's time zone")
    func localDatesAreWrittenInThe() throws {
        let zone = try #require(TimeZone(identifier: "Europe/Minsk"))
        #expect(LocalDateFormat.string(from: testNow, timeZone: zone) == "2026-11-19")
        #expect(LocalDateFormat.date(from: "2026-11-19", timeZone: zone) == testCalendar.startOfDay(for: testNow))
        #expect(LocalDateFormat.date(from: nil) == nil)
    }
}
