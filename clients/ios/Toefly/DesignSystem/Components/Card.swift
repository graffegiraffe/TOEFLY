import SwiftUI

/// `Card`: `surface`, 1 pt `border`, radius 12 (16 for large), padding 16 (20 for large), no shadow
/// (docs/07-ux-ui.md §3.2).
struct Card<Content: View>: View {
    var large = false
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            content
        }
        .padding(large ? Space.s5 : Space.s4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.toefly.surface, in: .rect(cornerRadius: large ? Radius.lg : Radius.base))
        .overlay {
            RoundedRectangle(cornerRadius: large ? Radius.lg : Radius.base).strokeBorder(Color.toefly.border)
        }
    }
}

/// Square icon box of a section or a block: `accent-soft` background, `accent` icon (docs/07-ux-ui.md §3.2).
struct IconBox: View {
    let systemImage: String
    var size: CGFloat = 36

    var body: some View {
        Image(systemName: systemImage)
            .font(.body.weight(.medium))
            .foregroundStyle(Color.toefly.accentOnSoft)
            .frame(width: size, height: size)
            .background(Color.toefly.accentSoft, in: .rect(cornerRadius: Radius.sm))
            .accessibilityHidden(true)
    }
}

/// Overline label of a group ("YOUR GOAL").
struct Overline: View {
    let text: String
    var systemImage: String?

    var body: some View {
        HStack(spacing: Space.s1) {
            if let systemImage {
                Image(systemName: systemImage).accessibilityHidden(true)
            }
            Text(text.uppercased())
        }
        .font(.toeflyOverline)
        .tracking(0.6)
        .foregroundStyle(Color.toefly.inkMuted)
        .accessibilityAddTraits(.isHeader)
    }
}
