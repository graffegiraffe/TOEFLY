import Foundation

/// Profile, onboarding and consents of the signed-in user (profile-service, docs/05-api-contracts.md §6.1).
public protocol ProfileRepository: Sendable {
    func me() async throws(APIError) -> UserProfile
    func onboarding() async throws(APIError) -> OnboardingState
    func saveOnboarding(_ update: OnboardingUpdate) async throws(APIError) -> OnboardingState
    func consents() async throws(APIError) -> [ConsentType: Bool]
    func recordConsent(_ type: ConsentType, granted: Bool) async throws(APIError)
    /// nil before the onboarding is completed (404).
    func studyGoal() async throws(APIError) -> StudyGoal?
}

/// Plan, current week and Today (planning-service, docs/05-api-contracts.md §6.7).
public protocol PlanningRepository: Sendable {
    /// nil while the plan does not exist yet (404): the onboarding event is still on its way or the diagnostic is
    /// pending.
    func currentPlan() async throws(APIError) -> StudyPlanSummary?
    func currentWeek() async throws(APIError) -> WeekProgress?
    func today() async throws(APIError) -> TodaySummary
    /// Every week of the current plan for the gallery (US-04.09).
    func weeks() async throws(APIError) -> [WeekSummary]
    /// The week page (US-04.02); `404` for a week the student cannot see yet (AC-04.02.4).
    func week(id: UUID) async throws(APIError) -> Week
}

/// One heartbeat of the active-time tracker (AC-16.01.1).
public struct Heartbeat: Sendable, Equatable {
    public var sequence: Int64
    public var state: HeartbeatState
    public var activeSecondsDelta: Int
    public var currentItemId: UUID?
    public var audioReplays: Int

    public init(
        sequence: Int64,
        state: HeartbeatState,
        activeSecondsDelta: Int,
        currentItemId: UUID? = nil,
        audioReplays: Int = 0,
    ) {
        self.sequence = sequence
        self.state = state
        self.activeSecondsDelta = activeSecondsDelta
        self.currentItemId = currentItemId
        self.audioReplays = audioReplays
    }
}

/// Attempts, answers, active time and reviews of closed tasks (practice-service, docs/05-api-contracts.md §7.1).
public protocol PracticeRepository: Sendable {
    /// The open attempt of a week task, or a new one (AC-04.03.4, AC-06.01.1).
    func openAttempt(itemSetId: UUID, weekTaskId: UUID?) async throws(APIError) -> PracticeAttempt
    func attempt(id: UUID) async throws(APIError) -> PracticeAttempt
    /// Saves an answer with `If-Match: "version"`; returns the new version.
    func recordAnswer(_ answer: Answer, itemId: UUID, attemptId: UUID, version: Int) async throws(APIError) -> Int
    /// Clears an answer with `If-Match: "version"`; returns the new version.
    func clearAnswer(itemId: UUID, attemptId: UUID, version: Int) async throws(APIError) -> Int
    func heartbeat(_ beat: Heartbeat, attemptId: UUID) async throws(APIError)
    /// Checks the attempt; answers not saved yet go along (AC-06.03.2). The same key returns the same review.
    func submit(attemptId: UUID, idempotencyKey: String, finalAnswers: [UUID: Answer]) async throws(APIError)
        -> AttemptReview
    func review(attemptId: UUID) async throws(APIError) -> AttemptReview
}

/// Calendar dates of the learner (`format: date`), always in their own time zone.
public enum LocalDateFormat {
    public static func formatter(timeZone: TimeZone = .current) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }

    public static func string(from date: Date, timeZone: TimeZone = .current) -> String {
        formatter(timeZone: timeZone).string(from: date)
    }

    public static func date(from string: String?, timeZone: TimeZone = .current) -> Date? {
        string.flatMap { formatter(timeZone: timeZone).date(from: $0) }
    }
}
