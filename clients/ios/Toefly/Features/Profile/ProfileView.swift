import SwiftUI
import ToeflyFeatures

/// Profile opened from Home: who is signed in and "Sign out" (AC-05.03.2, AC-01.05.3). Settings, billing and the
/// tutor workspace arrive with phase 12e.
struct ProfileView: View {
    let app: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Space.s4) {
                if let profile = app.profile {
                    Card(large: true) {
                        HStack(spacing: Space.s4) {
                            Avatar(initials: profile.initials, size: 56)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(profile.displayName ?? profile.email).font(.toeflyTitle3)
                                    .foregroundStyle(Color.toefly.ink)
                                Text(profile.email).font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
                            }
                        }
                        .accessibilityElement(children: .combine)
                    }
                }
                Button(role: .destructive) {
                    Task { await app.signOut() }
                } label: {
                    ButtonLabel(title: "Sign out", systemImage: "rectangle.portrait.and.arrow.right")
                }
                .buttonStyle(.toefly(.ghost))
                .accessibilityIdentifier("sign-out")
            }
            .padding(Space.s4)
        }
        .background(Color.toefly.canvas)
        .navigationTitle("Profile")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Close", systemImage: "xmark") { dismiss() }
                    .accessibilityIdentifier("profile-close")
            }
        }
    }
}
