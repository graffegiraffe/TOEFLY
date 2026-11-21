import Foundation

/// Canonical role (docs/00-README.md `Role`).
public enum Role: String, Sendable, Equatable {
    case student = "STUDENT", tutor = "TUTOR", admin = "ADMIN"
}

public struct UserProfile: Sendable, Equatable {
    public var id: UUID
    public var email: String
    public var displayName: String?
    public var roles: [Role]
    public var onboardingStatus: OnboardingStatus

    public init(id: UUID, email: String, displayName: String?, roles: [Role], onboardingStatus: OnboardingStatus) {
        self.id = id
        self.email = email
        self.displayName = displayName
        self.roles = roles
        self.onboardingStatus = onboardingStatus
    }

    public var isStudent: Bool {
        roles.contains(.student)
    }

    /// Only students go through the onboarding, once (EP-02).
    public var needsOnboarding: Bool {
        isStudent && onboardingStatus != .completed
    }

    public var firstName: String? {
        displayName?.split(separator: " ").first.map(String.init)
    }

    /// Up to two initials for the avatar.
    public var initials: String {
        let source = displayName.flatMap { $0.isEmpty ? nil : $0 } ?? email
        let letters = source.split(whereSeparator: { $0 == " " || $0 == "." || $0 == "@" }).prefix(2)
            .compactMap(\.first)
        return letters.map { String($0).uppercased() }.joined()
    }
}

public struct StudyGoal: Sendable, Equatable {
    public var overallTarget: Double
    public var examBooked: Bool
    public var examDate: Date?
    public var minutesPerDay: Int

    public init(overallTarget: Double, examBooked: Bool, examDate: Date?, minutesPerDay: Int) {
        self.overallTarget = overallTarget
        self.examBooked = examBooked
        self.examDate = examDate
        self.minutesPerDay = minutesPerDay
    }
}

public enum ConsentType: String, Sendable, Equatable {
    case aiProcessing = "AI_PROCESSING"
    case marketingEmails = "MARKETING_EMAILS"
}

public struct StudyPlanSummary: Sendable, Equatable {
    public var weeksCount: Int
    public var currentWeekNo: Int?
    public var examDate: Date?
    public var daysToExam: Int?

    public init(weeksCount: Int, currentWeekNo: Int?, examDate: Date?, daysToExam: Int?) {
        self.weeksCount = weeksCount
        self.currentWeekNo = currentWeekNo
        self.examDate = examDate
        self.daysToExam = daysToExam
    }
}

public struct WeekProgress: Sendable, Equatable {
    public var weekNo: Int
    public var progressPercent: Double
    public var mockExam: Bool

    public init(weekNo: Int, progressPercent: Double, mockExam: Bool) {
        self.weekNo = weekNo
        self.progressPercent = progressPercent
        self.mockExam = mockExam
    }
}
