import Foundation
import ToeflyCore
import ToeflyFeatures
import UserNotifications

/// Composition root. The live app talks to Keycloak and api-gateway; `-fixtures <scenario>` (UI tests, demos without
/// the stack) swaps the transport and the identity provider for in-memory fakes, keeping the generated clients.
@MainActor
enum AppEnvironment {
    static func makeAppModel(
        arguments: [String] = ProcessInfo.processInfo.arguments,
        info: [String: Any] = Bundle.main.infoDictionary ?? [:],
    ) -> AppModel {
        var configuration = AppConfiguration(infoDictionary: info)
        if arguments.contains("-appleSignInEnabled") {
            configuration.appleSignInEnabled = true
        }
        if let scenario = value(after: "-fixtures", in: arguments).flatMap(FixtureScenario.init(rawValue:)) {
            return fixtureModel(scenario: scenario, configuration: configuration, arguments: arguments)
        }
        let tokens = TokenEndpoint(configuration: configuration.oidc, loader: URLSessionDataLoader())
        let session = SessionManager(
            configuration: configuration.oidc,
            web: WebAuthenticationSessionAuthenticator(),
            tokens: tokens,
            store: KeychainRefreshTokenStore(),
        )
        let clients = APIClients(
            configuration: configuration,
            transport: APIClients.urlSessionTransport(),
            tokens: session,
        )
        return AppModel(
            configuration: configuration,
            session: session,
            profiles: LiveProfileRepository(client: clients.profile),
            planning: LivePlanningRepository(client: clients.planning),
            practice: LivePracticeRepository(client: clients.practice),
            pendingAnswers: FilePendingAnswerStore(),
            notifications: SystemNotificationPermission(),
        )
    }

    private static func fixtureModel(
        scenario: FixtureScenario,
        configuration: AppConfiguration,
        arguments: [String],
    ) -> AppModel {
        let outcome = value(after: "-authOutcome", in: arguments)
            .flatMap(FixtureWebAuthenticator.Outcome.init(rawValue:)) ?? .success
        // `-expireSession` lets the first refresh succeed (restore) and rejects the second (AC-01.05.5).
        let tokens = FixtureTokenService(
            failRefresh: false,
            failingRefreshes: arguments.contains("-expireSession") ? [2] : [],
            accessLifetime: arguments.contains("-expireSession") ? 0 : 900,
        )
        let store = InMemoryRefreshTokenStore(token: arguments.contains("-signedIn") ? "fixture-refresh" : nil)
        let session = SessionManager(
            configuration: configuration.oidc,
            web: FixtureWebAuthenticator(outcome: outcome),
            tokens: tokens,
            store: store,
        )
        let backend = FixtureBackend(scenario: scenario)
        let clients = APIClients(
            configuration: configuration,
            transport: FixtureTransport(backend: backend),
            tokens: session,
        )
        return AppModel(
            configuration: configuration,
            session: session,
            profiles: LiveProfileRepository(client: clients.profile),
            planning: LivePlanningRepository(client: clients.planning),
            practice: LivePracticeRepository(client: clients.practice),
        )
    }

    private static func value(after flag: String, in arguments: [String]) -> String? {
        guard let index = arguments.firstIndex(of: flag), arguments.indices.contains(index + 1) else { return nil }
        return arguments[index + 1]
    }
}

/// Asks iOS for alert and badge permission once the reminder time is set (docs/07-ux-ui.md §5.3).
struct SystemNotificationPermission: NotificationPermissionRequesting {
    func requestAuthorization() async {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])
    }
}
