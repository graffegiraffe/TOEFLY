import Foundation

/// Opens the Keycloak pages and returns the callback URL; `ASWebAuthenticationSession` in the app.
public protocol WebAuthenticating: Sendable {
    func authenticate(url: URL, callbackScheme: String) async throws(AuthError) -> URL
}

/// Exchanges codes and refreshes tokens; `TokenEndpoint` in the app, a fake in tests.
public protocol TokenService: Sendable {
    func exchange(code: String, verifier: String) async throws(AuthError) -> TokenSet
    func refresh(_ refreshToken: String) async throws(AuthError) -> TokenSet
    func revoke(_ refreshToken: String) async
}

extension TokenEndpoint: TokenService {}

/// Single owner of the tokens. Refreshes are single-flight: concurrent requests wait for one exchange, because
/// Keycloak treats a second use of a rotated refresh token as theft and ends the session
/// (`TokenRefresher` in docs/10-security-privacy.md §4.3).
public actor SessionManager {
    private let configuration: OIDCConfiguration
    private let web: any WebAuthenticating
    private let tokens: any TokenService
    private let store: any RefreshTokenStore
    private let clock: @Sendable () -> Date

    private var current: TokenSet?
    private var refreshing: Task<TokenSet, any Error>?
    private var expiryHandler: (@Sendable () async -> Void)?

    public init(
        configuration: OIDCConfiguration,
        web: any WebAuthenticating,
        tokens: any TokenService,
        store: any RefreshTokenStore,
        clock: @escaping @Sendable () -> Date = Date.init,
    ) {
        self.configuration = configuration
        self.web = web
        self.tokens = tokens
        self.store = store
        self.clock = clock
    }

    /// Called once when the session cannot be renewed, so the app returns to sign-in (AC-01.05.5).
    public func onSessionExpired(_ handler: @escaping @Sendable () async -> Void) {
        expiryHandler = handler
    }

    public var hasStoredSession: Bool {
        current != nil || store.load() != nil
    }

    /// Runs the authorization code flow with PKCE in a web authentication session.
    public func signIn(with method: SignInMethod) async throws(AuthError) {
        let request = AuthorizationRequest(configuration: configuration, method: method)
        let callback = try await web.authenticate(url: request.url, callbackScheme: configuration.callbackScheme)
        let code = try request.authorizationCode(from: callback)
        let set = try await tokens.exchange(code: code, verifier: request.pkce.verifier)
        try keep(set)
    }

    /// A valid access token, refreshed when it is about to expire.
    public func accessToken() async throws(AuthError) -> String {
        if let current, current.isAccessTokenFresh(now: clock()) {
            return current.accessToken
        }
        return try await refreshed().accessToken
    }

    /// Forces a refresh after the gateway answered 401 to a token that looked fresh.
    public func refreshAfterRejection(of token: String) async throws(AuthError) -> String {
        if let current, current.accessToken != token, current.isAccessTokenFresh(now: clock()) {
            return current.accessToken // another request already renewed it
        }
        current = current.map { TokenSet(
            accessToken: $0.accessToken,
            refreshToken: $0.refreshToken,
            accessTokenExpiresAt: .distantPast,
        ) }
        return try await refreshed().accessToken
    }

    /// Restores the session at launch; false when there is nothing to restore or the refresh token is no longer valid.
    public func restore() async -> Bool {
        guard store.load() != nil else { return false }
        do {
            _ = try await refreshed(notifyExpiry: false)
            return true
        } catch {
            return false
        }
    }

    /// Revokes the refresh token and forgets every local token (AC-01.05.3).
    public func signOut() async {
        let token = current?.refreshToken ?? store.load()
        refreshing?.cancel()
        refreshing = nil
        current = nil
        store.delete()
        if let token {
            await tokens.revoke(token)
        }
    }

    private func refreshed(notifyExpiry: Bool = true) async throws(AuthError) -> TokenSet {
        if let refreshing {
            return try await Self.value(of: refreshing)
        }
        guard let refreshToken = current?.refreshToken ?? store.load() else {
            try await expire(notify: notifyExpiry)
        }
        let tokens = tokens
        let task = Task<TokenSet, any Error> { try await tokens.refresh(refreshToken) }
        refreshing = task
        defer { refreshing = nil }
        do {
            let set = try await Self.value(of: task)
            try keep(set)
            return set
        } catch .sessionExpired {
            try await expire(notify: notifyExpiry)
        }
    }

    private func expire(notify: Bool) async throws(AuthError) -> Never {
        current = nil
        store.delete()
        if notify, let expiryHandler {
            await expiryHandler()
        }
        throw .sessionExpired
    }

    private func keep(_ set: TokenSet) throws(AuthError) {
        current = set
        do {
            try store.save(set.refreshToken)
        } catch {
            throw .provider("keychain")
        }
    }

    private static func value(of task: Task<TokenSet, any Error>) async throws(AuthError) -> TokenSet {
        do {
            return try await task.value
        } catch let error as AuthError {
            throw error
        } catch {
            throw .network
        }
    }
}
