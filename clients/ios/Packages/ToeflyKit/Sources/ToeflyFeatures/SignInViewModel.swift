import Foundation
import Observation
import ToeflyCore

/// Welcome screen: email and password, Google and Sign in with Apple all open the TOEFLY theme of Keycloak in
/// `ASWebAuthenticationSession` (docs/07-ux-ui.md §5.2, docs/10-security-privacy.md §4.3).
@MainActor
@Observable
public final class SignInViewModel {
    public static let appleUnavailable = "Sign in with Apple is unavailable in this build"

    public private(set) var busyMethod: SignInMethod?
    public private(set) var errorMessage: String?
    public let appleSignInEnabled: Bool
    @ObservationIgnored public var onSignedIn: (@MainActor () async -> Void)?

    private let session: any SessionControlling

    public init(session: any SessionControlling, appleSignInEnabled: Bool) {
        self.session = session
        self.appleSignInEnabled = appleSignInEnabled
    }

    public var isBusy: Bool {
        busyMethod != nil
    }

    public func signIn(with method: SignInMethod) async {
        guard !isBusy else { return }
        errorMessage = nil
        // Without the Apple provider the button stays visible and explains itself (AC-01.04.4).
        if method == .apple, !appleSignInEnabled {
            errorMessage = Self.appleUnavailable
            return
        }
        busyMethod = method
        defer { busyMethod = nil }
        do {
            try await session.signIn(with: method)
        } catch .cancelled {
            return // closing the sheet or cancelling at Google is not an error (AC-01.03.4)
        } catch .network {
            errorMessage = "You're offline. Check your connection and try again."
            return
        } catch {
            errorMessage = "We couldn't sign you in. Try again."
            return
        }
        await onSignedIn?()
    }

    public func dismissError() {
        errorMessage = nil
    }
}
