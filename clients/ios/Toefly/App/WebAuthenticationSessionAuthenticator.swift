import AuthenticationServices
import ToeflyCore
import UIKit

/// Opens the TOEFLY theme of Keycloak in `ASWebAuthenticationSession`. The session is ephemeral: Safari cookies are
/// not shared and do not outlive the sign-in, which is safe on shared devices (docs/10-security-privacy.md §4.3).
struct WebAuthenticationSessionAuthenticator: WebAuthenticating {
    func authenticate(url: URL, callbackScheme: String) async throws(AuthError) -> URL {
        do {
            return try await Self.run(url: url, callbackScheme: callbackScheme)
        } catch let error as ASWebAuthenticationSessionError where error.code == .canceledLogin {
            throw .cancelled
        } catch {
            throw .network
        }
    }

    @MainActor
    private static func run(url: URL, callbackScheme: String) async throws -> URL {
        let anchor = PresentationAnchor()
        return try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: url,
                callback: .customScheme(callbackScheme),
            ) { callback, error in
                if let callback {
                    continuation.resume(returning: callback)
                } else {
                    continuation.resume(throwing: error ?? ASWebAuthenticationSessionError(.canceledLogin))
                }
            }
            session.prefersEphemeralWebBrowserSession = true
            session.presentationContextProvider = anchor
            if !session.start() {
                continuation.resume(throwing: ASWebAuthenticationSessionError(.presentationContextInvalid))
            }
            // The provider is held weakly by the session; keep it alive until the callback.
            objc_setAssociatedObject(session, &PresentationAnchor.key, anchor, .OBJC_ASSOCIATION_RETAIN)
        }
    }
}

private final class PresentationAnchor: NSObject, ASWebAuthenticationPresentationContextProviding {
    nonisolated(unsafe) static var key: UInt8 = 0

    func presentationAnchor(for _: ASWebAuthenticationSession) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            if let window = scenes.flatMap(\.windows).first(where: \.isKeyWindow) ?? scenes.first?.windows.first {
                return window
            }
            guard let scene = scenes.first else { preconditionFailure("No window scene to present the sign-in sheet") }
            return ASPresentationAnchor(windowScene: scene)
        }
    }
}
