import SwiftUI
import ToeflyFeatures

@main
struct ToeflyApp: App {
    @State private var app = AppEnvironment.makeAppModel()

    var body: some Scene {
        WindowGroup {
            RootView(app: app)
                .tint(Color.toefly.link)
                // Layouts are checked up to AX3 (docs/12-implementation-plan.md, phase 12 DoD).
                .dynamicTypeSize(...DynamicTypeSize.accessibility3)
                .task { await app.start() }
        }
    }
}

/// Switches between the welcome screen, the onboarding and the tabs.
struct RootView: View {
    @Bindable var app: AppModel

    var body: some View {
        Group {
            switch app.phase {
            case .launching:
                LaunchView()
            case .signedOut:
                WelcomeView(model: app.signIn, notice: app.notice)
            case .onboarding:
                if let onboarding = app.onboarding {
                    OnboardingView(model: onboarding)
                }
            case .main:
                MainTabView(app: app)
            }
        }
        .animation(Motion.base, value: app.phase)
    }
}

private struct LaunchView: View {
    var body: some View {
        ZStack {
            MeshBackground(variant: .auth)
            VStack(spacing: Space.s4) {
                Logo(font: .largeTitle.weight(.heavy))
                ProgressView().accessibilityLabel("Loading")
            }
        }
    }
}
