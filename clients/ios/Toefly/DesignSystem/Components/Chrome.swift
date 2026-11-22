import SwiftUI
import ToeflyCore

/// `mesh-auth` and `mesh-app` as `MeshGradient` (docs/07-ux-ui.md §2.5): brand-500, #B6F0CF and brand-600 over
/// the canvas; quieter for the app and in the dark theme.
struct MeshBackground: View {
    enum Variant { case auth, app }

    var variant: Variant = .auth
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let strength = (variant == .app ? 0.5 : 1) * (colorScheme == .dark ? 0.35 : 1)
        let canvas = Color.toefly.meshCanvas
        MeshGradient(
            width: 3,
            height: 3,
            points: [
                [0, 0], [0.5, 0], [1, 0],
                [0, 0.5], [0.55, 0.45], [1, 0.5],
                [0, 1], [0.5, 1], [1, 1],
            ],
            colors: [
                Color(hex: 0x04AC6A, opacity: 0.85 * strength), canvas, canvas,
                canvas, canvas, Color(hex: 0xB6F0CF, opacity: strength),
                canvas, Color(hex: 0x08945B, opacity: 0.55 * strength), canvas,
            ],
        )
        .background(canvas)
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}

/// Floating AI Tutor button: 52 pt circle above the tab bar on the right, interactive Liquid Glass, `sparkles` in
/// the accent color (docs/07-ux-ui.md §2.6, §4.3).
struct TutorFab: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "sparkles")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Color.toefly.accent)
                .frame(width: 52, height: 52)
                .contentShape(.circle)
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .circle)
        .accessibilityLabel("AI tutor")
        .accessibilityHint("Ask Toefly Tutor about this page")
        .accessibilityIdentifier("tutor-fab")
    }
}

/// Panel on glass for the onboarding over `mesh-auth`; solid `surface-raised` with Reduce Transparency
/// (docs/07-ux-ui.md §2.6).
struct GlassPanel<Content: View>: View {
    @ViewBuilder var content: Content
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Radius.xxl)
        VStack(alignment: .leading, spacing: Space.s5) { content }
            .padding(Space.s5)
            .frame(maxWidth: 560, alignment: .leading)
            .background {
                if reduceTransparency {
                    shape.fill(Color.toefly.surfaceRaised)
                }
            }
            .glassEffect(reduceTransparency ? .identity : .regular, in: shape)
    }
}

/// Row of a task: section icon, title, section and time, status (docs/07-ux-ui.md §5.5, iOS without the tag column).
struct TaskRow: View {
    let task: WeekTask

    var body: some View {
        HStack(spacing: Space.s3) {
            IconBox(systemImage: WeekSectionStyle.symbol(task.section), size: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(task.title).font(.toeflyBodyStrong).foregroundStyle(Color.toefly.ink)
                Text("\(task.section.label) · \(task.estimatedMinutes) min").font(.toeflyCaption)
                    .foregroundStyle(Color.toefly.inkMuted)
            }
            Spacer(minLength: Space.s2)
            StatusMark(status: task.status)
        }
        .padding(.vertical, Space.s2)
        .accessibilityElement(children: .combine)
        .accessibilityValue(StatusMark.spoken(task.status))
    }
}

/// Round status check: empty, half (in progress) or done.
struct StatusMark: View {
    let status: WeekTaskStatus

    static func spoken(_ status: WeekTaskStatus) -> String {
        switch status {
        case .pending: "Not started"
        case .inProgress: "In progress"
        case .done: "Done"
        case .skipped: "Skipped"
        }
    }

    var body: some View {
        Group {
            switch status {
            case .done:
                Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.toefly.primary)
            case .inProgress:
                Image(systemName: "circle.lefthalf.filled").foregroundStyle(Color.toefly.accent)
            case .pending, .skipped:
                Image(systemName: "circle").foregroundStyle(Color.toefly.checkboxBorder)
            }
        }
        .font(.title3)
        .accessibilityHidden(true)
    }
}
