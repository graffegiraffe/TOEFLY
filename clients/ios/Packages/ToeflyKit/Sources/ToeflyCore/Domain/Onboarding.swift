import Foundation

/// Steps of the onboarding (docs/07-ux-ui.md §5.3); the same order as the web route `/onboarding/$step`.
public enum OnboardingStep: String, CaseIterable, Sendable, Codable {
    case goal
    case examDate = "exam-date"
    case schedule
    case explanationLanguage = "explanation-language"
    case consents
    case diagnosticIntro = "diagnostic-intro"

    public var number: Int {
        (Self.allCases.firstIndex(of: self) ?? 0) + 1
    }

    public static var count: Int {
        allCases.count
    }

    public var next: OnboardingStep? {
        let index = (Self.allCases.firstIndex(of: self) ?? 0) + 1
        return index < Self.allCases.count ? Self.allCases[index] : nil
    }

    public var previous: OnboardingStep? {
        let index = Self.allCases.firstIndex(of: self) ?? 0
        return index > 0 ? Self.allCases[index - 1] : nil
    }
}

public enum OnboardingStatus: String, Sendable, Equatable {
    case notStarted = "NOT_STARTED"
    case inProgress = "IN_PROGRESS"
    case completed = "COMPLETED"
}

public enum ExplanationLanguage: String, Sendable, Equatable, CaseIterable {
    case en, ru
}

public struct SectionTargets: Sendable, Equatable {
    public var reading: Double
    public var listening: Double
    public var writing: Double
    public var speaking: Double

    public init(reading: Double, listening: Double, writing: Double, speaking: Double) {
        self.reading = reading
        self.listening = listening
        self.writing = writing
        self.speaking = speaking
    }

    public init(all band: Double) {
        self.init(reading: band, listening: band, writing: band, speaking: band)
    }

    public var values: [Double] {
        [reading, listening, writing, speaking]
    }

    public var overall: Double {
        Band.overall(of: values)
    }
}

/// Saved onboarding values and the first unfilled data step (profile-service `OnboardingState`).
public struct OnboardingState: Sendable, Equatable {
    /// Data steps known to profile-service; consents and the diagnostic intro are client-side steps.
    public enum DataStep: String, Sendable, Equatable {
        case goal = "GOAL", examDate = "EXAM_DATE", schedule = "SCHEDULE", explanationLanguage = "EXPLANATION_LANGUAGE"
    }

    public var status: OnboardingStatus
    public var nextStep: DataStep?
    public var filledSteps: [DataStep]
    public var overallTarget: Double?
    public var sectionTargets: SectionTargets?
    public var examBooked: Bool?
    public var examDate: Date?
    public var minutesPerDay: Int
    public var preferredStudyTime: String
    public var timezone: String
    public var explanationLanguage: ExplanationLanguage

    public init(
        status: OnboardingStatus = .notStarted,
        nextStep: DataStep? = .goal,
        filledSteps: [DataStep] = [],
        overallTarget: Double? = nil,
        sectionTargets: SectionTargets? = nil,
        examBooked: Bool? = nil,
        examDate: Date? = nil,
        minutesPerDay: Int = 60,
        preferredStudyTime: String = "19:00",
        timezone: String = "UTC",
        explanationLanguage: ExplanationLanguage = .en,
    ) {
        self.status = status
        self.nextStep = nextStep
        self.filledSteps = filledSteps
        self.overallTarget = overallTarget
        self.sectionTargets = sectionTargets
        self.examBooked = examBooked
        self.examDate = examDate
        self.minutesPerDay = minutesPerDay
        self.preferredStudyTime = preferredStudyTime
        self.timezone = timezone
        self.explanationLanguage = explanationLanguage
    }

    /// The first step without saved values; consents follow once every data step is filled (AC-02.04.2).
    public var resumeStep: OnboardingStep {
        switch nextStep {
        case .goal: .goal
        case .examDate: .examDate
        case .schedule: .schedule
        case .explanationLanguage: .explanationLanguage
        case nil: .consents
        }
    }
}

/// Values of the steps being saved; nil fields keep the saved values (profile-service `OnboardingRequest`).
public struct OnboardingUpdate: Sendable, Equatable {
    public var overallTarget: Double?
    public var sectionTargets: SectionTargets?
    public var examBooked: Bool?
    public var examDate: Date?
    public var minutesPerDay: Int?
    public var preferredStudyTime: String?
    public var timezone: String?
    public var explanationLanguage: ExplanationLanguage?
    public var complete: Bool

    public init(
        overallTarget: Double? = nil,
        sectionTargets: SectionTargets? = nil,
        examBooked: Bool? = nil,
        examDate: Date? = nil,
        minutesPerDay: Int? = nil,
        preferredStudyTime: String? = nil,
        timezone: String? = nil,
        explanationLanguage: ExplanationLanguage? = nil,
        complete: Bool = false,
    ) {
        self.overallTarget = overallTarget
        self.sectionTargets = sectionTargets
        self.examBooked = examBooked
        self.examDate = examDate
        self.minutesPerDay = minutesPerDay
        self.preferredStudyTime = preferredStudyTime
        self.timezone = timezone
        self.explanationLanguage = explanationLanguage
        self.complete = complete
    }
}

/// Rules of the onboarding steps shared with the web client (`clients/web/src/lib/onboarding.ts`).
public enum OnboardingRules {
    /// AC-02.02.1: 15, 30, 45, 60, 90, 120 or 180 minutes a day, 60 by default.
    public static let minuteOptions = [15, 30, 45, 60, 90, 120, 180]
    public static let defaultMinutes = 60
    public static let defaultStudyTime = "19:00"
    public static let defaultTarget = 4.5

    public static let examDateError = "Pick a date within the next 5 years"
    public static let examSoonWarning = "That’s soon. We’ll build a 1-week intensive plan."
    public static let fifteenMinutesWarning = "15 minutes a day may not be enough to reach your target by the exam date"

    /// Tomorrow … today + 5 years in the given calendar (AC-02.01.3).
    public static func examDateRange(today: Date, calendar: Calendar) -> ClosedRange<Date> {
        let start = calendar.startOfDay(for: today)
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: start) ?? start.addingTimeInterval(86400)
        let latest = calendar.date(byAdding: .year, value: 5, to: start) ?? start.addingTimeInterval(5 * 365 * 86400)
        return tomorrow ... latest
    }

    /// An exam date is after today and at most five years ahead (AC-02.01.3).
    public static func examDateError(_ date: Date, today: Date, calendar: Calendar) -> String? {
        examDateRange(today: today, calendar: calendar).contains(calendar.startOfDay(for: date)) ? nil : examDateError
    }

    /// Fewer than 7 days to the exam (docs/07-ux-ui.md §5.3).
    public static func isExamSoon(_ date: Date, today: Date, calendar: Calendar) -> Bool {
        let days = calendar.dateComponents(
            [.day],
            from: calendar.startOfDay(for: today),
            to: calendar.startOfDay(for: date),
        ).day ?? 0
        return days < 7
    }

    /// Plan length in weeks: clamp(floor(days to exam / 7), 1, 16); 8 weeks without a date (AC-02.01.4, AC-02.01.5).
    public static func planWeeks(examDate: Date?, today: Date, calendar: Calendar) -> Int {
        guard let examDate else { return 8 }
        let days = calendar.dateComponents(
            [.day],
            from: calendar.startOfDay(for: today),
            to: calendar.startOfDay(for: examDate),
        ).day ?? 0
        return min(max(days / 7, 1), 16)
    }
}
