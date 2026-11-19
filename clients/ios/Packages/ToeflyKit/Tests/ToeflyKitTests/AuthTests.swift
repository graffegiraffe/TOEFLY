import Foundation
import Testing
@testable import ToeflyCore

@Suite("Authorization request")
struct AuthorizationRequestTests {
    let oidc = AppConfiguration.local.oidc

    private func query(_ url: URL) -> [String: String] {
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        return Dictionary(uniqueKeysWithValues: items.map { ($0.name, $0.value ?? "") })
    }

    @Test("AC-01.05.1 code flow with PKCE S256, state, nonce and offline_access")
    func ac01051CodeFlow() {
        let request = AuthorizationRequest(configuration: oidc, method: .email)
        let items = query(request.url)
        #expect(request.url.absoluteString
            .hasPrefix("http://localhost:8180/realms/toefly/protocol/openid-connect/auth?"))
        #expect(items["client_id"] == "toefly-ios")
        #expect(items["redirect_uri"] == "app.toefly://oauth/callback")
        #expect(items["response_type"] == "code")
        #expect(items["code_challenge_method"] == "S256")
        #expect(items["code_challenge"] == request.pkce.challenge)
        #expect(items["state"] == request.state)
        #expect(items["nonce"] == request.nonce)
        #expect(items["scope"] == "openid profile email offline_access")
        #expect(items["kc_idp_hint"] == nil)
        #expect(items["prompt"] == nil)
    }

    @Test("AC-01.03.1 Continue with Google goes straight to the Google broker")
    func ac01031ContinueWith() {
        #expect(query(AuthorizationRequest(configuration: oidc, method: .google).url)["kc_idp_hint"] == "google")
    }

    @Test("AC-01.04.1 Sign in with Apple goes through the Keycloak broker")
    func ac01041SignIn() {
        #expect(query(AuthorizationRequest(configuration: oidc, method: .apple).url)["kc_idp_hint"] == "apple")
    }

    @Test("AC-01.01.1 Get started opens the registration page")
    func ac01011GetStarted() {
        #expect(query(AuthorizationRequest(configuration: oidc, method: .signUp).url)["prompt"] == "create")
    }

    @Test("RFC 7636 appendix B: the S256 challenge of the reference verifier")
    func rfc7636AppendixBTheS256() {
        let pkce = PKCE(verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        #expect(pkce.challenge == "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
        let random = PKCE.random()
        #expect(random.verifier.count == 43)
        #expect(random.verifier != PKCE.random().verifier)
    }

    @Test("The callback with the matching state yields the code")
    func theCallbackWithTheMatchingState() throws {
        let request = AuthorizationRequest(configuration: oidc, method: .email, state: "s1")
        let code = try request
            .authorizationCode(from: #require(URL(string: "app.toefly://oauth/callback?code=abc&state=s1")))
        #expect(code == "abc")
    }

    @Test("A forged state or a missing code is rejected")
    func aForgedStateOrAMissing() throws {
        let request = AuthorizationRequest(configuration: oidc, method: .email, state: "s1")
        #expect(throws: AuthError.stateMismatch) {
            try request
                .authorizationCode(from: #require(URL(string: "app.toefly://oauth/callback?code=abc&state=evil")))
        }
        #expect(throws: AuthError.missingCode) {
            try request.authorizationCode(from: #require(URL(string: "app.toefly://oauth/callback?state=s1")))
        }
    }

    @Test("AC-01.03.4 cancelling at the provider is a silent cancellation")
    func ac01034CancellingAt() throws {
        let request = AuthorizationRequest(configuration: oidc, method: .google, state: "s1")
        #expect(throws: AuthError.cancelled) {
            try request
                .authorizationCode(
                    from: #require(URL(string: "app.toefly://oauth/callback?error=access_denied&state=s1")),
                )
        }
        #expect(throws: AuthError.provider("server_error")) {
            try request.authorizationCode(from: #require(URL(string: "app.toefly://oauth/callback?error=server_error")))
        }
    }
}

/// Answers HTTP requests from a closure and records them.
final class StubLoader: HTTPDataLoading, @unchecked Sendable {
    private let lock = NSLock()
    private var recorded: [URLRequest] = []
    private let handler: @Sendable (URLRequest) throws -> (Int, String)

    init(_ handler: @escaping @Sendable (URLRequest) throws -> (Int, String)) {
        self.handler = handler
    }

    var requests: [URLRequest] {
        lock.withLock { recorded }
    }

    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        lock.withLock { recorded.append(request) }
        let (status, body) = try handler(request)
        guard let url = request.url, let response = HTTPURLResponse(
            url: url,
            statusCode: status,
            httpVersion: nil,
            headerFields: nil,
        )
        else { throw URLError(.badURL) }
        return (Data(body.utf8), response)
    }
}

@Suite("Token endpoint")
struct TokenEndpointTests {
    let tokenJSON = #"{"access_token":"a1","refresh_token":"r1","expires_in":900,"token_type":"Bearer"}"#

    private func form(_ request: URLRequest) -> [String: String] {
        let body = String(data: request.httpBody ?? Data(), encoding: .utf8) ?? ""
        return Dictionary(uniqueKeysWithValues: body.split(separator: "&").map {
            let pair = $0.split(separator: "=", maxSplits: 1).map(String.init)
            return (pair[0], pair[1].removingPercentEncoding ?? "")
        })
    }

    @Test("AC-01.05.1 the code is exchanged with the PKCE verifier")
    func ac01051TheCode() async throws {
        let json = tokenJSON
        let loader = StubLoader { _ in (200, json) }
        let endpoint = TokenEndpoint(configuration: AppConfiguration.local.oidc, loader: loader, clock: { testNow })
        let tokens = try await endpoint.exchange(code: "c1", verifier: "v1")
        #expect(tokens == TokenSet(
            accessToken: "a1",
            refreshToken: "r1",
            accessTokenExpiresAt: testNow.addingTimeInterval(900),
        ))
        let request = try #require(loader.requests.first)
        #expect(request.url == AppConfiguration.local.oidc.tokenEndpoint)
        #expect(request.httpMethod == "POST")
        #expect(form(request) == [
            "grant_type": "authorization_code",
            "code": "c1",
            "code_verifier": "v1",
            "redirect_uri": "app.toefly://oauth/callback",
            "client_id": "toefly-ios",
        ])
    }

    @Test("AC-01.05.5 a rejected refresh token ends the session")
    func ac01055ARejected() async {
        let endpoint = TokenEndpoint(
            configuration: AppConfiguration.local.oidc,
            loader: StubLoader { _ in (400, #"{"error":"invalid_grant"}"#) },
        )
        await #expect(throws: AuthError.sessionExpired) { try await endpoint.refresh("r1") }
    }

    @Test("A server error or a response without a refresh token is not a session end")
    func aServerErrorOrAResponse() async {
        let failing = TokenEndpoint(configuration: AppConfiguration.local.oidc, loader: StubLoader { _ in (503, "") })
        await #expect(throws: AuthError.tokenEndpoint(status: 503)) { try await failing.refresh("r1") }
        let noRefresh = TokenEndpoint(
            configuration: AppConfiguration.local.oidc,
            loader: StubLoader { _ in (200, #"{"access_token":"a","expires_in":60}"#) },
        )
        await #expect(throws: AuthError.tokenEndpoint(status: 200)) { try await noRefresh.exchange(
            code: "c",
            verifier: "v",
        ) }
        let offline = TokenEndpoint(
            configuration: AppConfiguration.local.oidc,
            loader: StubLoader { _ in throw URLError(.notConnectedToInternet) },
        )
        await #expect(throws: AuthError.network) { try await offline.refresh("r1") }
    }

    @Test("AC-01.05.3 sign-out revokes the refresh token")
    func ac01053SignOut() async throws {
        let loader = StubLoader { _ in (200, "") }
        await TokenEndpoint(configuration: AppConfiguration.local.oidc, loader: loader).revoke("r1")
        let request = try #require(loader.requests.first)
        #expect(request.url == AppConfiguration.local.oidc.revocationEndpoint)
        #expect(form(request) == ["token": "r1", "token_type_hint": "refresh_token", "client_id": "toefly-ios"])
    }

    @Test("Form values are percent-encoded")
    func formValuesArePercentEncoded() {
        #expect(TokenEndpoint
            .formEncoded(["b": "x y", "a": "app.toefly://oauth/callback"]) ==
            "a=app.toefly%3A%2F%2Foauth%2Fcallback&b=x%20y")
    }
}

/// Token service that counts refreshes and can be slowed down to test single-flight.
actor CountingTokens: TokenService {
    private(set) var refreshes = 0
    private(set) var revoked: [String] = []
    var failRefresh = false
    var delay: Duration = .zero

    func setFailRefresh(_ value: Bool) {
        failRefresh = value
    }

    func setDelay(_ value: Duration) {
        delay = value
    }

    func exchange(code _: String, verifier _: String) async throws(AuthError) -> TokenSet {
        TokenSet(accessToken: "a0", refreshToken: "r0", accessTokenExpiresAt: testNow.addingTimeInterval(900))
    }

    func refresh(_: String) async throws(AuthError) -> TokenSet {
        refreshes += 1
        let number = refreshes
        try? await Task.sleep(for: delay)
        if failRefresh {
            throw .sessionExpired
        }
        return TokenSet(
            accessToken: "a\(number)",
            refreshToken: "r\(number)",
            accessTokenExpiresAt: testNow.addingTimeInterval(900),
        )
    }

    func revoke(_ refreshToken: String) async {
        revoked.append(refreshToken)
    }
}

@Suite("Session manager")
struct SessionManagerTests {
    private func manager(
        tokens: CountingTokens = CountingTokens(),
        store: InMemoryRefreshTokenStore = InMemoryRefreshTokenStore(),
        web: any WebAuthenticating = FixtureWebAuthenticator(outcome: .success),
        clock: @escaping @Sendable () -> Date = { testNow },
    ) -> SessionManager {
        SessionManager(configuration: AppConfiguration.local.oidc, web: web, tokens: tokens, store: store, clock: clock)
    }

    @Test("AC-01.05.1 signing in keeps the refresh token in the store and the access token in memory")
    func ac01051SigningIn() async throws {
        let store = InMemoryRefreshTokenStore()
        let session = manager(store: store)
        try await session.signIn(with: .email)
        #expect(store.load() == "r0")
        #expect(try await session.accessToken() == "a0")
        #expect(await session.hasStoredSession)
    }

    @Test("AC-01.03.4 a cancelled web session signs nobody in")
    func ac01034ACancelled() async {
        let store = InMemoryRefreshTokenStore()
        let session = manager(store: store, web: FixtureWebAuthenticator(outcome: .cancel))
        await #expect(throws: AuthError.cancelled) { try await session.signIn(with: .google) }
        #expect(store.load() == nil)
    }

    @Test("docs/10 §4.3 concurrent requests share one refresh (single-flight)")
    func docs1043ConcurrentRequests() async throws {
        let tokens = CountingTokens()
        await tokens.setDelay(.milliseconds(50))
        let session = manager(tokens: tokens, store: InMemoryRefreshTokenStore(token: "stored"))
        async let first = session.accessToken()
        async let second = session.accessToken()
        async let third = session.accessToken()
        let results = try await [first, second, third]
        #expect(Set(results) == ["a1"])
        #expect(await tokens.refreshes == 1)
    }

    @Test("AC-01.05.5 an expired access token is renewed transparently")
    func ac01055AnExpired() async throws {
        let tokens = CountingTokens()
        let session = manager(tokens: tokens)
        try await session.signIn(with: .email)
        let later = manager(
            tokens: tokens,
            store: InMemoryRefreshTokenStore(token: "r0"),
            clock: { testNow.addingTimeInterval(2000) },
        )
        #expect(try await later.accessToken() == "a1")
        #expect(try await session.refreshAfterRejection(of: "a0") == "a2")
        #expect(try await session.refreshAfterRejection(of: "a0") == "a2") // already renewed by another request
    }

    @Test("AC-01.05.5 a failed refresh clears the session and tells the app")
    func ac01055AFailed() async {
        let tokens = CountingTokens()
        await tokens.setFailRefresh(true)
        let store = InMemoryRefreshTokenStore(token: "stored")
        let session = manager(tokens: tokens, store: store)
        let flag = Flag()
        await session.onSessionExpired { await flag.set() }
        await #expect(throws: AuthError.sessionExpired) { try await session.accessToken() }
        #expect(store.load() == nil)
        #expect(await flag.value)
    }

    @Test("Restore at launch: no stored token, a valid one, a revoked one")
    func restoreAtLaunchNoStoredToken() async {
        #expect(await manager().restore() == false)
        #expect(await manager(store: InMemoryRefreshTokenStore(token: "stored")).restore())
        let failing = CountingTokens()
        await failing.setFailRefresh(true)
        let flag = Flag()
        let session = manager(tokens: failing, store: InMemoryRefreshTokenStore(token: "stored"))
        await session.onSessionExpired { await flag.set() }
        #expect(await session.restore() == false)
        #expect(await flag.value == false) // a cold start shows the welcome screen without the expiry notice
    }

    @Test("AC-01.05.3 sign-out revokes the refresh token and forgets every token")
    func ac01053SignOut() async throws {
        let tokens = CountingTokens()
        let store = InMemoryRefreshTokenStore()
        let session = manager(tokens: tokens, store: store)
        try await session.signIn(with: .email)
        await session.signOut()
        #expect(await tokens.revoked == ["r0"])
        #expect(store.load() == nil)
        #expect(await session.hasStoredSession == false)
        await #expect(throws: AuthError.sessionExpired) { try await session.accessToken() }
    }

    @Test("Fixture token service issues, refreshes and revokes")
    func fixtureTokenServiceIssuesRefreshesAnd() async throws {
        let service = FixtureTokenService()
        let first = try await service.exchange(code: "c", verifier: "v")
        #expect(first.accessToken == "access-1")
        #expect(try await service.refresh(first.refreshToken).refreshToken == "refresh-2")
        service.setRefreshFails(true)
        await #expect(throws: AuthError.sessionExpired) { try await service.refresh("x") }
        await service.revoke("refresh-2")
        #expect(service.revoked == ["refresh-2"])
        let once = FixtureTokenService(failingRefreshes: [2], accessLifetime: 0)
        _ = try await once.refresh("r")
        await #expect(throws: AuthError.sessionExpired) { try await once.refresh("r") }
        #expect(try await once.refresh("r").accessTokenExpiresAt <= Date())
    }

    @Test("Keychain store round trip (docs/10 §4.3)")
    func keychainStoreRoundTripDocs10() throws {
        let store = KeychainRefreshTokenStore(account: "test-\(UUID().uuidString)")
        defer { store.delete() }
        do {
            try store.save("one")
        } catch let error as KeychainError {
            // A test runner without a keychain (CI on macOS without a login keychain) cannot store items.
            withKnownIssue("No keychain in this environment: \(error.status)") { throw error }
            return
        }
        try store.save("two")
        #expect(store.load() == "two")
        store.delete()
        #expect(store.load() == nil)
    }
}

actor Flag {
    private(set) var value = false
    func set() {
        value = true
    }
}
