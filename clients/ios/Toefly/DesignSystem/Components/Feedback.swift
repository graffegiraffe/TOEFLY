import SwiftUI
import ToeflyCore

/// Banner of the info, warning and error states (docs/07-ux-ui.md §3.20).
struct Banner: View {
    enum Kind { case info, warning, danger }

    let kind: Kind
    let text: String
    var systemImage: String?

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Space.s3) {
            Image(systemName: systemImage ?? defaultSymbol).accessibilityHidden(true)
            Text(text).fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .font(.toeflyBody)
        .foregroundStyle(foreground)
        .padding(Space.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(background, in: .rect(cornerRadius: Radius.base))
        .accessibilityElement(children: .combine)
    }

    private var defaultSymbol: String {
        switch kind {
        case .info: "info.circle"
        case .warning: "exclamationmark.triangle"
        case .danger: "exclamationmark.circle"
        }
    }

    private var foreground: Color {
        switch kind {
        case .info: Color.toefly.info
        case .warning: Color.toefly.warning
        case .danger: Color.toefly.danger
        }
    }

    private var background: Color {
        switch kind {
        case .info: Color.toefly.infoSoft
        case .warning: Color.toefly.warningSoft
        case .danger: Color.toefly.dangerSoft
        }
    }
}

/// `EmptyState`: illustration, `title-3`, up to two lines of text, one button (docs/07-ux-ui.md §3.18).
struct EmptyState<Action: View>: View {
    let systemImage: String
    let title: String
    let text: String
    @ViewBuilder var action: Action

    var body: some View {
        VStack(spacing: Space.s3) {
            Image(systemName: systemImage)
                .font(.system(size: 48, weight: .light))
                .foregroundStyle(Color.toefly.accent)
                .accessibilityHidden(true)
            Text(title).font(.toeflyTitle3).foregroundStyle(Color.toefly.ink).multilineTextAlignment(.center)
            Text(text).font(.toeflyBody).foregroundStyle(Color.toefly.inkMuted).multilineTextAlignment(.center)
            action
        }
        .padding(Space.s6)
        .frame(maxWidth: .infinity)
    }
}

extension EmptyState where Action == EmptyView {
    init(systemImage: String, title: String, text: String) {
        self.init(systemImage: systemImage, title: title, text: text) { EmptyView() }
    }
}

/// Inline error block with "Try again" and the trace id in small print (docs/07-ux-ui.md §3.20).
struct ErrorState: View {
    let error: APIError
    let retry: () -> Void

    var body: some View {
        Card {
            HStack(alignment: .firstTextBaseline, spacing: Space.s2) {
                Image(systemName: error == .offline ? "wifi.slash" : "exclamationmark.circle")
                    .foregroundStyle(Color.toefly.danger)
                    .accessibilityHidden(true)
                Text(error.message).font(.toeflyBody).foregroundStyle(Color.toefly.ink)
            }
            if let traceId = error.traceId {
                Text("Trace \(traceId)").font(.toeflyCaption.monospaced()).foregroundStyle(Color.toefly.inkMuted)
            }
            Button("Try again", action: retry).buttonStyle(.toefly(.secondary))
        }
        .accessibilityIdentifier("error-state")
    }
}

/// Skeleton plates of `surface-sunken`; static with Reduce Motion (docs/07-ux-ui.md §3.19).
struct Skeleton: View {
    var height: CGFloat = 20
    var width: CGFloat?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dimmed = false

    var body: some View {
        RoundedRectangle(cornerRadius: Radius.sm)
            .fill(Color.toefly.surfaceSunken)
            .frame(width: width, height: height)
            .frame(maxWidth: width == nil ? .infinity : nil, alignment: .leading)
            .opacity(dimmed ? 0.55 : 1)
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.easeInOut(duration: 1.2).repeatForever()) { dimmed = true }
            }
            .accessibilityHidden(true)
    }
}
