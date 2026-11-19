import Foundation

/// Asks for notification permission after the reminder time is chosen (docs/07-ux-ui.md §5.3, iOS).
public protocol NotificationPermissionRequesting: Sendable {
    func requestAuthorization() async
}

/// Used by previews, UI tests and unit tests: never shows the system alert.
public struct NoNotificationPermission: NotificationPermissionRequesting {
    public init() {}
    public func requestAuthorization() async {}
}
