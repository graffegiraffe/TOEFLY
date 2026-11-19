import Foundation
import Observation
import ToeflyCore

/// The six onboarding steps; every step is saved before moving on so a closed app resumes at the first unfilled
/// step (EP-02, docs/07-ux-ui.md §5.3). Texts and rules match the web wizard.
@MainActor
@Observable
public final class OnboardingViewModel {
    public private(set) var step: OnboardingStep = .goal
    public private(set) var isLoading = true
    public private(set) var isSaving = false
    public private(set) var errorMessage: String?
    public private(set) var state = OnboardingState()

    // goal (US-02.01)
    public var overallTarget = OnboardingRules.defaultTarget
    public var setPerSection = false
    public var sectionTargets = SectionTargets(all: OnboardingRules.defaultTarget)
    // exam date (AC-02.01.3, AC-02.01.4)
    public var examNotBooked = false
    public var examDate: Date?
    // schedule (US-02.02)
    public var minutesPerDay = OnboardingRules.defaultMinutes
    public var studyTime = OnboardingRules.defaultStudyTime
    // explanation language and time zone (US-02.03)
    public var explanationLanguage: ExplanationLanguage = .en
    public var timezone: String
    // consents (docs/07-ux-ui.md §5.3)
    public var aiProcessing = true
    public var productEmails = false

    @ObservationIgnored public var onCompleted: (@MainActor () async -> Void)?

    private let profiles: any ProfileRepository
    private let notifications: any NotificationPermissionRequesting
    private let now: @Sendable () -> Date
    private let calendar: Calendar
    private var consentsLoaded = false

    public init(
        profiles: any ProfileRepository,
        notifications: any NotificationPermissionRequesting = NoNotificationPermission(),
        now: @escaping @Sendable () -> Date = Date.init,
        calendar: Calendar = .current,
        deviceTimeZone: TimeZone = .current,
    ) {
        self.profiles = profiles
        self.notifications = notifications
        self.now = now
        self.calendar = calendar
        timezone = deviceTimeZone.identifier
    }

    // MARK: - Derived values

    public var stepLabel: String {
        "Step \(step.number) of \(OnboardingStep.count)"
    }

    public var progress: Double {
        Double(step.number) / Double(OnboardingStep.count)
    }

    public var canGoBack: Bool {
        step.previous != nil && !isSaving
    }

    /// The goal shown under the picker: the chosen band or the mean of the sections (AC-02.01.1, AC-02.01.2).
    public var effectiveTarget: Double {
        setPerSection ? sectionTargets.overall : overallTarget
    }

    public var goalHint: String {
        setPerSection ? "Overall target: \(Band.hint(effectiveTarget))" : Band.hint(effectiveTarget)
    }

    public var examDateRange: ClosedRange<Date> {
        OnboardingRules.examDateRange(today: now(), calendar: calendar)
    }

    public var examDateError: String? {
        guard !examNotBooked, let examDate else { return nil }
        return OnboardingRules.examDateError(examDate, today: now(), calendar: calendar)
    }

    public var examDateWarning: String? {
        guard !examNotBooked, let examDate, examDateError == nil,
              OnboardingRules.isExamSoon(examDate, today: now(), calendar: calendar) else { return nil }
        return OnboardingRules.examSoonWarning
    }

    public var minutesWarning: String? {
        minutesPerDay == 15 ? OnboardingRules.fifteenMinutesWarning : nil
    }

    public var reminderNote: String {
        "We'll remind you at \(studyTime). Next, iOS will ask to allow notifications."
    }

    public var canContinue: Bool {
        guard !isSaving, !isLoading else { return false }
        switch step {
        case .examDate: return examNotBooked || (examDate != nil && examDateError == nil)
        default: return true
        }
    }

    public var continueTitle: String {
        step == .diagnosticIntro ? "Continue to diagnostic" : "Continue"
    }

    /// Summary on the last step: target, exam and daily time.
    public var summary: [(label: String, value: String)] {
        let target = state.overallTarget.map(Band.hint) ?? "—"
        let exam: String = if state.examBooked == true, let date = state.examDate {
            date.formatted(.dateTime.day().month(.abbreviated).year())
        } else {
            "Not booked yet"
        }
        return [("Target", target), ("Exam", exam), ("Daily time", "\(state.minutesPerDay) min")]
    }

    public static let timeZoneIdentifiers = TimeZone.knownTimeZoneIdentifiers.sorted()

    // MARK: - Actions

    /// Loads the saved values and opens the first unfilled step (AC-02.04.2).
    public func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            try await apply(profiles.onboarding())
            step = state.resumeStep
            if step == .consents {
                await loadConsents()
            }
        } catch {
            errorMessage = error.message
        }
    }

    public func continueTapped() async {
        guard canContinue else { return }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }
        do {
            switch step {
            case .goal:
                let update = setPerSection ? OnboardingUpdate(sectionTargets: sectionTargets) :
                    OnboardingUpdate(overallTarget: overallTarget)
                try await apply(profiles.saveOnboarding(update))
            case .examDate:
                let update = examNotBooked ? OnboardingUpdate(examBooked: false) : OnboardingUpdate(
                    examBooked: true,
                    examDate: examDate,
                )
                try await apply(profiles.saveOnboarding(update))
            case .schedule:
                try await apply(profiles.saveOnboarding(OnboardingUpdate(
                    minutesPerDay: minutesPerDay,
                    preferredStudyTime: studyTime,
                )))
                await notifications.requestAuthorization()
            case .explanationLanguage:
                try await apply(profiles.saveOnboarding(OnboardingUpdate(
                    timezone: timezone,
                    explanationLanguage: explanationLanguage,
                )))
            case .consents:
                try await profiles.recordConsent(.aiProcessing, granted: aiProcessing)
                try await profiles.recordConsent(.marketingEmails, granted: productEmails)
            case .diagnosticIntro:
                // AC-02.04.1: profile-service saves the StudyGoal and publishes onboarding-completed.
                try await apply(profiles.saveOnboarding(OnboardingUpdate(complete: true)))
                if state.status == .completed {
                    await onCompleted?()
                }
                return
            }
            if let next = step.next {
                step = next
                if next == .consents {
                    await loadConsents()
                }
            }
        } catch {
            errorMessage = error.message
        }
    }

    public func back() {
        guard let previous = step.previous, !isSaving else { return }
        errorMessage = nil
        step = previous
    }

    public func choose(minutes: Int) {
        minutesPerDay = minutes
    }

    // MARK: - Private

    private func apply(_ saved: OnboardingState) {
        state = saved
        if let target = saved.overallTarget {
            overallTarget = target
        }
        if let sections = saved.sectionTargets {
            setPerSection = true
            sectionTargets = sections
        } else {
            sectionTargets = SectionTargets(all: overallTarget)
        }
        if saved.filledSteps.contains(.examDate) {
            examNotBooked = saved.examBooked == false
            examDate = saved.examDate
        }
        if saved.filledSteps.contains(.schedule) {
            minutesPerDay = saved.minutesPerDay
            studyTime = saved.preferredStudyTime
        }
        if saved.filledSteps.contains(.explanationLanguage) {
            explanationLanguage = saved.explanationLanguage
            timezone = saved.timezone
        }
    }

    private func loadConsents() async {
        guard !consentsLoaded else { return }
        if let answers = try? await profiles.consents() {
            aiProcessing = answers[.aiProcessing] ?? true
            productEmails = answers[.marketingEmails] ?? false
            consentsLoaded = true
        }
    }
}
