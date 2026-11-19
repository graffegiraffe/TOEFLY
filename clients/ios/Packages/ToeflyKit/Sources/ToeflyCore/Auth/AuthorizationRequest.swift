import CryptoKit
import Foundation

/// How the person signs in. Google and Apple go through the Keycloak broker (`kc_idp_hint`), so the app never
/// sees provider tokens (docs/10-security-privacy.md §4.4).
public enum SignInMethod: String, Sendable, Equatable {
    case email
    case signUp
    case google
    case apple

    var identityProviderHint: String? {
        switch self {
        case .google: "google"
        case .apple: "apple"
        case .email, .signUp: nil
        }
    }
}

/// Proof Key for Code Exchange, S256 (RFC 7636).
public struct PKCE: Sendable, Equatable {
    public let verifier: String
    public let challenge: String

    public init(verifier: String) {
        self.verifier = verifier
        challenge = Self.base64URL(Data(SHA256.hash(data: Data(verifier.utf8))))
    }

    /// 32 random bytes give a 43-character verifier, the minimum length of RFC 7636 §4.1.
    public static func random() -> PKCE {
        PKCE(verifier: randomToken(bytes: 32))
    }

    static func base64URL(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    public static func randomToken(bytes count: Int) -> String {
        var generator = SystemRandomNumberGenerator()
        let bytes = (0 ..< count).map { _ in UInt8.random(in: .min ... .max, using: &generator) }
        return base64URL(Data(bytes))
    }
}

/// One authorization request with its PKCE, state and nonce (docs/10-security-privacy.md §4.3).
public struct AuthorizationRequest: Sendable, Equatable {
    public let url: URL
    public let pkce: PKCE
    public let state: String
    public let nonce: String

    public init(
        configuration: OIDCConfiguration,
        method: SignInMethod,
        pkce: PKCE = .random(),
        state: String = PKCE.randomToken(bytes: 16),
        nonce: String = PKCE.randomToken(bytes: 16),
    ) {
        self.pkce = pkce
        self.state = state
        self.nonce = nonce
        var items = [
            URLQueryItem(name: "client_id", value: configuration.clientID),
            URLQueryItem(name: "redirect_uri", value: configuration.redirectURI.absoluteString),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: configuration.scopes.joined(separator: " ")),
            URLQueryItem(name: "code_challenge", value: pkce.challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "nonce", value: nonce),
        ]
        if let hint = method.identityProviderHint {
            items.append(URLQueryItem(name: "kc_idp_hint", value: hint))
        }
        if method == .signUp {
            // OIDC `prompt=create` opens the registration page of the TOEFLY theme (Keycloak 24+).
            items.append(URLQueryItem(name: "prompt", value: "create"))
        }
        url = configuration.authorizationEndpoint.appending(queryItems: items)
    }

    /// Reads the code from `app.toefly://oauth/callback?code=…&state=…`; a different state is rejected as forged.
    public func authorizationCode(from callback: URL) throws(AuthError) -> String {
        let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let value = { (name: String) in items.first { $0.name == name }?.value }
        if let error = value("error") {
            // Keycloak returns access_denied when the person cancels at the identity provider (AC-01.03.4).
            throw error == "access_denied" ? .cancelled : .provider(error)
        }
        guard value("state") == state else { throw .stateMismatch }
        guard let code = value("code"), !code.isEmpty else { throw .missingCode }
        return code
    }
}

public enum AuthError: Error, Equatable, Sendable {
    /// The person closed the sign-in sheet or cancelled at the provider; no message is shown (AC-01.03.4).
    case cancelled
    case stateMismatch
    case missingCode
    case provider(String)
    case appleUnavailable
    /// The refresh token is missing, expired or revoked: the person signs in again (AC-01.05.5).
    case sessionExpired
    case network
    case tokenEndpoint(status: Int)
}
