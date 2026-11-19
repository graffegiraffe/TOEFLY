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
