import SwiftUI
import ToeflyCore
import ToeflyFeatures

/// `Cover` (docs/07-ux-ui.md §3.2, §8): the cover gradient with line art on the right, decorative. The dark theme
/// dims it by `rgba(11,20,15,0.18)` (§8.1).
struct Cover: View {
    let id: String
    var height: CGFloat?
    var aspectRatio: CGFloat?

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Color.clear
            .frame(height: height)
            .aspectRatio(aspectRatio, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .overlay(alignment: .trailing) {
                Image("Covers/\(id)")
                    .resizable()
                    .scaledToFill()
            }
            .background(.coverGradient)
            .overlay(colorScheme == .dark ? Color(hex: 0x0B140F, opacity: 0.18) : .clear)
            .clipped()
            .accessibilityHidden(true)
    }
}

/// A small label pill: status of a week, origin of a task (docs/07-ux-ui.md §3.2 `WeekCard`, §3.4).
struct Pill: View {
    enum Style { case accent, muted, exam, solid }

    let text: String
    var systemImage: String?
    var style: Style = .accent

    var body: some View {
        HStack(spacing: 3) {
            if let systemImage {
                Image(systemName: systemImage).accessibilityHidden(true)
            }
            Text(text)
        }
        .font(.toeflyMicro)
        .foregroundStyle(foreground)
        .padding(.horizontal, Space.s2)
        .padding(.vertical, 3)
        .background(background, in: .capsule)
    }

    private var foreground: Color {
        switch style {
        case .accent: Color.toefly.accentOnSoft
        case .muted: Color.toefly.inkMuted
        case .exam: .white
        case .solid: Color.toefly.ink
        }
    }

    private var background: Color {
        switch style {
        case .accent: Color.toefly.accentSoft
        case .muted: Color.toefly.surfaceSunken
        case .exam: Color.toefly.brand700
        case .solid: Color.toefly.surface
        }
    }
}

/// Lays its children out in lines, wrapping like text: words and gaps of Complete the Words, the slots of Build a
/// Sentence. A child wider than the line gets a line of its own and the full width.
struct FlowLayout: Layout {
    var spacing: CGFloat = 4
    var lineSpacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        let height = rows.reduce(0) { $0 + $1.height } + lineSpacing * CGFloat(max(rows.count - 1, 0))
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal _: ProposedViewSize, subviews: Subviews, cache _: inout ()) {
        var y = bounds.minY
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for item in row.items {
                subviews[item.index].place(
                    at: CGPoint(x: x, y: y + (row.height - item.size.height) / 2),
                    proposal: ProposedViewSize(item.size),
                )
                x += item.size.width + spacing
            }
            y += row.height + lineSpacing
        }
    }

    private struct Row {
        var items: [(index: Int, size: CGSize)] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = [Row()]
        for index in subviews.indices {
            var size = subviews[index].sizeThatFits(.unspecified)
            if size.width > width {
                size = subviews[index].sizeThatFits(ProposedViewSize(width: width, height: nil))
            }
            let needed = (rows[rows.count - 1].items.isEmpty ? 0 : spacing) + size.width
            if rows[rows.count - 1].width + needed > width, !rows[rows.count - 1].items.isEmpty {
                rows.append(Row())
            }
            var row = rows[rows.count - 1]
            row.width += (row.items.isEmpty ? 0 : spacing) + size.width
            row.height = max(row.height, size.height)
            row.items.append((index, size))
            rows[rows.count - 1] = row
        }
        return rows
    }
}

/// A row of a week task: section icon, title, day, minutes, origin and status (docs/07-ux-ui.md §5.5, §5.7).
struct WeekTaskRow: View {
    let task: WeekTask
    var day: String?

    var body: some View {
        HStack(spacing: Space.s3) {
            IconBox(systemImage: WeekSectionStyle.symbol(task.section), size: 32)
            VStack(alignment: .leading, spacing: 3) {
                Text(task.title)
                    .font(.toeflyBodyStrong)
                    .foregroundStyle(Color.toefly.ink)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                Text(details).font(.toeflyCaption).foregroundStyle(Color.toefly.inkMuted)
                if let overdue = task.overdueDays, overdue > 0 {
                    Text(overdue == 1 ? "From yesterday" : "\(overdue) days behind")
                        .font(.toeflyCaption.weight(.semibold))
                        .foregroundStyle(Color.toefly.warning)
                }
            }
            Spacer(minLength: Space.s2)
            StatusMark(status: task.status)
        }
        .padding(.vertical, Space.s2)
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
        .accessibilityValue(StatusMark.spoken(task.status))
    }

    private var details: String {
        var parts = [task.section.label]
        if let day {
            parts.append(day)
        }
        parts.append("\(task.estimatedMinutes) min")
        if let provenance = task.provenance, provenance != .extracted {
            parts.append(provenance.label)
        }
        if task.legacy {
            parts.append("legacy")
        }
        return parts.joined(separator: " · ")
    }
}

/// A task that opens its destination: the player or the review on the stack, a resource in Safari.
struct TaskLink<Label: View>: View {
    let task: WeekTask
    let route: AppRouteBuilder
    @ViewBuilder var label: Label

    var body: some View {
        if case let .external(url) = TaskDestination.of(task) {
            Link(destination: url) { label }
                .accessibilityHint("Opens in Safari")
        } else {
            NavigationLink(value: route(task)) { label }
        }
    }
}

typealias AppRouteBuilder = (WeekTask) -> AppRoute
