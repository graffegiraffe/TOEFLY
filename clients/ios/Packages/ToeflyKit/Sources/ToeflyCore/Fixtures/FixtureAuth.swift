import Foundation

/// Stands in for Keycloak in UI tests: answers the web authentication session like the real callback does.
public struct FixtureWebAuthenticator: WebAuthenticating {
    public enum Outcome: String, Sendable {
        /// The person signs in and Keycloak redirects back with a code.
        case success
        /// The person closes the sheet or cancels at Google (AC-01.03.4).
        case cancel
    }

    private let outcome: Outcome

    public init(outcome: Outcome) {
        self.outcome = outcome
    }

    public func authenticate(url: URL, callbackScheme: String) async throws(AuthError) -> URL {
        guard outcome == .success else { throw .cancelled }
        let state = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "state" }?
            .value ?? ""
        var callback = URLComponents()
        callback.scheme = callbackScheme
        callback.host = "oauth"
        callback.path = "/callback"
        callback.queryItems = [
            URLQueryItem(name: "code", value: "fixture-code"),
            URLQueryItem(name: "state", value: state),
        ]
        guard let result = callback.url else { throw .missingCode }
        return result
    }
}

/// Token endpoint of the fixtures. `failRefresh` rejects every refresh; `failingRefreshes` rejects only the refreshes
/// with these numbers (1-based), so a session can be restored and then revoked (AC-01.05.5).
public final class FixtureTokenService: TokenService, @unchecked Sendable {
    private let lock = NSLock()
    private var refreshFails: Bool
    private let failingRefreshes: Set<Int>
    private let accessLifetime: TimeInterval
    private var counter = 0
    private var refreshes = 0
    public private(set) var revoked: [String] = []

    public init(failRefresh: Bool = false, failingRefreshes: Set<Int> = [], accessLifetime: TimeInterval = 900) {
        refreshFails = failRefresh
        self.failingRefreshes = failingRefreshes
        self.accessLifetime = accessLifetime
    }

    public func setRefreshFails(_ fails: Bool) {
        lock.withLock { refreshFails = fails }
    }

    public func exchange(code _: String, verifier _: String) async throws(AuthError) -> TokenSet {
        issue()
    }

    public func refresh(_: String) async throws(AuthError) -> TokenSet {
        let fails = lock.withLock {
            refreshes += 1
            return refreshFails || failingRefreshes.contains(refreshes)
        }
        if fails {
            throw .sessionExpired
        }
        return issue()
    }

    public func revoke(_ refreshToken: String) async {
        lock.withLock { revoked.append(refreshToken) }
    }

    private func issue() -> TokenSet {
        let number = lock.withLock {
            counter += 1
            return counter
        }
        return TokenSet(
            accessToken: "access-\(number)",
            refreshToken: "refresh-\(number)",
            accessTokenExpiresAt: Date().addingTimeInterval(accessLifetime),
        )
    }
}
