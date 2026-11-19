import SwiftUI

/// `ProgressBar`: 6 pt (4 pt thin), `surface-sunken` track, `bar-gradient` fill (docs/07-ux-ui.md §3.3).
struct ProgressBar: View {
    let value: Double
    let label: String
    var thin = false
    var showsPercent = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var fraction: Double {
        min(max(value / 100, 0), 1)
    }

    var body: some View {
        HStack(spacing: Space.s3) {
            Capsule()
                .fill(Color.toefly.ringTrack)
                .frame(height: thin ? 4 : 6)
                .overlay(alignment: .leading) {
                    GeometryReader { proxy in
                        Capsule().fill(.barGradient).frame(width: proxy.size.width * fraction)
                    }
                }
                .clipShape(.capsule)
                .animation(reduceMotion ? nil : Motion.progress, value: fraction)
            if showsPercent {
                Text("\(Int(value.rounded()))%")
                    .font(.toeflyCaption.monospacedDigit())
                    .foregroundStyle(Color.toefly.inkMuted)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityValue("\(Int(value.rounded())) percent")
    }
}

/// `ProgressRing`: arc with `ring-gradient` from −90°, round caps (docs/07-ux-ui.md §3.3).
struct ProgressRing<Center: View>: View {
    let fraction: Double
    var diameter: CGFloat = 48
    var dashedTrack = false
    @ViewBuilder var center: Center

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ScaledMetric(relativeTo: .body) private var scale: CGFloat = 1

    private var lineWidth: CGFloat {
        diameter >= 120 ? 10 : 8
    }

    var body: some View {
        ZStack {
            Circle()
                .stroke(
                    Color.toefly.ringTrack,
                    style: StrokeStyle(lineWidth: lineWidth, dash: dashedTrack ? [4, 6] : []),
                )
            Circle()
                .trim(from: 0, to: min(max(fraction, 0), 1))
                .stroke(AngularGradient.ring, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(reduceMotion ? nil : Motion.progress, value: fraction)
            center
        }
        .frame(width: diameter * min(scale, 1.6), height: diameter * min(scale, 1.6))
    }
}

/// `BandRing` in the `insufficient` state: dashed track, `gauge` icon and "Need more data" (docs/07-ux-ui.md §3.3, P6).
struct BandRingInsufficient: View {
    let detail: String

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: Space.s4) { content }
            VStack(alignment: .leading, spacing: Space.s3) { content }
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private var content: some View {
        ProgressRing(fraction: 0, diameter: 74, dashedTrack: true) {
            Image(systemName: "gauge.with.needle")
                .font(.title2)
                .foregroundStyle(Color.toefly.inkMuted)
                .accessibilityHidden(true)
        }
        VStack(alignment: .leading, spacing: Space.s1) {
            Text("Need more data").font(.toeflyTitle3).foregroundStyle(Color.toefly.ink)
            Text(detail).font(.toeflyBody).foregroundStyle(Color.toefly.inkMuted).fixedSize(
                horizontal: false,
                vertical: true,
            )
            AIEstimateBadge()
        }
    }
}

/// "AI estimate" label on every score forecast (P6).
struct AIEstimateBadge: View {
    var body: some View {
        Text("AI estimate")
            .font(.toeflyMicro)
            .foregroundStyle(Color.toefly.accentOnSoft)
            .padding(.horizontal, Space.s2)
            .padding(.vertical, 2)
            .background(Color.toefly.accentSoft, in: .capsule)
    }
}
