import Foundation
import OpenAPIRuntime
import OpenAPIURLSession
import PlanningAPI
import ProfileAPI

/// Builds the generated clients over one transport and the shared middlewares.
public struct APIClients: Sendable {
    public let profile: ProfileAPI.Client
    public let planning: PlanningAPI.Client

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
            return TodaySummary(
                weekNo: today.weekNo,
                dayIndex: today.dayIndex,
                overdue: today.overdue.compactMap { PlanningMapping.task($0.task) },
                today: today.today.compactMap { PlanningMapping.task($0.task) },
                vocabularyDue: today.vocabularyReview.dueCount,
                waitingForTutor: today.waitingForTutor,
                estimatedMinutes: today.estimatedMinutes,
                doneMinutes: today.doneMinutes,
            )
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
    static func task(_ dto: PlanningAPI.Components.Schemas.WeekTask) -> TaskSummary? {
        guard let id = UUID(uuidString: dto.taskId),
              let section = WeekSection(rawValue: dto.section.rawValue) else { return nil }
        return TaskSummary(
            id: id,
            title: dto.title,
            section: section,
            status: WeekTaskStatus(rawValue: dto.status.rawValue) ?? .pending,
            estimatedMinutes: dto.estimatedMinutes,
        )
    }
}
