import Foundation
import Observation
import ToeflyCore

/// Sign-in, restore and sign-out of the session; `SessionManager` in the app.
public protocol SessionControlling: Sendable {
    func signIn(with method: SignInMethod) async throws(AuthError)
    func restore() async -> Bool
    func signOut() async
    func onSessionExpired(_ handler: @escaping @Sendable () async -> Void) async
}

extension SessionManager: SessionControlling {}

/// The five tabs of the student (AC-05.03.2) with their SF Symbols (docs/07-ux-ui.md §2.9, §4.3).
public enum AppTab: String, CaseIterable, Sendable, Hashable {
    case home, today, weeks, library, progress

    public var title: String {
        switch self {
        case .home: "Home"
        case .today: "Today"
        case .weeks: "Weeks"
        case .library: "Library"
        case .progress: "Progress"
        }
    }

    public var symbol: String {
        switch self {
        case .home: "house"
        case .today: "sun.max"
        case .weeks: "calendar"
        case .library: "books.vertical"
        case .progress: "chart.bar"
        }
    }
}

public enum AppPhase: Equatable, Sendable {
    case launching
    case signedOut
    case onboarding
    case main
}

/// Root state of the app: which flow is on screen and the models of its screens.
@MainActor
@Observable
public final class AppModel {
    public private(set) var phase: AppPhase = .launching
    public private(set) var profile: UserProfile?
    public private(set) var onboarding: OnboardingViewModel?
    public private(set) var home: HomeViewModel?
    public let signIn: SignInViewModel
    public let configuration: AppConfiguration

    public var selectedTab: AppTab = .home
    public var isTutorPresented = false
    public var isProfilePresented = false
    /// Neutral notice on the welcome screen after the session could not be renewed (AC-01.05.5).
    public private(set) var notice: String?
    /// Tab to reopen after the person signs in again (AC-01.05.5).
    private var tabToRestore: AppTab?

    private let session: any SessionControlling
    private let profiles: any ProfileRepository
    private let planning: any PlanningRepository
    private let notifications: any NotificationPermissionRequesting
    private let now: @Sendable () -> Date

    public init(
        configuration: AppConfiguration,
        session: any SessionControlling,
        profiles: any ProfileRepository,
        planning: any PlanningRepository,
        notifications: any NotificationPermissionRequesting = NoNotificationPermission(),
        now: @escaping @Sendable () -> Date = Date.init,
    ) {
        self.configuration = configuration
        self.session = session
        self.profiles = profiles
        self.planning = planning
        self.notifications = notifications
        self.now = now
        signIn = SignInViewModel(session: session, appleSignInEnabled: configuration.appleSignInEnabled)
        signIn.onSignedIn = { [weak self] in await self?.routeSignedInUser() }
    }

    /// Restores the stored session at launch.
    public func start() async {
        await session.onSessionExpired { [weak self] in await self?.sessionExpired() }
        if await session.restore() {
            await routeSignedInUser()
        } else {
            phase = .signedOut
        }
    }

    /// After sign-in: students who have not finished the onboarding go there, everyone else to the tabs
    /// (AC-02.04.2, AC-02.04.3).
    public func routeSignedInUser() async {
        do {
            let profile = try await profiles.me()
            self.profile = profile
            notice = nil
            if profile.needsOnboarding {
                let model = OnboardingViewModel(profiles: profiles, notifications: notifications, now: now)
                model.onCompleted = { [weak self] in await self?.onboardingCompleted() }
                onboarding = model
                phase = .onboarding
                await model.load()
            } else {
                showMain()
            }
        } catch .sessionExpired {
            sessionExpired()
        } catch {
            notice = error.message
            phase = .signedOut
        }
    }

    public func onboardingCompleted() async {
        profile?.onboardingStatus = .completed
        onboarding = nil
        showMain()
    }

    private func showMain() {
        home = HomeViewModel(profile: profile, profiles: profiles, planning: planning, now: now)
        if let tabToRestore {
            selectedTab = tabToRestore
            self.tabToRestore = nil
        }
        phase = .main
    }

    /// Opens Today from the Home block (US-05.02).
    public func openToday() {
        selectedTab = .today
    }

    /// Sign out: revoke the refresh token, forget local tokens, return to the welcome screen (AC-01.05.3).
    public func signOut() async {
        isProfilePresented = false
        isTutorPresented = false
        await session.signOut()
        reset()
        selectedTab = .home
        notice = nil
        phase = .signedOut
    }

    /// The refresh token was rejected: back to sign-in, keeping the tab for the return (AC-01.05.5).
    public func sessionExpired() {
        guard phase != .signedOut else { return }
        tabToRestore = phase == .main ? selectedTab : nil
        isProfilePresented = false
        isTutorPresented = false
        reset()
        notice = "Your session has ended. Sign in again to continue."
        phase = .signedOut
    }

    private func reset() {
        profile = nil
        onboarding = nil
        home = nil
    }
}
