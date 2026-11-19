import Foundation

/// Tokens of one session. The access token lives only in memory; the refresh token goes to the Keychain
/// (docs/10-security-privacy.md §4.3).
public struct TokenSet: Sendable, Equatable {
    public var accessToken: String
    public var refreshToken: String
    public var accessTokenExpiresAt: Date

    public init(accessToken: String, refreshToken: String, accessTokenExpiresAt: Date) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.accessTokenExpiresAt = accessTokenExpiresAt
    }

    /// The token is renewed 30 s before it expires, so a request never leaves with an almost expired token.
    public func isAccessTokenFresh(now: Date) -> Bool {
        accessTokenExpiresAt.timeIntervalSince(now) > 30
    }
}

/// Sends one HTTP request; `URLSession` in the app, a fake in tests.
public protocol HTTPDataLoading: Sendable {
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse)
}

public struct URLSessionDataLoader: HTTPDataLoading {
    private let session: URLSession

    public init(session: URLSession = .ephemeralNoCache) {
        self.session = session
    }

    public func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        return (data, http)
    }
}

public extension URLSession {
    /// No disk cache for tokens and personal data (docs/10-security-privacy.md §4.3).
    static let ephemeralNoCache: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        return URLSession(configuration: configuration)
    }()
}

/// Token, refresh and revocation calls to Keycloak for the public client `toefly-ios`.
public struct TokenEndpoint: Sendable {
    private let configuration: OIDCConfiguration
    private let loader: any HTTPDataLoading
    private let clock: @Sendable () -> Date

    public init(
        configuration: OIDCConfiguration,
        loader: any HTTPDataLoading,
        clock: @escaping @Sendable () -> Date = Date.init,
    ) {
        self.configuration = configuration
        self.loader = loader
        self.clock = clock
    }

    public func exchange(code: String, verifier: String) async throws(AuthError) -> TokenSet {
        try await tokens([
            "grant_type": "authorization_code",
            "code": code,
            "code_verifier": verifier,
            "redirect_uri": configuration.redirectURI.absoluteString,
            "client_id": configuration.clientID,
        ])
    }

    public func refresh(_ refreshToken: String) async throws(AuthError) -> TokenSet {
        do {
            return try await tokens([
                "grant_type": "refresh_token",
                "refresh_token": refreshToken,
                "client_id": configuration.clientID,
            ])
        } catch .tokenEndpoint(status: 400), .tokenEndpoint(status: 401) {
            // invalid_grant: the token expired, was revoked or was already used.
            throw .sessionExpired
        }
    }

    /// Revokes the refresh token at sign-out (AC-01.05.3); a failure does not keep the person signed in.
    public func revoke(_ refreshToken: String) async {
        var request = formRequest(configuration.revocationEndpoint, [
            "token": refreshToken,
            "token_type_hint": "refresh_token",
            "client_id": configuration.clientID,
        ])
        request.timeoutInterval = 10
        _ = try? await loader.data(for: request)
    }

    private func tokens(_ form: [String: String]) async throws(AuthError) -> TokenSet {
        let data: Data
        let response: HTTPURLResponse
        do {
            (data, response) = try await loader.data(for: formRequest(configuration.tokenEndpoint, form))
        } catch {
            throw .network
        }
        guard (200 ..< 300).contains(response.statusCode), let body = try? JSONDecoder().decode(
                  TokenResponse.self,
                  from: data,
              ),
              let refresh = body.refreshToken else {
            throw .tokenEndpoint(status: response.statusCode)
        }
        return TokenSet(
            accessToken: body.accessToken,
            refreshToken: refresh,
            accessTokenExpiresAt: clock().addingTimeInterval(TimeInterval(body.expiresIn)),
        )
    }

    private func formRequest(_ url: URL, _ form: [String: String]) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = Self.formEncoded(form).data(using: .utf8)
        return request
    }

    static func formEncoded(_ form: [String: String]) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return form.sorted { $0.key < $1.key }.map { key, value in
            "\(key)=\(value.addingPercentEncoding(withAllowedCharacters: allowed) ?? "")"
        }.joined(separator: "&")
    }
}

private struct TokenResponse: Decodable {
    let accessToken: String
    let refreshToken: String?
    let expiresIn: Int

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case expiresIn = "expires_in"
    }
}
