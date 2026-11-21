import Foundation
import Observation
import ToeflyCore

/// Home (US-05.02): with a plan, the first tasks of Today, the week progress and the daily goal lead; before the plan
/// exists the diagnostic block stays (AC-02.04.4). Score forecast, streak and weekly time come from
/// assessment-service and analytics-service, which are not built yet, so the forecast honestly shows "Need more data".
@MainActor
@Observable
public final class HomeViewModel {
    public enum LoadState: Equatable {
        case loading
        case loaded
        case failed(APIError)
    }

    public private(set) var loadState: LoadState = .loading
    public private(set) var profile: UserProfile?
    public private(set) var goal: StudyGoal?
    public private(set) var plan: StudyPlanSummary?
    public private(set) var week: WeekProgress?
    public private(set) var today: TodaySummary?
    public private(set) var lastUpdated: Date?

    private let profiles: any ProfileRepository
    private let planning: any PlanningRepository
    private let now: @Sendable () -> Date

    public init(
        profile: UserProfile?,
        profiles: any ProfileRepository,
        planning: any PlanningRepository,
        now: @escaping @Sendable () -> Date = Date.init,
    ) {
        self.profile = profile
        self.profiles = profiles
        self.planning = planning
        self.now = now
    }

    public var greeting: String {
        profile?.firstName.map { "Welcome, \($0)" } ?? "Welcome"
    }

    public var isStudent: Bool {
        profile?.isStudent ?? true
    }

    public var hasPlan: Bool {
        plan != nil
    }

    /// "Take your 25-minute diagnostic" while there is no plan (AC-02.04.4).
    public var showsDiagnostic: Bool {
        loadState == .loaded && isStudent && plan == nil
    }

    /// The first three tasks, overdue ones first (AC-05.02.1).
    public var topTasks: [WeekTask] {
        Array(today?.orderedTasks.prefix(3) ?? [])
    }

    public var todayCaption: String? {
        guard let week = today?.weekNo, let day = today?.dayIndex else { return nil }
        return "Week \(week), day \(day)"
    }

    public var todayMinutesText: String? {
        guard let today, !today.orderedTasks.isEmpty else { return nil }
        return "about \(today.estimatedMinutes) min"
    }

    /// Daily goal ring: active minutes today out of the goal (docs/07-ux-ui.md §5.5).
    public var goalMinutes: (done: Int, goal: Int)? {
        guard let today, let goal else { return nil }
        return (today.doneMinutes, goal.minutesPerDay)
    }

    public var goalHint: String? {
        goal.map { Band.hint($0.overallTarget) }
    }

    public var examText: String {
        guard let date = goal?.examDate ?? plan?.examDate else { return "Exam date not booked" }
        return "Exam on \(date.formatted(.dateTime.day().month(.abbreviated).year()))"
    }

    public var weekTitle: String? {
        week.map { "Week \($0.weekNo) progress" }
    }

    public var nextMockExamText: String? {
        guard let week, week.mockExam else { return nil }
        return "Mock exam this week"
    }

    public func load() async {
        if loadState != .loaded {
            loadState = .loading
        }
        do {
            async let loadedMe = profiles.me()
            async let goal = profiles.studyGoal()
            async let plan = planning.currentPlan()
            let (loadedProfile, loadedGoal, loadedPlan) = try await (loadedMe, goal, plan)
            profile = loadedProfile
            self.goal = loadedGoal
            self.plan = loadedPlan
            if loadedPlan != nil {
                async let today = planning.today()
                async let week = planning.currentWeek()
                (self.today, self.week) = try await (today, week)
            } else {
                today = nil
                week = nil
            }
            lastUpdated = now()
            loadState = .loaded
        } catch {
            loadState = .failed(APIError.from(error))
        }
    }
}
