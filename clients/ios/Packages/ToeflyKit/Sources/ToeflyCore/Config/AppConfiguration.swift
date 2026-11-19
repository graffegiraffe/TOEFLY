import Foundation

/// Endpoints and switches of one environment. The app reads them from Info.plist (`TOEFLYApiBaseURL`,
/// `TOEFLYAuthIssuer`, `TOEFLYAppleSignInEnabled`), so a build for another environment changes no code.
public struct AppConfiguration: Sendable, Equatable {
    /// Origin of `api-gateway`; operations of profile-service carry `/api/v1` in their paths, planning-service does
    /// not.
    public var apiBaseURL: URL
    public var oidc: OIDCConfiguration
    /// Sign in with Apple needs the provider configured in Keycloak and a paid Apple developer account (AC-01.04.4).
    public var appleSignInEnabled: Bool
    public var clientVersion: String

    public init(apiBaseURL: URL, oidc: OIDCConfiguration, appleSignInEnabled: Bool, clientVersion: String) {
        self.apiBaseURL = apiBaseURL
        self.oidc = oidc
        self.appleSignInEnabled = appleSignInEnabled
        self.clientVersion = clientVersion
    }

    /// The local Compose stack: api-gateway on 8080, Keycloak realm `toefly` on 8180 (docs/09-infrastructure.md).
    public static let local = AppConfiguration(
        apiBaseURL: URL(string: "http://localhost:8080")!,
        oidc: OIDCConfiguration(issuer: URL(string: "http://localhost:8180/realms/toefly")!),
        appleSignInEnabled: false,
        clientVersion: "0.1.0",
    )

    public init(infoDictionary: [String: Any]) {
        let fallback = Self.local
        let api = (infoDictionary["TOEFLYApiBaseURL"] as? String).flatMap(URL.init(string:)) ?? fallback.apiBaseURL
        let issuer = (infoDictionary["TOEFLYAuthIssuer"] as? String).flatMap(URL.init(string:)) ?? fallback.oidc.issuer
        let apple = (infoDictionary["TOEFLYAppleSignInEnabled"] as? String).map { $0 == "YES" || $0 == "true" }
            ?? (infoDictionary["TOEFLYAppleSignInEnabled"] as? Bool) ?? false
        let version = infoDictionary["CFBundleShortVersionString"] as? String ?? fallback.clientVersion
        self.init(
            apiBaseURL: api,
            oidc: OIDCConfiguration(issuer: issuer),
            appleSignInEnabled: apple,
            clientVersion: version,
        )
    }

    /// Base URL of the planning-service client: its OpenAPI paths are relative to `/api/v1`.
    public var apiV1URL: URL {
        apiBaseURL.appending(path: "api/v1")
    }
}

/// Public client `toefly-ios` of the realm `toefly` (docs/10-security-privacy.md §4.1, §4.3).
public struct OIDCConfiguration: Sendable, Equatable {
    public var issuer: URL
    public var clientID: String
    public var redirectURI: URL
    public var scopes: [String]

    public init(
        issuer: URL,
        clientID: String = "toefly-ios",
        redirectURI: URL = URL(string: "app.toefly://oauth/callback")!,
        scopes: [String] = ["openid", "profile", "email", "offline_access"],
    ) {
        self.issuer = issuer
        self.clientID = clientID
        self.redirectURI = redirectURI
        self.scopes = scopes
    }

    public var callbackScheme: String {
        redirectURI.scheme ?? "app.toefly"
    }

    public var authorizationEndpoint: URL {
        issuer.appending(path: "protocol/openid-connect/auth")
    }

    public var tokenEndpoint: URL {
        issuer.appending(path: "protocol/openid-connect/token")
    }

    public var revocationEndpoint: URL {
        issuer.appending(path: "protocol/openid-connect/revoke")
    }
}
