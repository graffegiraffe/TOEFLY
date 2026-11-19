import Foundation
@testable import ToeflyCore

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
