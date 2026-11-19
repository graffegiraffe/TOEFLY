import Foundation
import OpenAPIRuntime

/// What a screen needs to know about a failed call (docs/07-ux-ui.md §3.20).
public enum APIError: Error, Equatable, Sendable {
    /// The session could not be renewed; the app returns to sign-in.
    case sessionExpired
    /// No connection or a timeout: "You're offline …".
    case offline
    /// RFC 9457 problem or an undocumented status; `traceId` is shown small under "Try again".
    case server(status: Int, title: String?, traceId: String?)

    public var message: String {
        switch self {
        case .sessionExpired: "Your session has ended. Sign in again."
        case .offline: "You're offline. Check your connection and try again."
        case let .server(status, title, _):
            if status >= 500 {
                "Something went wrong on our side."
            } else {
                title ?? "Something went wrong on our side."
            }
        }
    }

    public var traceId: String? {
        if case let .server(_, _, traceId) = self {
            traceId
        } else {
            nil
        }
    }

    /// Maps any error thrown by a generated client call.
    public static func from(_ error: any Error) -> APIError {
        if let error = error as? APIError {
            return error
        }
        if let auth = error as? AuthError {
            return auth == .network ? .offline : .sessionExpired
        }
        if let client = error as? ClientError {
            return from(client.underlyingError)
        }
        if let url = error as? URLError {
            switch url.code {
            case .notConnectedToInternet, .networkConnectionLost, .timedOut, .cannotConnectToHost, .cannotFindHost,
                 .dataNotAllowed:
                return .offline
            default:
                return .server(status: 0, title: nil, traceId: nil)
            }
        }
        return .server(status: 0, title: nil, traceId: nil)
    }
}
