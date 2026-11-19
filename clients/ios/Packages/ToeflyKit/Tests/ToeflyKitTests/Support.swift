import Foundation
@testable import ToeflyCore
@testable import ToeflyFeatures

/// Fixed "today" of the tests: Thursday, 19 November 2026, 10:00 in Minsk.
let testNow = Date(timeIntervalSince1970: 1_795_071_600)

let minsk = TimeZone(identifier: "Europe/Minsk") ?? .gmt

var testCalendar: Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = minsk
    return calendar
}

func day(_ offset: Int) -> Date {
    testCalendar.date(byAdding: .day, value: offset, to: testCalendar.startOfDay(for: testNow)) ?? testNow
}

struct StaticTokens: AccessTokenProviding {
    func accessToken() async throws(AuthError) -> String {
        "test-token"
    }

    func refreshAfterRejection(of _: String) async throws(AuthError) -> String {
        "test-token-2"
    }
}

/// Repositories over the fixture backend, through the generated clients and the real middlewares.
struct FixtureRepositories {
    let profiles: LiveProfileRepository
    let planning: LivePlanningRepository
    let backend: FixtureBackend
}

func fixtureRepositories(_ scenario: FixtureScenario) -> FixtureRepositories {
    let backend = FixtureBackend(scenario: scenario)
    let clients = APIClients(
        configuration: .local,
        transport: FixtureTransport(backend: backend),
        tokens: StaticTokens(),
    )
    let zone: @Sendable () -> TimeZone = { minsk }
    return FixtureRepositories(
        profiles: LiveProfileRepository(client: clients.profile, timeZone: zone),
        planning: LivePlanningRepository(client: clients.planning, timeZone: zone),
        backend: backend,
    )
}

/// Session controller that records calls; `signInResult` decides what the web session returns.
actor FakeSession: SessionControlling {
    var signInResult: AuthError?
    var restoreResult = false
    private(set) var signedInWith: [SignInMethod] = []
    private(set) var signOutCount = 0
    private var expiryHandler: (@Sendable () async -> Void)?

    init(signInResult: AuthError? = nil, restoreResult: Bool = false) {
        self.signInResult = signInResult
        self.restoreResult = restoreResult
    }

    func signIn(with method: SignInMethod) async throws(AuthError) {
        signedInWith.append(method)
        if let signInResult {
            throw signInResult
        }
    }

    func restore() async -> Bool {
        restoreResult
    }

    func signOut() async {
        signOutCount += 1
    }

    func onSessionExpired(_ handler: @escaping @Sendable () async -> Void) async {
        expiryHandler = handler
    }

    func expire() async {
        await expiryHandler?()
    }
}

/// Profile repository that fails every call with the given error.
struct FailingProfiles: ProfileRepository {
    let error: APIError
    func me() async throws(APIError) -> UserProfile {
        throw error
    }

    func onboarding() async throws(APIError) -> OnboardingState {
        throw error
    }

    func saveOnboarding(_: OnboardingUpdate) async throws(APIError) -> OnboardingState {
        throw error
    }

    func consents() async throws(APIError) -> [ConsentType: Bool] {
        throw error
    }

    func recordConsent(_: ConsentType, granted _: Bool) async throws(APIError) {
        throw error
    }

    func studyGoal() async throws(APIError) -> StudyGoal? {
        throw error
    }
}

final class RecordingNotifications: NotificationPermissionRequesting, @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0
    var requests: Int {
        lock.withLock { count }
    }

    func requestAuthorization() async {
        lock.withLock { count += 1 }
    }
}
