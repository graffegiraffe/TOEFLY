import Foundation
import HTTPTypes
import OpenAPIRuntime
import Testing
@testable import ToeflyCore

/// Records what reaches the transport and answers with the given statuses in order.
actor RecordingNext {
    private(set) var requests: [HTTPRequest] = []
    private var statuses: [Int]
    private let body: String?

    init(statuses: [Int], body: String? = nil) {
        self.statuses = statuses
        self.body = body
    }

    func handle(_ request: HTTPRequest) -> (HTTPResponse, HTTPBody?) {
        requests.append(request)
        let status = statuses.isEmpty ? 200 : statuses.removeFirst()
        return (HTTPResponse(status: .init(code: status)), body.map { HTTPBody($0) })
    }
}

actor RotatingTokens: AccessTokenProviding {
    private(set) var rejections: [String] = []
    func accessToken() async throws(AuthError) -> String {
        "old"
    }

    func refreshAfterRejection(of token: String) async throws(AuthError) -> String {
        rejections.append(token)
        return "new"
    }
}

@Suite("Middlewares")
struct MiddlewareTests {
    let base = URL(string: "http://localhost:8080")!

    @Test("AC-01.05.1 requests carry the bearer token of the session")
    func ac01051RequestsCarry() async throws {
        let next = RecordingNext(statuses: [200])
        _ = try await AuthMiddleware(tokens: StaticTokens()).intercept(
            HTTPRequest(method: .get, scheme: nil, authority: nil, path: "/api/v1/me"), body: nil, baseURL: base,
            operationID: "getMe",
        ) { request, _, _ in await next.handle(request) }
        #expect(await next.requests.first?.headerFields[.authorization] == "Bearer test-token")
    }

    @Test("AC-01.05.5 a 401 renews the token once and repeats the request")
    func ac01055A401() async throws {
        let next = RecordingNext(statuses: [401, 200])
        let tokens = RotatingTokens()
        let (response, _) = try await AuthMiddleware(tokens: tokens).intercept(
            HTTPRequest(method: .get, scheme: nil, authority: nil, path: "/api/v1/me"), body: nil, baseURL: base,
            operationID: "getMe",
        ) { request, _, _ in await next.handle(request) }
        #expect(response.status == .ok)
        #expect(await next.requests.map { $0.headerFields[.authorization] } == ["Bearer old", "Bearer new"])
        #expect(await tokens.rejections == ["old"])
    }

    @Test("A 401 to a request with a body is not repeated (the body stream is single-use)")
    func a401ToARequestWith() async throws {
        let next = RecordingNext(statuses: [401])
        let (response, _) = try await AuthMiddleware(tokens: RotatingTokens()).intercept(
            HTTPRequest(method: .put, scheme: nil, authority: nil, path: "/api/v1/me/onboarding"), body: HTTPBody("{}"),
            baseURL: base,
            operationID: "saveOnboarding",
        ) { request, _, _ in await next.handle(request) }
        #expect(response.status == .unauthorized)
        #expect(await next.requests.count == 1)
    }

    @Test("docs/05 §2.11 platform, version and language headers")
    func docs05211PlatformVersion() async throws {
        let next = RecordingNext(statuses: [200])
        _ = try await ClientInfoMiddleware(version: "0.1.0", language: "ru").intercept(
            HTTPRequest(method: .get, scheme: nil, authority: nil, path: "/today"), body: nil, baseURL: base,
            operationID: "getToday",
        ) { request, _, _ in await next.handle(request) }
        let fields = try #require(await next.requests.first?.headerFields)
        #expect(try fields[#require(HTTPField.Name("X-Client-Platform"))] == "ios")
        #expect(try fields[#require(HTTPField.Name("X-Client-Version"))] == "0.1.0")
        #expect(fields[.acceptLanguage] == "ru")
    }

    @Test("docs/05 §2.6 POST requests get an Idempotency-Key, others do not")
    func docs0526PostRequests() async throws {
        let next = RecordingNext(statuses: [200, 200])
        let middleware = IdempotencyMiddleware(makeKey: { "k1" })
        _ = try await middleware.intercept(
            HTTPRequest(method: .post, scheme: nil, authority: nil, path: "/x"),
            body: nil,
            baseURL: base,
            operationID: "x",
        ) { request, _, _ in await next.handle(request) }
        _ = try await middleware.intercept(
            HTTPRequest(method: .get, scheme: nil, authority: nil, path: "/x"),
            body: nil,
            baseURL: base,
            operationID: "x",
        ) { request, _, _ in await next.handle(request) }
        let name = try #require(HTTPField.Name("Idempotency-Key"))
        #expect(await next.requests.map { $0.headerFields[name] } == ["k1", nil])
    }

    @Test("docs/05 §3 problems become APIError with title and trace id")
    func docs053ProblemsBecomeApierror() async throws {
        let next = RecordingNext(
            statuses: [422],
            body: #"{"title":"Fill every step first","status":422,"traceId":"t-1"}"#,
        )
        await #expect(throws: APIError.server(status: 422, title: "Fill every step first", traceId: "t-1")) {
            _ = try await ProblemMiddleware().intercept(
                HTTPRequest(method: .get, scheme: nil, authority: nil, path: "/x"),
                body: nil,
                baseURL: base,
                operationID: "x",
            ) { request, _, _ in await next.handle(request) }
        }
        let ok = RecordingNext(statuses: [204])
        let (response, _) = try await ProblemMiddleware().intercept(
            HTTPRequest(method: .get, scheme: nil, authority: nil, path: "/x"),
            body: nil,
            baseURL: base,
            operationID: "x",
        ) { request, _, _ in await ok.handle(request) }
        #expect(response.status.code == 204)
    }

    @Test("docs/07 §3.20 error messages for offline, server and client errors")
    func docs07320ErrorMessages() {
        #expect(APIError.from(URLError(.notConnectedToInternet)) == .offline)
        #expect(APIError.from(URLError(.badURL)) == .server(status: 0, title: nil, traceId: nil))
        #expect(APIError.from(AuthError.sessionExpired) == .sessionExpired)
        #expect(APIError.from(AuthError.network) == .offline)
        #expect(APIError.from(CancellationError()) == .server(status: 0, title: nil, traceId: nil))
        #expect(APIError.offline.message.hasPrefix("You're offline"))
        #expect(APIError.server(status: 503, title: "Unavailable", traceId: "t")
            .message == "Something went wrong on our side.")
        #expect(APIError.server(status: 409, title: "Conflict", traceId: "t").message == "Conflict")
        #expect(APIError.server(status: 409, title: nil, traceId: "t").traceId == "t")
        #expect(APIError.sessionExpired.message.contains("Sign in again"))
        #expect(APIError.offline.traceId == nil)
    }
}

@Suite("Repositories over the generated clients")
struct RepositoryTests {
    @Test("AC-01.01.5 the profile of the signed-in user")
    func ac01015TheProfile() async throws {
        let fixture = fixtureRepositories(.newStudent)
        let profiles = fixture.profiles
        let profile = try await profiles.me()
        #expect(profile.email == "maria.petrova@example.com")
        #expect(profile.roles == [.student])
        #expect(profile.onboardingStatus == .notStarted)
    }

    @Test("AC-02.04.2 saving steps fills them and moves the next step")
    func ac02042SavingSteps() async throws {
        let fixture = fixtureRepositories(.newStudent)
        let profiles = fixture.profiles
        #expect(try await profiles.onboarding().nextStep == .goal)
        let afterGoal = try await profiles.saveOnboarding(OnboardingUpdate(sectionTargets: SectionTargets(
            reading: 5,
            listening: 5,
            writing: 4,
            speaking: 4,
        )))
        #expect(afterGoal.sectionTargets?.reading == 5)
        #expect(afterGoal.nextStep == .examDate)
        let afterDate = try await profiles.saveOnboarding(OnboardingUpdate(examBooked: true, examDate: day(60)))
        #expect(afterDate.examDate == day(60))
        #expect(afterDate.status == .inProgress)
        await #expect(throws: APIError.server(status: 422, title: "Fill every step first", traceId: "fixture-trace")) {
            _ = try await profiles.saveOnboarding(OnboardingUpdate(complete: true))
        }
    }

    @Test("Consents are recorded from iOS and read back")
    func consentsAreRecordedFromIosAnd() async throws {
        let fixture = fixtureRepositories(.newStudent)
        let profiles = fixture.profiles
        let backend = fixture.backend
        try await profiles.recordConsent(.aiProcessing, granted: false)
        #expect(try await profiles.consents() == [.aiProcessing: false])
        #expect(await backend.savedRequests == ["recordConsent:AI_PROCESSING=false"])
    }

    @Test("AC-02.04.4 no goal and no plan before the onboarding is done")
    func ac02044NoGoal() async throws {
        let fixture = fixtureRepositories(.newStudent)
        let profiles = fixture.profiles
        let planning = fixture.planning
        #expect(try await profiles.studyGoal() == nil)
        #expect(try await planning.currentPlan() == nil)
        await #expect(throws: APIError.self) { _ = try await planning.currentWeek() }
    }

    @Test("US-05.02 plan, current week and today of a student with a plan")
    func us0502PlanCurrentWeek() async throws {
        let fixture = fixtureRepositories(.withPlan)
        let profiles = fixture.profiles
        let planning = fixture.planning
        #expect(try await profiles.studyGoal() == StudyGoal(
            overallTarget: 5,
            examBooked: false,
            examDate: nil,
            minutesPerDay: 45,
        ))
        #expect(try await planning.currentPlan() == StudyPlanSummary(
            weeksCount: 8,
            currentWeekNo: 3,
            examDate: nil,
            daysToExam: nil,
        ))
        #expect(try await planning.currentWeek() == WeekProgress(weekNo: 3, progressPercent: 46.6, mockExam: false))
        let today = try await planning.today()
        #expect(today.weekNo == 3)
        #expect(today.orderedTasks.map(\.section) == [.reading, .listening, .speaking, .writing])
        #expect(today.orderedTasks.first?.status == .pending)
        #expect(today.vocabularyDue == 12)
    }

    @Test("Mapping drops tasks with unknown ids")
    func mappingDropsTasksWithUnknownIds() {
        #expect(PlanningMapping.task(.init(
            taskId: "not-a-uuid",
            weekId: "w",
            kind: .itemSet,
            section: .reading,
            dayIndex: 1,
            ordinal: 1,
            status: .pending,
            estimatedMinutes: 3,
            title: "t",
        )) == nil)
    }
}
