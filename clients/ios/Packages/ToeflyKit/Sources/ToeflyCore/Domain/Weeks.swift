import Foundation

/// Canonical `WeekSection` (docs/00-README.md) in the order of the week table (docs/07-ux-ui.md §5.7).
public enum WeekSection: String, Sendable, Equatable, Hashable, CaseIterable {
    case reading = "READING", listening = "LISTENING", writing = "WRITING", speaking = "SPEAKING"
    case vocabulary = "VOCABULARY", grammar = "GRAMMAR", resources = "RESOURCES"

    public var label: String {
        rawValue.capitalized
    }

    /// The six cards of the week gallery; resources stay a group of the table only (§5.7).
    public static let cards: [WeekSection] = [.reading, .listening, .writing, .speaking, .vocabulary, .grammar]
}

public enum WeekStatus: String, Sendable, Equatable {
    case draft = "DRAFT", awaitingTutorApproval = "AWAITING_TUTOR_APPROVAL", published = "PUBLISHED"
    case inProgress = "IN_PROGRESS", completed = "COMPLETED"
}

public enum WeekTaskStatus: String, Sendable, Equatable {
    case pending = "PENDING", inProgress = "IN_PROGRESS", done = "DONE", skipped = "SKIPPED"

    public var isOpen: Bool {
        self == .pending || self == .inProgress
    }
}

public enum WeekTaskKind: String, Sendable, Equatable {
    case itemSet = "ITEM_SET", grammarUnit = "GRAMMAR_UNIT", vocabularyUnit = "VOCABULARY_UNIT"
    case vocabularyReview = "VOCABULARY_REVIEW", tutorMaterial = "TUTOR_MATERIAL"
    case externalResource = "EXTERNAL_RESOURCE", mockExam = "MOCK_EXAM"
}

/// Canonical `WeekTaskSkipReason`; `TUTOR`, `REBALANCE` and `NO_ENTITLEMENT` leave the progress denominator
/// (AC-04.02.3).
public enum WeekTaskSkipReason: String, Sendable, Equatable {
    case userSkipped = "USER_SKIPPED", rebalance = "REBALANCE", tutor = "TUTOR", weekClose = "WEEK_CLOSE"
    case noEntitlement = "NO_ENTITLEMENT"

    public var leavesProgress: Bool {
        self == .rebalance || self == .tutor || self == .noEntitlement
    }
}

/// Where a task comes from (AC-04.02.2).
public enum Provenance: String, Sendable, Equatable {
    case extracted = "EXTRACTED", generated = "GENERATED", tutorCreated = "TUTOR_CREATED"

    public var label: String {
        switch self {
        case .extracted: "From book"
        case .generated: "AI-generated"
        case .tutorCreated: "From your tutor"
        }
    }
}

/// Canonical `TaskType` (docs/00-README.md) with the titles of the web player.
public enum TaskType: String, Sendable, Equatable, CaseIterable {
    case completeTheWords = "COMPLETE_THE_WORDS", readDailyLife = "READ_DAILY_LIFE"
    case readAcademicPassage = "READ_ACADEMIC_PASSAGE", listenChooseResponse = "LISTEN_CHOOSE_RESPONSE"
    case listenConversation = "LISTEN_CONVERSATION", listenAnnouncement = "LISTEN_ANNOUNCEMENT"
    case listenAcademicTalk = "LISTEN_ACADEMIC_TALK", buildASentence = "BUILD_A_SENTENCE"
    case writeEmail = "WRITE_EMAIL", writeAcademicDiscussion = "WRITE_ACADEMIC_DISCUSSION"
    case listenAndRepeat = "LISTEN_AND_REPEAT", takeAnInterview = "TAKE_AN_INTERVIEW"
    case grammarExercise = "GRAMMAR_EXERCISE", vocabExercise = "VOCAB_EXERCISE"
    case legacyReading = "LEGACY_READING", legacyListening = "LEGACY_LISTENING"

    public var label: String {
        switch self {
        case .completeTheWords: "Complete the Words"
        case .readDailyLife: "Read in Daily Life"
        case .readAcademicPassage: "Read an Academic Passage"
        case .listenChooseResponse: "Listen and Choose a Response"
        case .listenConversation: "Listen to a Conversation"
        case .listenAnnouncement: "Listen to an Announcement"
        case .listenAcademicTalk: "Listen to an Academic Talk"
        case .buildASentence: "Build a Sentence"
        case .writeEmail: "Write an Email"
        case .writeAcademicDiscussion: "Academic Discussion"
        case .listenAndRepeat: "Listen and Repeat"
        case .takeAnInterview: "Take an Interview"
        case .grammarExercise: "Grammar exercise"
        case .vocabExercise: "Vocabulary exercise"
        case .legacyReading: "Reading (old format)"
        case .legacyListening: "Listening (old format)"
        }
    }

    public var isListening: Bool {
        [.listenChooseResponse, .listenConversation, .listenAnnouncement, .listenAcademicTalk].contains(self)
    }
}

/// A task of a week (planning-service `WeekTask`); also a row of Today.
public struct WeekTask: Sendable, Equatable, Identifiable, Hashable {
    public var id: UUID
    public var weekId: UUID?
    public var title: String
    public var section: WeekSection
    public var kind: WeekTaskKind
    public var taskType: TaskType?
    public var status: WeekTaskStatus
    public var skippedReason: WeekTaskSkipReason?
    public var dayIndex: Int
    public var ordinal: Int
    public var estimatedMinutes: Int
    public var provenance: Provenance?
    public var legacy: Bool
    public var itemSetId: UUID?
    public var lastAttemptId: UUID?
    public var resourceURL: URL?
    /// Days behind for a task of the Overdue group (AC-04.04.1).
    public var overdueDays: Int?

    public init(
        id: UUID,
        weekId: UUID? = nil,
        title: String,
        section: WeekSection,
        kind: WeekTaskKind = .itemSet,
        taskType: TaskType? = nil,
        status: WeekTaskStatus,
        skippedReason: WeekTaskSkipReason? = nil,
        dayIndex: Int = 1,
        ordinal: Int = 1,
        estimatedMinutes: Int,
        provenance: Provenance? = nil,
        legacy: Bool = false,
        itemSetId: UUID? = nil,
        lastAttemptId: UUID? = nil,
        resourceURL: URL? = nil,
        overdueDays: Int? = nil,
    ) {
        self.id = id
        self.weekId = weekId
        self.title = title
        self.section = section
        self.kind = kind
        self.taskType = taskType
        self.status = status
        self.skippedReason = skippedReason
        self.dayIndex = dayIndex
        self.ordinal = ordinal
        self.estimatedMinutes = estimatedMinutes
        self.provenance = provenance
        self.legacy = legacy
        self.itemSetId = itemSetId
        self.lastAttemptId = lastAttemptId
        self.resourceURL = resourceURL
        self.overdueDays = overdueDays
    }

    /// Counted in the week progress (AC-04.02.3).
    public var countsInProgress: Bool {
        !(status == .skipped && skippedReason?.leavesProgress == true)
    }
}

/// A card of the Weeks gallery (planning-service `WeekSummary`).
public struct WeekSummary: Sendable, Equatable, Identifiable, Hashable {
    public var id: UUID
    public var weekNo: Int
    public var status: WeekStatus
    public var startDate: Date
    public var endDate: Date
    public var progressPercent: Double?
    public var mockExam: Bool
    public var locked: Bool

    public init(
        id: UUID,
        weekNo: Int,
        status: WeekStatus,
        startDate: Date,
        endDate: Date,
        progressPercent: Double?,
        mockExam: Bool,
        locked: Bool = false,
    ) {
        self.id = id
        self.weekNo = weekNo
        self.status = status
        self.startDate = startDate
        self.endDate = endDate
        self.progressPercent = progressPercent
        self.mockExam = mockExam
        self.locked = locked
    }

    /// Weeks in `DRAFT` or `AWAITING_TUTOR_APPROVAL` are not composed for the student yet (AC-04.02.4).
    public var isOpenable: Bool {
        !locked && status != .draft && status != .awaitingTutorApproval
    }
}

public struct DayPlan: Sendable, Equatable, Hashable {
    public var dayIndex: Int
    public var date: Date
    public var estimatedMinutes: Int
    public var taskIds: [UUID]

    public init(dayIndex: Int, date: Date, estimatedMinutes: Int, taskIds: [UUID]) {
        self.dayIndex = dayIndex
        self.date = date
        self.estimatedMinutes = estimatedMinutes
        self.taskIds = taskIds
    }
}

/// The week page (planning-service `Week`).
public struct Week: Sendable, Equatable, Identifiable {
    public var id: UUID
    public var weekNo: Int
    public var status: WeekStatus
    public var startDate: Date
    public var endDate: Date
    public var dayCount: Int
    public var currentDayIndex: Int?
    public var progressPercent: Double
    public var mockExam: Bool
    public var rationale: String?
    /// Tasks excluded by the last rebalance (AC-04.05.2); empty when the week was never rebalanced.
    public var rebalancedTaskIds: [UUID]
    public var tasks: [WeekTask]
    public var days: [DayPlan]

    public init(
        id: UUID,
        weekNo: Int,
        status: WeekStatus,
        startDate: Date,
        endDate: Date,
        dayCount: Int = 7,
        currentDayIndex: Int?,
        progressPercent: Double,
        mockExam: Bool = false,
        rationale: String? = nil,
        rebalancedTaskIds: [UUID] = [],
        tasks: [WeekTask],
        days: [DayPlan] = [],
    ) {
        self.id = id
        self.weekNo = weekNo
        self.status = status
        self.startDate = startDate
        self.endDate = endDate
        self.dayCount = dayCount
        self.currentDayIndex = currentDayIndex
        self.progressPercent = progressPercent
        self.mockExam = mockExam
        self.rationale = rationale
        self.rebalancedTaskIds = rebalancedTaskIds
        self.tasks = tasks
        self.days = days
    }

    /// Tasks of one section in the order of the days.
    public func tasks(in section: WeekSection) -> [WeekTask] {
        tasks.filter { $0.section == section }.sorted { ($0.dayIndex, $0.ordinal) < ($1.dayIndex, $1.ordinal) }
    }

    /// Calendar date of a day of the week (Day 1 = `startDate`).
    public func date(ofDay dayIndex: Int, calendar: Calendar = .current) -> Date {
        days.first { $0.dayIndex == dayIndex }?.date
            ?? calendar.date(byAdding: .day, value: dayIndex - 1, to: startDate) ?? startDate
    }
}

/// Today (planning-service `Today`, US-05.01).
public struct TodaySummary: Sendable, Equatable {
    public var weekId: UUID?
    public var weekNo: Int?
    public var dayIndex: Int?
    public var date: Date?
    public var overdue: [WeekTask]
    public var today: [WeekTask]
    /// Up to two tasks of the next day once the day is done (AC-05.01.2).
    public var getAhead: [WeekTask]
    public var vocabularyDue: Int
    public var waitingForTutor: Bool
    public var estimatedMinutes: Int
    public var doneMinutes: Int

    public init(
        weekId: UUID? = nil,
        weekNo: Int?,
        dayIndex: Int?,
        date: Date? = nil,
        overdue: [WeekTask],
        today: [WeekTask],
        getAhead: [WeekTask] = [],
        vocabularyDue: Int,
        waitingForTutor: Bool,
        estimatedMinutes: Int,
        doneMinutes: Int,
    ) {
        self.weekId = weekId
        self.weekNo = weekNo
        self.dayIndex = dayIndex
        self.date = date
        self.overdue = overdue
        self.today = today
        self.getAhead = getAhead
        self.vocabularyDue = vocabularyDue
        self.waitingForTutor = waitingForTutor
        self.estimatedMinutes = estimatedMinutes
        self.doneMinutes = doneMinutes
    }

    /// Overdue tasks first, then the tasks of the day (AC-05.01.1).
    public var orderedTasks: [WeekTask] {
        overdue + today
    }
}

/// Covers of weeks and sections (docs/07-ux-ui.md §8.2): the exam desk for mock exam weeks, a rotation of the
/// other fifteen otherwise; the same rule as the web.
public enum Covers {
    public static let weeks = [
        "week-01-campus", "week-02-library", "week-03-lecture-hall", "week-04-exam-desk", "week-05-headphones",
        "week-06-microphone", "week-07-open-books", "week-08-letter", "week-09-globe", "week-10-laboratory",
        "week-11-quad", "week-12-dormitory", "week-13-museum", "week-14-observatory", "week-15-cafeteria",
        "week-16-graduation",
    ]

    public static func week(_ weekNo: Int, mockExam: Bool) -> String {
        if mockExam {
            return "week-04-exam-desk"
        }
        var index = (max(weekNo, 1) - 1) % weeks.count
        if index == 3 {
            index = 4
        }
        return weeks[index]
    }

    public static func section(_ section: WeekSection) -> String {
        "section-\(section.rawValue.lowercased())"
    }
}
