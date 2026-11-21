import Foundation
import OpenAPIRuntime
import OpenAPIURLSession
import PlanningAPI
import PracticeAPI
import ProfileAPI

/// Builds the generated clients over one transport and the shared middlewares.
public struct APIClients: Sendable {
    public let profile: ProfileAPI.Client
    public let planning: PlanningAPI.Client
    public let practice: PracticeAPI.Client

    public init(configuration: AppConfiguration, transport: any ClientTransport, tokens: any AccessTokenProviding) {
        // Order: client info and idempotency first, then auth (which may repeat the request), then problems.
        let middlewares: [any ClientMiddleware] = [
            ClientInfoMiddleware(version: configuration.clientVersion),
            IdempotencyMiddleware(),
            AuthMiddleware(tokens: tokens),
            ProblemMiddleware(),
        ]
        profile = ProfileAPI.Client(serverURL: configuration.apiBaseURL, transport: transport, middlewares: middlewares)
        planning = PlanningAPI.Client(serverURL: configuration.apiV1URL, transport: transport, middlewares: middlewares)
        // The practice-service contract writes `/api/v1` into its paths, like profile-service.
        practice = PracticeAPI.Client(
            serverURL: configuration.apiBaseURL,
            transport: transport,
            middlewares: middlewares,
        )
    }

    public static func urlSessionTransport() -> URLSessionTransport {
        URLSessionTransport(configuration: .init(session: .ephemeralNoCache))
    }
}

public struct LiveProfileRepository: ProfileRepository {
    private let client: ProfileAPI.Client
    private let timeZone: @Sendable () -> TimeZone

    public init(client: ProfileAPI.Client, timeZone: @escaping @Sendable () -> TimeZone = { .current }) {
        self.client = client
        self.timeZone = timeZone
    }

    public func me() async throws(APIError) -> UserProfile {
        try await call {
            guard case let .ok(ok) = try await client.getMe() else { throw APIError.unexpected }
            return try ProfileMapping.profile(ok.body.json)
        }
    }

    public func onboarding() async throws(APIError) -> OnboardingState {
        try await call {
            guard case let .ok(ok) = try await client.getOnboarding() else { throw APIError.unexpected }
            return try ProfileMapping.onboarding(ok.body.json, timeZone: timeZone())
        }
    }

    public func saveOnboarding(_ update: OnboardingUpdate) async throws(APIError) -> OnboardingState {
        try await call {
            let body = ProfileMapping.request(update, timeZone: timeZone())
            guard case let .ok(ok) = try await client.saveOnboarding(body: .json(body))
            else { throw APIError.unexpected }
            return try ProfileMapping.onboarding(ok.body.json, timeZone: timeZone())
        }
    }

    public func consents() async throws(APIError) -> [ConsentType: Bool] {
        try await call {
            guard case let .ok(ok) = try await client.listConsents() else { throw APIError.unexpected }
            var result: [ConsentType: Bool] = [:]
            for consent in try ok.body.json.items {
                if let type = ConsentType(rawValue: consent.consentType.rawValue) {
                    result[type] = consent.granted
                }
            }
            return result
        }
    }

    public func recordConsent(_ type: ConsentType, granted: Bool) async throws(APIError) {
        try await call {
            guard let apiType = ProfileAPI.Components.Schemas.ConsentType(rawValue: type.rawValue)
            else { throw APIError.unexpected }
            let output = try await client.recordConsent(
                path: .init(consentType: apiType),
                body: .json(.init(granted: granted, source: .ios)),
            )
            guard case .ok = output else { throw APIError.unexpected }
        }
    }

    public func studyGoal() async throws(APIError) -> StudyGoal? {
        do {
            return try await call {
                guard case let .ok(ok) = try await client.getStudyGoal() else { throw APIError.unexpected }
                let goal = try ok.body.json
                return StudyGoal(
                    overallTarget: goal.overallTarget,
                    examBooked: goal.examBooked,
                    examDate: LocalDateFormat.date(from: goal.examDate, timeZone: timeZone()),
                    minutesPerDay: goal.minutesPerDay,
                )
            }
        } catch .server(status: 404, _, _) {
            return nil
        }
    }
}

public struct LivePlanningRepository: PlanningRepository {
    private let client: PlanningAPI.Client
    private let timeZone: @Sendable () -> TimeZone

    public init(client: PlanningAPI.Client, timeZone: @escaping @Sendable () -> TimeZone = { .current }) {
        self.client = client
        self.timeZone = timeZone
    }

    public func currentPlan() async throws(APIError) -> StudyPlanSummary? {
        do {
            return try await call {
                guard case let .ok(ok) = try await client.getCurrentStudyPlan() else { throw APIError.unexpected }
                let plan = try ok.body.json
                return StudyPlanSummary(
                    weeksCount: plan.weeksCount,
                    currentWeekNo: plan.currentWeekNo,
                    examDate: LocalDateFormat.date(from: plan.examDate, timeZone: timeZone()),
                    daysToExam: plan.daysToExam,
                )
            }
        } catch .server(status: 404, _, _) {
            return nil
        }
    }

    public func currentWeek() async throws(APIError) -> WeekProgress? {
        try await call {
            guard case let .ok(ok) = try await client.listCurrentPlanWeeks() else { throw APIError.unexpected }
            let weeks = try ok.body.json.items
            let current = weeks.first { $0.status == .inProgress } ?? weeks.first { $0.status == .published }
            return current.map { WeekProgress(
                weekNo: $0.weekNo,
                progressPercent: $0.progressPercent ?? 0,
                mockExam: $0.mockExam,
            ) }
        }
    }

    public func today() async throws(APIError) -> TodaySummary {
        try await call {
            guard case let .ok(ok) = try await client.getToday() else { throw APIError.unexpected }
            let today = try ok.body.json
            let zone = timeZone()
            let map = { (entry: PlanningAPI.Components.Schemas.TodayTask) in
                PlanningMapping.task(entry.task, timeZone: zone).map { task in
                    var task = task
                    task.overdueDays = entry.overdueDays
                    return task
                }
            }
            return TodaySummary(
                weekId: today.weekId.flatMap(UUID.init(uuidString:)),
                weekNo: today.weekNo,
                dayIndex: today.dayIndex,
                date: LocalDateFormat.date(from: today.date, timeZone: zone),
                overdue: today.overdue.compactMap(map),
                today: today.today.compactMap(map),
                getAhead: (today.getAhead ?? []).compactMap(map),
                vocabularyDue: today.vocabularyReview.dueCount,
                waitingForTutor: today.waitingForTutor,
                estimatedMinutes: today.estimatedMinutes,
                doneMinutes: today.doneMinutes,
            )
        }
    }

    public func weeks() async throws(APIError) -> [WeekSummary] {
        try await call {
            guard case let .ok(ok) = try await client.listCurrentPlanWeeks() else { throw APIError.unexpected }
            let zone = timeZone()
            return try ok.body.json.items.compactMap { dto in
                guard let id = UUID(uuidString: dto.weekId),
                      let status = WeekStatus(rawValue: dto.status.rawValue),
                      let start = LocalDateFormat.date(from: dto.startDate, timeZone: zone),
                      let end = LocalDateFormat.date(from: dto.endDate, timeZone: zone) else { return nil }
                return WeekSummary(
                    id: id,
                    weekNo: dto.weekNo,
                    status: status,
                    startDate: start,
                    endDate: end,
                    progressPercent: dto.progressPercent,
                    mockExam: dto.mockExam,
                    locked: dto.locked ?? false,
                )
            }.sorted { $0.weekNo < $1.weekNo }
        }
    }

    public func week(id: UUID) async throws(APIError) -> Week {
        try await call {
            let output = try await client.getWeek(path: .init(weekId: id.uuidString.lowercased()))
            guard case let .ok(ok) = output else { throw APIError.unexpected }
            return try PlanningMapping.week(ok.body.json, timeZone: timeZone())
        }
    }
}

extension APIError {
    static let unexpected = APIError.server(status: 0, title: nil, traceId: nil)
}

/// Runs a generated client call and converts every failure to `APIError`.
func call<T>(_ body: () async throws -> T) async throws(APIError) -> T {
    do {
        return try await body()
    } catch {
        throw APIError.from(error)
    }
}

enum ProfileMapping {
    static func profile(_ dto: ProfileAPI.Components.Schemas.UserProfile) throws -> UserProfile {
        guard let id = UUID(uuidString: dto.id) else { throw APIError.unexpected }
        return UserProfile(
            id: id,
            email: dto.email,
            displayName: dto.displayName,
            roles: dto.roles.compactMap { Role(rawValue: $0.rawValue) },
            onboardingStatus: OnboardingStatus(rawValue: dto.onboardingStatus.rawValue) ?? .notStarted,
        )
    }

    static func onboarding(
        _ dto: ProfileAPI.Components.Schemas.OnboardingState,
        timeZone: TimeZone,
    ) -> OnboardingState {
        OnboardingState(
            status: OnboardingStatus(rawValue: dto.status.rawValue) ?? .notStarted,
            nextStep: dto.nextStep.flatMap { OnboardingState.DataStep(rawValue: $0.rawValue) },
            filledSteps: dto.filledSteps.compactMap { OnboardingState.DataStep(rawValue: $0.rawValue) },
            overallTarget: dto.overallTarget,
            sectionTargets: dto.sectionTargets.map {
                SectionTargets(reading: $0.reading, listening: $0.listening, writing: $0.writing, speaking: $0.speaking)
            },
            examBooked: dto.examBooked,
            examDate: LocalDateFormat.date(from: dto.examDate, timeZone: timeZone),
            minutesPerDay: dto.minutesPerDay,
            preferredStudyTime: dto.preferredStudyTime,
            timezone: dto.timezone,
            explanationLanguage: ExplanationLanguage(rawValue: dto.explanationLanguage.rawValue) ?? .en,
        )
    }

    static func request(_ update: OnboardingUpdate, timeZone: TimeZone) -> ProfileAPI.Components.Schemas
        .OnboardingRequest {
        ProfileAPI.Components.Schemas.OnboardingRequest(
            overallTarget: update.overallTarget,
            sectionTargets: update.sectionTargets.map {
                .init(reading: $0.reading, listening: $0.listening, writing: $0.writing, speaking: $0.speaking)
            },
            examBooked: update.examBooked,
            examDate: update.examDate.map { LocalDateFormat.string(from: $0, timeZone: timeZone) },
            minutesPerDay: update.minutesPerDay,
            preferredStudyTime: update.preferredStudyTime,
            timezone: update.timezone,
            explanationLanguage: update.explanationLanguage
                .flatMap { ProfileAPI.Components.Schemas.ExplanationLanguage(rawValue: $0.rawValue) },
            complete: update.complete ? true : nil,
        )
    }
}

enum PlanningMapping {
    static func task(_ dto: PlanningAPI.Components.Schemas.WeekTask, timeZone _: TimeZone = .current) -> WeekTask? {
        guard let id = UUID(uuidString: dto.taskId),
              let section = WeekSection(rawValue: dto.section.rawValue) else { return nil }
        return WeekTask(
            id: id,
            weekId: UUID(uuidString: dto.weekId),
            title: dto.title,
            section: section,
            kind: WeekTaskKind(rawValue: dto.kind.rawValue) ?? .itemSet,
            taskType: dto.taskType.flatMap { TaskType(rawValue: $0.rawValue) },
            status: WeekTaskStatus(rawValue: dto.status.rawValue) ?? .pending,
            skippedReason: dto.skippedReason.flatMap { WeekTaskSkipReason(rawValue: $0.rawValue) },
            dayIndex: dto.dayIndex,
            ordinal: dto.ordinal,
            estimatedMinutes: dto.estimatedMinutes,
            provenance: dto.provenance.flatMap { Provenance(rawValue: $0.rawValue) },
            legacy: dto.legacy ?? false,
            itemSetId: dto.itemSetId.flatMap(UUID.init(uuidString:)),
            lastAttemptId: dto.lastAttemptId.flatMap(UUID.init(uuidString:)),
            resourceURL: dto.resourceUrl.flatMap(URL.init(string:)),
        )
    }

    static func week(_ dto: PlanningAPI.Components.Schemas.Week, timeZone: TimeZone) throws -> Week {
        guard let id = UUID(uuidString: dto.weekId),
              let start = LocalDateFormat.date(from: dto.startDate, timeZone: timeZone),
              let end = LocalDateFormat.date(from: dto.endDate, timeZone: timeZone) else { throw APIError.unexpected }
        return Week(
            id: id,
            weekNo: dto.weekNo,
            status: WeekStatus(rawValue: dto.status.rawValue) ?? .published,
            startDate: start,
            endDate: end,
            dayCount: dto.dayCount,
            currentDayIndex: dto.currentDayIndex,
            progressPercent: dto.progressPercent,
            mockExam: dto.mockExam ?? false,
            rationale: dto.rationale,
            rebalancedTaskIds: (dto.rebalance?.excludedTaskIds ?? []).compactMap(UUID.init(uuidString:)),
            tasks: dto.sections.flatMap(\.tasks).compactMap { task($0, timeZone: timeZone) },
            days: dto.days.compactMap { day in
                LocalDateFormat.date(from: day.date, timeZone: timeZone).map {
                    DayPlan(
                        dayIndex: day.dayIndex,
                        date: $0,
                        estimatedMinutes: day.estimatedMinutes,
                        taskIds: day.taskIds.compactMap(UUID.init(uuidString:)),
                    )
                }
            },
        )
    }
}
