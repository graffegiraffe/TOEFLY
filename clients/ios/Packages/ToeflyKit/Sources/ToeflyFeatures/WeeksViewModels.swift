import Foundation
import Observation
import ToeflyCore

/// What a tap on a task opens (AC-05.01.4): the player for a closed item set, the review of a done one, the link of an
/// external resource; the rest arrives with later sub-phases.
public enum TaskDestination: Equatable, Sendable {
    case player
    case review(attemptId: UUID)
    case external(URL)
    case later(String)

    public static func of(_ task: WeekTask) -> TaskDestination {
        switch task.kind {
        case .itemSet:
            guard PlayerLayout.of(task.taskType) != nil, task.itemSetId != nil else {
                let what = task
                    .section == .speaking ? "Speaking tasks" :
                    (task.section == .writing ? "Writing tasks" : "This task")
                return .later("\(what) open in the next update of TOEFLY.")
            }
            if task.status == .done, let attemptId = task.lastAttemptId {
                return .review(attemptId: attemptId)
            }
            return .player
        case .externalResource:
            return task.resourceURL.map(TaskDestination.external) ?? .later("This link is not available.")
        case .grammarUnit, .vocabularyUnit:
            return .later("Grammar and vocabulary units open in the next update of TOEFLY.")
        case .vocabularyReview:
            return .later("Vocabulary review opens in the next update of TOEFLY.")
        case .mockExam:
            return .later("Mock exams open in the next update of TOEFLY.")
        case .tutorMaterial:
            return .later("Materials from your tutor open in the next update of TOEFLY.")
        }
    }
}

/// Dates of the English UI (the web formats them with `en-US` too), in the learner's time zone.
enum DateText {
    static let locale = Locale(identifier: "en_US")

    static func style(_ timeZone: TimeZone = .current) -> Date.FormatStyle {
        Date.FormatStyle(locale: locale, timeZone: timeZone)
    }

    static func shortDay(_ date: Date, timeZone: TimeZone = .current) -> String {
        date.formatted(style(timeZone).month(.abbreviated).day())
    }

    static func weekday(_ date: Date, wide: Bool, timeZone: TimeZone = .current) -> String {
        date.formatted(style(timeZone).weekday(wide ? .wide : .abbreviated))
    }

    /// "Nov 16 – Nov 22, 2026" (docs/07-ux-ui.md §5.7).
    static func range(_ start: Date, _ end: Date, year: Bool = true) -> String {
        let tail = year ? ", \(end.formatted(style().year()))" : ""
        return "\(shortDay(start)) – \(shortDay(end))\(tail)"
    }
}

/// The Weeks gallery (US-04.09, docs/07-ux-ui.md §5.6): the current week first, then the upcoming ones, then the
/// completed ones folded.
@MainActor
@Observable
public final class WeeksViewModel {
    public enum LoadState: Equatable {
        case loading
        case loaded
        /// No plan yet (AC-02.04.4).
        case noPlan
        case failed(APIError)
    }

    public enum Badge: Equatable, Sendable {
        case current, done, upcoming, mockExam, locked
    }

    public struct Card: Equatable, Identifiable, Sendable {
        public var id: UUID {
            week.id
        }

        public let week: WeekSummary
        public let title: String
        public let dates: String
        public let badges: [Badge]
        /// The line for a week that cannot be opened yet.
        public let note: String?
        public let coverId: String

        public var progress: Double? {
            week.isOpenable ? week.progressPercent : nil
        }
    }

    public private(set) var loadState: LoadState = .loading
    public private(set) var plan: StudyPlanSummary?
    public private(set) var weeks: [WeekSummary] = []
    public var showsCompleted = false

    private let planning: any PlanningRepository

    public init(planning: any PlanningRepository) {
        self.planning = planning
    }

    public func load() async {
        if loadState != .loaded {
            loadState = .loading
        }
        do {
            guard let plan = try await planning.currentPlan() else {
                loadState = .noPlan
                return
            }
            self.plan = plan
            weeks = try await planning.weeks()
            loadState = .loaded
        } catch {
            loadState = .failed(error)
        }
    }

    /// "8-week plan · exam on Dec 27".
    public var subtitle: String {
        guard let plan else { return "" }
        let exam = plan.examDate.map { " · exam on \(DateText.shortDay($0))" } ?? ""
        return "\(plan.weeksCount)-week plan\(exam)"
    }

    private var currentNo: Int? {
        plan?.currentWeekNo
            ?? weeks.first { $0.status == .inProgress }?.weekNo
            ?? weeks.first { $0.status == .published }?.weekNo
    }

    public var current: Card? {
        weeks.first { $0.weekNo == currentNo }.map(card)
    }

    public var upcoming: [Card] {
        weeks.filter { $0.weekNo > (currentNo ?? 0) && $0.status != .completed }.map(card)
    }

    public var completed: [Card] {
        weeks.filter { $0.weekNo != currentNo && ($0.status == .completed || $0.weekNo < (currentNo ?? 0)) }
            .sorted { $0.weekNo > $1.weekNo }.map(card)
    }

    private func card(_ week: WeekSummary) -> Card {
        var badges: [Badge] = []
        if week.weekNo == currentNo {
            badges.append(.current)
        } else if week.status == .completed {
            badges.append(.done)
        } else {
            badges.append(.upcoming)
        }
        if week.mockExam {
            badges.append(.mockExam)
        }
        if week.locked {
            badges.append(.locked)
        }
        let note: String? = switch week.status {
        case _ where week.locked: "Upgrade to Pro to continue your plan"
        case .awaitingTutorApproval: "Your tutor is reviewing this week"
        case .draft: "Will be built from your progress"
        default: nil
        }
        return Card(
            week: week,
            title: "Week \(week.weekNo)",
            dates: DateText.range(week.startDate, week.endDate, year: false),
            badges: badges,
            note: note,
            coverId: Covers.week(week.weekNo, mockExam: week.mockExam),
        )
    }
}

/// The week page and its section pages (US-04.02, docs/07-ux-ui.md §5.7–5.8).
@MainActor
@Observable
public final class WeekViewModel {
    public enum LoadState: Equatable {
        case loading
        case loaded
        case failed(APIError)
    }

    public struct SectionCard: Equatable, Identifiable, Sendable {
        public var id: WeekSection {
            section
        }

        public let section: WeekSection
        public let done: Int
        public let total: Int
        public let coverId: String

        public var caption: String {
            total == 0 ? "Nothing this week" : "\(done) of \(total) done"
        }

        /// Ticked when every counted task is done or skipped (§3.2 `SectionCard`).
        public var isComplete: Bool {
            total > 0 && done == total
        }
    }

    public let weekId: UUID
    public private(set) var loadState: LoadState = .loading
    public private(set) var week: Week?

    private let planning: any PlanningRepository
    private let calendar: Calendar

    public init(weekId: UUID, planning: any PlanningRepository, calendar: Calendar = .current) {
        self.weekId = weekId
        self.planning = planning
        self.calendar = calendar
    }

    public func load() async {
        if loadState != .loaded {
            loadState = .loading
        }
        do {
            week = try await planning.week(id: weekId)
            loadState = .loaded
        } catch {
            loadState = .failed(error)
        }
    }

    public var title: String {
        week.map { "Week \($0.weekNo)" } ?? "Week"
    }

    public var dates: String {
        guard let week else { return "" }
        return DateText.range(week.startDate, week.endDate)
    }

    /// Shown for weeks that are not in progress (§5.7 "Status").
    public var statusText: String? {
        let zone = calendar.timeZone
        return switch week?.status {
        case .completed: "Completed"
        case .awaitingTutorApproval: "Awaiting tutor approval"
        case .published: "Starts \(DateText.weekday(week?.startDate ?? Date(), wide: true, timeZone: zone))"
        default: nil
        }
    }

    public var isClosed: Bool {
        week?.status == .completed
    }

    /// Tasks of the current day, in order.
    public var todayTasks: [WeekTask] {
        guard let week, let day = week.currentDayIndex else { return [] }
        return week.tasks.filter { $0.dayIndex == day }.sorted { $0.ordinal < $1.ordinal }
    }

    /// The Today strip: the first open task of the day (§5.7).
    public var nextTask: WeekTask? {
        todayTasks.first { $0.status.isOpen }
    }

    /// "Reading · about 3 min · 4 tasks today".
    public var nextTaskCaption: String? {
        guard let task = nextTask else { return nil }
        let count = todayTasks.count
        let tasks = count == 1 ? "1 task" : "\(count) tasks"
        return "\(task.section.label) · about \(task.estimatedMinutes) min · \(tasks) today"
    }

    /// The weekly review is written after the last day (US-18.01, phase 12c).
    public var weeklyReviewText: String {
        guard let week else { return "" }
        return "Available on \(DateText.shortDay(week.endDate, timeZone: calendar.timeZone))"
    }

    /// Groups of the week table on iPhone: one per section with tasks (§5.7 `xs`).
    public var groups: [(section: WeekSection, tasks: [WeekTask])] {
        guard let week else { return [] }
        return WeekSection.allCases.compactMap { section in
            let tasks = week.tasks(in: section)
            return tasks.isEmpty ? nil : (section, tasks)
        }
    }

    public var sectionCards: [SectionCard] {
        WeekSection.cards.map(card)
    }

    public func card(_ section: WeekSection) -> SectionCard {
        let tasks = week?.tasks(in: section).filter(\.countsInProgress) ?? []
        return SectionCard(
            section: section,
            done: tasks.count(where: { $0.status == .done || $0.status == .skipped }),
            total: tasks.count,
            coverId: Covers.section(section),
        )
    }

    /// AC-04.05.2: the banner and the list of the tasks a rebalance left out.
    public var rebalancedTasks: [WeekTask] {
        guard let week else { return [] }
        return week.tasks.filter { week.rebalancedTaskIds.contains($0.id) }
    }

    public var coverId: String {
        week.map { Covers.week($0.weekNo, mockExam: $0.mockExam) } ?? Covers.weeks[0]
    }

    /// "Thu" for a task of day 4.
    public func dayLabel(_ task: WeekTask) -> String {
        guard let week else { return "Day \(task.dayIndex)" }
        return DateText.weekday(
            week.date(ofDay: task.dayIndex, calendar: calendar),
            wide: false,
            timeZone: calendar.timeZone,
        )
    }

    /// Section page: the tasks by day with the weekday as the header ("THURSDAY").
    public func days(of section: WeekSection) -> [(title: String, tasks: [WeekTask])] {
        guard let week else { return [] }
        let tasks = week.tasks(in: section)
        return Dictionary(grouping: tasks, by: \.dayIndex).keys.sorted().map { day in
            let date = week.date(ofDay: day, calendar: calendar)
            let title = "\(DateText.weekday(date, wide: true, timeZone: calendar.timeZone)) · day \(day)"
            return (title, tasks.filter { $0.dayIndex == day })
        }
    }

    /// "Reading · Week 3".
    public func sectionTitle(_ section: WeekSection) -> String {
        "\(section.label) · \(title)"
    }
}

/// Today (US-05.01, docs/07-ux-ui.md §5.5).
@MainActor
@Observable
public final class TodayViewModel {
    public enum LoadState: Equatable {
        case loading
        case loaded
        case noPlan
        case failed(APIError)
    }

    public private(set) var loadState: LoadState = .loading
    public private(set) var today: TodaySummary?
    public private(set) var lastUpdated: Date?

    private let planning: any PlanningRepository
    private let now: @Sendable () -> Date

    public init(planning: any PlanningRepository, now: @escaping @Sendable () -> Date = Date.init) {
        self.planning = planning
        self.now = now
    }

    public func load() async {
        if loadState != .loaded {
            loadState = .loading
        }
        do {
            guard try await planning.currentPlan() != nil else {
                loadState = .noPlan
                return
            }
            today = try await planning.today()
            lastUpdated = now()
            loadState = .loaded
        } catch {
            loadState = .failed(error)
        }
    }

    /// "Thursday, Nov 19 · Week 3, day 4".
    public var caption: String {
        let date = (today?.date ?? now()).formatted(DateText.style().weekday(.wide).month(.abbreviated).day())
        guard let week = today?.weekNo, let day = today?.dayIndex else { return date }
        return "\(date) · Week \(week), day \(day)"
    }

    public var overdue: [WeekTask] {
        today?.overdue ?? []
    }

    public var tasks: [WeekTask] {
        today?.today ?? []
    }

    public var getAhead: [WeekTask] {
        today?.getAhead ?? []
    }

    /// AC-05.01.2: everything of the day is done.
    public var isDayDone: Bool {
        guard let today else { return false }
        return !today.orderedTasks.isEmpty && !today.orderedTasks.contains { $0.status.isOpen }
    }

    /// "Today's tasks · about 24 min" (AC-05.01.1).
    public var tasksTitle: String {
        guard let minutes = today?.estimatedMinutes, minutes > 0 else { return "Today's tasks" }
        return "Today's tasks · about \(minutes) min"
    }

    public var vocabularyText: String? {
        guard let due = today?.vocabularyDue, due > 0 else { return nil }
        return "\(due) word\(due == 1 ? "" : "s") to review"
    }

    /// AC-05.01.3.
    public var waitingText: String? {
        guard today?.waitingForTutor == true else { return nil }
        let next = today?.weekNo.map { " \($0 + 1)" } ?? ""
        return "Your tutor is preparing Week\(next)"
    }

    /// "Start" begins the first unfinished task that opens on iOS now (§5.5).
    public var startTask: WeekTask? {
        (today?.orderedTasks ?? []).first {
            $0.status.isOpen && TaskDestination.of($0) == .player
        }
    }
}
