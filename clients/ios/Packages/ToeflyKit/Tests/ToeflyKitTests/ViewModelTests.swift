import Foundation
import Testing
@testable import ToeflyCore
@testable import ToeflyFeatures

@MainActor
@Suite("Sign in")
struct SignInViewModelTests {
    @Test("AC-01.05.1 signing in with email opens the session and continues")
    func ac01051SigningIn() async {
        let session = FakeSession()
        let model = SignInViewModel(session: session, appleSignInEnabled: false)
        var continued = false
        model.onSignedIn = { continued = true }
        await model.signIn(with: .email)
        #expect(continued)
        #expect(await session.signedInWith == [.email])
        #expect(model.errorMessage == nil)
        #expect(!model.isBusy)
    }

    @Test("AC-01.03.1 Continue with Google")
    func ac01031ContinueWith() async {
        let session = FakeSession()
        let model = SignInViewModel(session: session, appleSignInEnabled: false)
        await model.signIn(with: .google)
        #expect(await session.signedInWith == [.google])
    }

    @Test("AC-01.03.4 cancelling shows the sign-in page without an error")
    func ac01034CancellingShows() async {
        let model = SignInViewModel(session: FakeSession(signInResult: .cancelled), appleSignInEnabled: false)
        var continued = false
        model.onSignedIn = { continued = true }
        await model.signIn(with: .google)
        #expect(!continued)
        #expect(model.errorMessage == nil)
    }

    @Test("AC-01.04.4 without the Apple provider the button explains itself; other methods still work")
    func ac01044WithoutThe() async {
        let session = FakeSession()
        let model = SignInViewModel(session: session, appleSignInEnabled: false)
        await model.signIn(with: .apple)
        #expect(model.errorMessage == "Sign in with Apple is unavailable in this build")
        #expect(await session.signedInWith.isEmpty)
        model.dismissError()
        #expect(model.errorMessage == nil)
        await model.signIn(with: .email)
        #expect(await session.signedInWith == [.email])
    }

    @Test("AC-01.04.1 with the provider configured Apple goes through Keycloak")
    func ac01041WithThe() async {
        let session = FakeSession()
        await SignInViewModel(session: session, appleSignInEnabled: true).signIn(with: .apple)
        #expect(await session.signedInWith == [.apple])
    }

    @Test("Network and provider failures show a neutral message")
    func networkAndProviderFailuresShowA() async {
        let offline = SignInViewModel(session: FakeSession(signInResult: .network), appleSignInEnabled: false)
        await offline.signIn(with: .email)
        #expect(offline.errorMessage?.hasPrefix("You're offline") == true)
        let failing = SignInViewModel(session: FakeSession(signInResult: .stateMismatch), appleSignInEnabled: false)
        await failing.signIn(with: .email)
        #expect(failing.errorMessage == "We couldn't sign you in. Try again.")
    }
}

@MainActor
@Suite("Onboarding")
struct OnboardingViewModelTests {
    private func model(
        _ scenario: FixtureScenario = .newStudent,
        notifications: RecordingNotifications = RecordingNotifications(),
    )
        -> (OnboardingViewModel, FixtureBackend) {
        let fixture = fixtureRepositories(scenario)
        let profiles = fixture.profiles
        let backend = fixture.backend
        let model = OnboardingViewModel(
            profiles: profiles,
            notifications: notifications,
            now: { testNow },
            calendar: testCalendar,
            deviceTimeZone: minsk,
        )
        return (model, backend)
    }

    @Test("AC-02.01.1 the goal step shows CEFR and the 0–120 equivalent")
    func ac02011TheGoal() async {
        let (model, _) = model()
        await model.load()
        #expect(model.step == .goal)
        #expect(model.stepLabel == "Step 1 of 6")
        #expect(model.goalHint == "4.5 ≈ B2 ≈ 84+")
        model.overallTarget = 5
        #expect(model.goalHint == "5.0 ≈ C1 ≈ 95+")
        #expect(!model.canGoBack)
    }

    @Test("AC-02.01.2 per-section targets give a read-only overall")
    func ac02012PerSection() async {
        let (model, backend) = model()
        await model.load()
        model.setPerSection = true
        model.sectionTargets = SectionTargets(reading: 5, listening: 5, writing: 4, speaking: 4)
        #expect(model.goalHint == "Overall target: 4.5 ≈ B2 ≈ 84+")
        await model.continueTapped()
        #expect(model.step == .examDate)
        #expect(model.state.sectionTargets?.writing == 4)
        #expect(await backend.savedRequests == ["saveOnboarding"])
    }

    @Test("AC-02.01.3 the exam date must be within the next five years")
    func ac02013TheExam() async {
        let (model, _) = model()
        await model.load()
        await model.continueTapped()
        #expect(model.step == .examDate)
        #expect(!model.canContinue) // no date yet
        model.examDate = day(0)
        #expect(model.examDateError == "Pick a date within the next 5 years")
        #expect(!model.canContinue)
        model.examDate = day(3)
        #expect(model.examDateError == nil)
        #expect(model.examDateWarning == OnboardingRules.examSoonWarning)
        model.examDate = day(60)
        #expect(model.examDateWarning == nil)
        #expect(model.examDateRange.lowerBound == day(1))
        await model.continueTapped()
        #expect(model.step == .schedule)
        #expect(model.state.examDate == day(60))
    }

    @Test("AC-02.01.4 not booked: the date is not saved and the plan is 8 weeks")
    func ac02014NotBooked() async {
        let (model, _) = model()
        await model.load()
        await model.continueTapped()
        model.examNotBooked = true
        #expect(model.canContinue)
        #expect(model.examDateError == nil)
        await model.continueTapped()
        #expect(model.state.examBooked == false)
        #expect(model.state.examDate == nil)
    }

    @Test("AC-02.02.1 AC-02.02.2 AC-02.02.3 minutes, the 15-minute warning and the reminder time")
    func ac02021Ac02() async {
        let notifications = RecordingNotifications()
        let (model, _) = model(.midOnboarding, notifications: notifications)
        await model.load()
        #expect(model.step == .schedule)
        #expect(model.minutesPerDay == 60)
        #expect(model.minutesWarning == nil)
        model.choose(minutes: 15)
        #expect(model.minutesWarning == "15 minutes a day may not be enough to reach your target by the exam date")
        #expect(model.canContinue)
        model.studyTime = "07:30"
        #expect(model.reminderNote.contains("07:30"))
        await model.continueTapped()
        #expect(model.state.minutesPerDay == 15)
        #expect(model.state.preferredStudyTime == "07:30")
        #expect(notifications.requests == 1)
        #expect(model.step == .explanationLanguage)
    }

    @Test("AC-02.03.1 AC-02.03.2 time zone from the device and the explanations language")
    func ac02031Ac02() async {
        let (model, _) = model(.midOnboarding)
        await model.load()
        await model.continueTapped()
        #expect(model.step == .explanationLanguage)
        #expect(model.timezone == "Europe/Minsk")
        #expect(OnboardingViewModel.timeZoneIdentifiers.contains("Europe/Minsk"))
        model.explanationLanguage = .ru
        await model.continueTapped()
        #expect(model.state.explanationLanguage == .ru)
        #expect(model.step == .consents)
        #expect(model.aiProcessing)
        #expect(!model.productEmails)
    }

    @Test("AC-02.04.1 the full flow completes the onboarding")
    func ac02041TheFull() async {
        let (model, backend) = model()
        var completed = false
        model.onCompleted = { completed = true }
        await model.load()
        await model.continueTapped() // goal
        model.examNotBooked = true
        await model.continueTapped() // exam date
        await model.continueTapped() // schedule
        await model.continueTapped() // language
        model.productEmails = true
        await model.continueTapped() // consents
        #expect(model.step == .diagnosticIntro)
        #expect(model.continueTitle == "Continue to diagnostic")
        #expect(model.summary.map(\.value) == ["4.5 ≈ B2 ≈ 84+", "Not booked yet", "60 min"])
        await model.continueTapped()
        #expect(completed)
        #expect(model.state.status == .completed)
        #expect(await backend.savedRequests.contains("recordConsent:MARKETING_EMAILS=true"))
    }

    @Test("AC-02.04.2 a returning student resumes at the first unfilled step with saved values")
    func ac02042AReturning() async {
        let (model, _) = model(.midOnboarding)
        await model.load()
        #expect(model.step == .schedule)
        #expect(model.overallTarget == 5)
        #expect(model.examNotBooked)
        model.back()
        #expect(model.step == .examDate)
        model.back()
        model.back()
        #expect(model.step == .goal)
    }

    @Test("Summary shows a booked exam date")
    func summaryShowsABookedExamDate() async {
        let (model, _) = model()
        await model.load()
        await model.continueTapped()
        model.examDate = day(60)
        await model.continueTapped()
        #expect(model.summary[1].value != "Not booked yet")
    }

    @Test("docs/07 §3.20 a failed save keeps the step and shows the error")
    func docs07320AFailed() async {
        let model = OnboardingViewModel(
            profiles: FailingProfiles(error: .offline),
            now: { testNow },
            calendar: testCalendar,
        )
        await model.load()
        #expect(model.errorMessage?.hasPrefix("You're offline") == true)
        await model.continueTapped()
        #expect(model.step == .goal)
        #expect(!model.isSaving)
    }
}

@MainActor
@Suite("Home")
struct HomeViewModelTests {
    private func model(_ scenario: FixtureScenario) -> HomeViewModel {
        let fixture = fixtureRepositories(scenario)
        let profiles = fixture.profiles
        let planning = fixture.planning
        return HomeViewModel(profile: nil, profiles: profiles, planning: planning, now: { testNow })
    }

    @Test("AC-02.04.4 without a plan the main block is the diagnostic")
    func ac02044WithoutA() async {
        let model = model(.onboarded)
        #expect(model.loadState == .loading)
        #expect(!model.showsDiagnostic)
        await model.load()
        #expect(model.loadState == .loaded)
        #expect(model.showsDiagnostic)
        #expect(!model.hasPlan)
        #expect(model.topTasks.isEmpty)
        #expect(model.greeting == "Welcome, Maria")
        #expect(model.goalHint == "5.0 ≈ C1 ≈ 95+")
        #expect(model.examText == "Exam date not booked")
        #expect(model.weekTitle == nil)
        #expect(model.todayMinutesText == nil)
        #expect(model.lastUpdated == testNow)
    }

    @Test("AC-05.02.1 Today block with the first three tasks, week progress and the daily goal")
    func ac05021TodayBlock() async {
        let model = model(.withPlan)
        await model.load()
        #expect(!model.showsDiagnostic)
        #expect(model.topTasks.map(\.title) == [
            "Complete the Words: Coral reefs",
            "Academic talk: Biology",
            "Take an interview: Remote work",
        ])
        #expect(model.todayCaption == "Week 3, day 4")
        #expect(model.todayMinutesText == "about 24 min")
        #expect(model.goalMinutes?.done == 9)
        #expect(model.goalMinutes?.goal == 45)
        #expect(model.weekTitle == "Week 3 progress")
        #expect(model.week?.progressPercent == 42)
        #expect(model.nextMockExamText == nil)
    }

    @Test("docs/07 §3.20 a failed load shows the error and keeps a retry")
    func docs07320AFailed() async {
        let fixture = fixtureRepositories(.withPlan)
        let planning = fixture.planning
        let model = HomeViewModel(profile: nil, profiles: FailingProfiles(error: .offline), planning: planning)
        await model.load()
        #expect(model.loadState == .failed(.offline))
        #expect(model.greeting == "Welcome")
        #expect(model.isStudent)
    }

    @Test("Exam date and mock exam texts")
    func examDateAndMockExamTexts() {
        let model = model(.withPlan)
        #expect(model.examText == "Exam date not booked")
        #expect(model.goalMinutes == nil)
        #expect(model.todayCaption == nil)
    }
}

@MainActor
@Suite("App")
struct AppModelTests {
    private func app(_ scenario: FixtureScenario, session: FakeSession = FakeSession()) -> AppModel {
        let fixture = fixtureRepositories(scenario)
        let profiles = fixture.profiles
        let planning = fixture.planning
        return AppModel(
            configuration: .local,
            session: session,
            profiles: profiles,
            planning: planning,
            now: { testNow },
        )
    }

    @Test("Cold start without a session shows the welcome screen")
    func coldStartWithoutASessionShows() async {
        let app = app(.newStudent)
        #expect(app.phase == .launching)
        await app.start()
        #expect(app.phase == .signedOut)
        #expect(app.notice == nil)
    }

    @Test("AC-02.04.2 a student who has not finished goes to the onboarding after sign-in")
    func ac02042AStudent() async {
        let app = app(.newStudent)
        await app.start()
        await app.signIn.signIn(with: .email)
        #expect(app.phase == .onboarding)
        #expect(app.onboarding?.step == .goal)
        #expect(app.profile?.email == "maria.petrova@example.com")
    }

    @Test("AC-02.04.3 a finished onboarding opens Home, never the onboarding again")
    func ac02043AFinished() async {
        let app = app(.onboarded, session: FakeSession(restoreResult: true))
        await app.start()
        #expect(app.phase == .main)
        #expect(app.onboarding == nil)
        #expect(app.home != nil)
        #expect(app.selectedTab == .home)
    }

    @Test("AC-02.04.1 completing the onboarding opens Home")
    func ac02041CompletingThe() async {
        let app = app(.midOnboarding, session: FakeSession(restoreResult: true))
        await app.start()
        await app.onboardingCompleted()
        #expect(app.phase == .main)
        #expect(app.profile?.onboardingStatus == .completed)
    }

    @Test("AC-05.03.2 five tabs; Open Today switches the tab")
    func ac05032FiveTabs() async {
        #expect(AppTab.allCases.map(\.title) == ["Home", "Today", "Weeks", "Library", "Progress"])
        #expect(AppTab.allCases.map(\.symbol) == ["house", "sun.max", "calendar", "books.vertical", "chart.bar"])
        let app = app(.withPlan, session: FakeSession(restoreResult: true))
        await app.start()
        app.openToday()
        #expect(app.selectedTab == .today)
    }

    @Test("AC-01.05.3 sign-out ends the session and returns to the welcome screen")
    func ac01053SignOut() async {
        let session = FakeSession(restoreResult: true)
        let app = app(.onboarded, session: session)
        await app.start()
        app.isProfilePresented = true
        app.selectedTab = .weeks
        await app.signOut()
        #expect(app.phase == .signedOut)
        #expect(await session.signOutCount == 1)
        #expect(app.home == nil)
        #expect(!app.isProfilePresented)
        #expect(app.selectedTab == .home)
    }

    @Test("AC-01.05.5 an expired session returns to sign-in and reopens the same tab")
    func ac01055AnExpired() async {
        let session = FakeSession(restoreResult: true)
        let app = app(.onboarded, session: session)
        await app.start()
        app.selectedTab = .progress
        app.isTutorPresented = true
        await session.expire()
        #expect(app.phase == .signedOut)
        #expect(app.notice == "Your session has ended. Sign in again to continue.")
        #expect(!app.isTutorPresented)
        app.sessionExpired() // a second notice changes nothing
        await app.signIn.signIn(with: .email)
        #expect(app.phase == .main)
        #expect(app.selectedTab == .progress)
        #expect(app.notice == nil)
    }

    @Test("A profile that cannot be loaded keeps the welcome screen with a message")
    func aProfileThatCannotBeLoaded() async {
        let fixture = fixtureRepositories(.onboarded)
        let planning = fixture.planning
        let app = AppModel(
            configuration: .local,
            session: FakeSession(restoreResult: true),
            profiles: FailingProfiles(error: .offline),
            planning: planning,
        )
        await app.start()
        #expect(app.phase == .signedOut)
        #expect(app.notice?.hasPrefix("You're offline") == true)
        let expired = AppModel(
            configuration: .local,
            session: FakeSession(restoreResult: true),
            profiles: FailingProfiles(error: .sessionExpired),
            planning: planning,
        )
        await expired.start()
        #expect(expired.phase == .signedOut)
    }
}
