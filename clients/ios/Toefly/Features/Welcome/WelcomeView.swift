import AuthenticationServices
import SwiftUI
import ToeflyCore
import ToeflyFeatures

/// `WelcomeView` before the web authentication session: logo, slogan, Sign in with Apple, Google, "Get started" and
/// "I already have an account" (docs/07-ux-ui.md §5.2; prototype screen "Sign in" on iPhone).
struct WelcomeView: View {
    let model: SignInViewModel
    let notice: String?

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ZStack {
            MeshBackground(variant: .auth)
            ScrollView {
                VStack(alignment: .leading, spacing: Space.s8) {
                    header
                    Spacer(minLength: Space.s8)
                    if let message = model.errorMessage ?? notice {
                        Banner(kind: model.errorMessage == nil ? .info : .warning, text: message)
                            .accessibilityIdentifier("welcome-message")
                    }
                    actions
                }
                .padding(.horizontal, Space.s5)
                .padding(.vertical, Space.s8)
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Logo(font: .title.weight(.heavy))
            Text("Prepare for the new TOEFL iBT like you have a personal tutor.")
                .font(.toeflyTitle1)
                .foregroundStyle(Color.toefly.ink)
                .accessibilityAddTraits(.isHeader)
            Text("Weekly plans, instant feedback on every task, real mock exams.")
                .font(.toeflyBody)
                .foregroundStyle(Color.toefly.inkMuted)
        }
        .padding(.top, Space.s8)
    }

    private var actions: some View {
        VStack(spacing: Space.s3) {
            SignInWithAppleButton(.signIn) { _ in } onCompletion: { _ in }
                .signInWithAppleButtonStyle(colorScheme == .dark ? .white : .black)
                .frame(height: 52)
                .clipShape(.capsule)
                .allowsHitTesting(false)
                .overlay {
                    // The native button only draws; sign-in goes through the Keycloak `apple` broker (AC-01.04.1).
                    Button { Task { await model.signIn(with: .apple) } } label: { Color.clear.contentShape(.capsule) }
                        .accessibilityLabel("Sign in with Apple")
                        .accessibilityIdentifier("sign-in-apple")
                }
                .disabled(model.isBusy)
            Button { Task { await model.signIn(with: .google) } } label: {
                ButtonLabel(
                    title: "Continue with Google",
                    systemImage: "g.circle",
                    isLoading: model.busyMethod == .google,
                )
            }
            .buttonStyle(.toefly(.secondary, size: .lg, block: true))
            .accessibilityIdentifier("sign-in-google")
            Button { Task { await model.signIn(with: .signUp) } } label: {
                ButtonLabel(title: "Get started", isLoading: model.busyMethod == .signUp)
            }
            .buttonStyle(.toefly(.primary, size: .lg, block: true))
            .accessibilityIdentifier("get-started")
            Button { Task { await model.signIn(with: .email) } } label: {
                ButtonLabel(title: "I already have an account", isLoading: model.busyMethod == .email)
            }
            .buttonStyle(.toefly(.ghost, size: .lg, block: true))
            .accessibilityIdentifier("sign-in-email")
        }
        .disabled(model.isBusy)
    }
}
