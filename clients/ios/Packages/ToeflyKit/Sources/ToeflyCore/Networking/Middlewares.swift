import Foundation
import HTTPTypes
import OpenAPIRuntime

/// Supplies the bearer token of the current session.
public protocol AccessTokenProviding: Sendable {
    func accessToken() async throws(AuthError) -> String
    func refreshAfterRejection(of token: String) async throws(AuthError) -> String
}

extension SessionManager: AccessTokenProviding {}

/// Adds `Authorization: Bearer …`; after a 401 it renews the token once and repeats the request (AC-01.05.5).
public struct AuthMiddleware: ClientMiddleware {
    private let tokens: any AccessTokenProviding

    public init(tokens: any AccessTokenProviding) {
        self.tokens = tokens
    }

    public func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID _: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?),
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let token = try await tokens.accessToken()
        var signed = request
        signed.headerFields[.authorization] = "Bearer \(token)"
        let (response, responseBody) = try await next(signed, body, baseURL)
        // A request body is a single-use stream; only requests without one are repeated.
        guard response.status == .unauthorized, body == nil else { return (response, responseBody) }
        let renewed = try await tokens.refreshAfterRejection(of: token)
        signed.headerFields[.authorization] = "Bearer \(renewed)"
        return try await next(signed, nil, baseURL)
    }
}

/// `X-Client-Platform`, `X-Client-Version` and `Accept-Language` on every request (docs/05-api-contracts.md §2.11).
public struct ClientInfoMiddleware: ClientMiddleware {
    private let version: String
    private let language: String

    public init(
        version: String,
        language: String = Locale.preferredLanguages.first?.hasPrefix("ru") == true ? "ru" : "en",
    ) {
        self.version = version
        self.language = language
    }

    public func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID _: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?),
    ) async throws -> (HTTPResponse, HTTPBody?) {
        var request = request
        request.headerFields[.clientPlatform] = "ios"
        request.headerFields[.clientVersion] = version
        request.headerFields[.acceptLanguage] = language
        return try await next(request, body, baseURL)
    }
}

/// `Idempotency-Key` for unsafe requests that do not carry one yet (docs/05-api-contracts.md §2.6).
public struct IdempotencyMiddleware: ClientMiddleware {
    private let makeKey: @Sendable () -> String

    public init(makeKey: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() }) {
        self.makeKey = makeKey
    }

    public func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID _: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?),
    ) async throws -> (HTTPResponse, HTTPBody?) {
        var request = request
        let name = HTTPField.Name.idempotencyKey
        if request.method == .post, request.headerFields[name] == nil {
            request.headerFields[name] = makeKey()
        }
        return try await next(request, body, baseURL)
    }
}

extension HTTPField.Name {
    // The names are valid header tokens, so the failable initializer cannot fail.
    // swiftlint:disable force_unwrapping
    static let clientPlatform = HTTPField.Name("X-Client-Platform")!
    static let clientVersion = HTTPField.Name("X-Client-Version")!
    static let idempotencyKey = HTTPField.Name("Idempotency-Key")!
    // swiftlint:enable force_unwrapping
}
