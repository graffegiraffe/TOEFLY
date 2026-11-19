import SwiftUI

/// Button of the design system, identical to the web button: gradient pill, 44/36/52 pt high, 17 pt semibold
/// (docs/07-ux-ui.md §2.12, §3.1). The primary button does not use `.glassProminent`: glass is only for the chrome.
struct ToeflyButtonStyle: ButtonStyle {
    enum Variant { case primary, secondary, ghost, destructive }
    enum Size { case sm, md, lg }

    var variant: Variant = .primary
    var size: Size = .md
    var block = false

    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.colorScheme) private var colorScheme

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(size == .sm ? .subheadline.weight(.semibold) : .body.weight(.semibold))
            .multilineTextAlignment(.center)
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, Space.s2)
            .frame(maxWidth: block ? .infinity : nil, minHeight: minHeight)
            .foregroundStyle(foreground)
            .background { background(pressed: configuration.isPressed) }
            .clipShape(.capsule)
            .contentShape(.capsule)
            .shadow(color: shadowColor, radius: 7, y: 4)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .opacity(isEnabled ? 1 : 0.45)
            .animation(.linear(duration: 0.08), value: configuration.isPressed)
    }

    private var horizontalPadding: CGFloat {
        switch size {
        case .sm: 14
        case .md: 20
        case .lg: 24
        }
    }

    private var minHeight: CGFloat {
        switch size {
        case .sm: 36
        case .md: 44
        case .lg: 52
        }
    }

    private var foreground: Color {
        switch variant {
        case .primary, .destructive: .white
        case .secondary: Color.toefly.accentOnSoft
        case .ghost: Color.toefly.link
        }
    }

    private var shadowColor: Color {
        guard variant == .primary, isEnabled else { return .clear }
        return colorScheme == .dark ? Color(hex: 0x04AC6A, opacity: 0.25) : Color(hex: 0x08945B, opacity: 0.35)
    }

    @ViewBuilder private func background(pressed: Bool) -> some View {
        switch variant {
        case .primary: Capsule().fill(pressed ? .buttonGradientHover : .buttonGradient)
        case .secondary: Capsule().fill(pressed ? Color.toefly.brand100 : Color.toefly.accentSoft)
        case .ghost: Capsule().fill(pressed ? Color.toefly.accentSoft : .clear)
        case .destructive: Capsule().fill(pressed ? Color(hex: 0xA82D24) : Color.toefly.dangerSolid)
        }
    }
}

extension ButtonStyle where Self == ToeflyButtonStyle {
    static func toefly(
        _ variant: ToeflyButtonStyle.Variant = .primary,
        size: ToeflyButtonStyle.Size = .md,
        block: Bool = false,
    ) -> ToeflyButtonStyle {
        ToeflyButtonStyle(variant: variant, size: size, block: block)
    }
}

/// Label with an optional leading SF Symbol and a spinner in the loading state (docs/07-ux-ui.md §3).
struct ButtonLabel: View {
    let title: String
    var systemImage: String?
    var isLoading = false

    var body: some View {
        HStack(spacing: Space.s2) {
            if isLoading {
                ProgressView().tint(.white).controlSize(.small)
            } else if let systemImage {
                Image(systemName: systemImage).accessibilityHidden(true)
            }
            Text(title)
        }
    }
}

/// Icon-only button with a 44 pt target and a mandatory label (docs/07-ux-ui.md §3.1 `icon`).
struct IconButton: View {
    let systemImage: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.body.weight(.medium))
                .foregroundStyle(Color.toefly.inkMuted)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(.rect)
        }
        .accessibilityLabel(label)
    }
}
